package com.example.irremote;

import android.app.Application;

import com.polidea.rxandroidble3.RxBleClient;
import com.polidea.rxandroidble3.RxBleConnection;
import com.polidea.rxandroidble3.RxBleDevice;
import com.polidea.rxandroidble3.scan.ScanSettings;

import android.app.Service;
import android.bluetooth.BluetoothGatt;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Global BLE Controller extending Application to maintain Bluetooth state across activities.
 * Handles scanning, connection parameters, MTU negotiation, and data I/O.
 */
public class BLE extends Application {

    private final static String TAG = "MY_TAG";

    private final static int MIN_REQUIRED_MTU = 512;


    public static class Characteristic{
        final static UUID IR = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
        final static UUID BATTERY = UUID.fromString("cba1d466-344c-4be3-ab65-5fccf933c572");
        final static UUID REQUEST_LISTEN = UUID.fromString("cba1d466-344c-4be3-ab65-5fccf933c571");
        final static UUID HEARTBEAT = UUID.fromString("cba1d466-344c-4be3-ab65-5fccf933c573");
    }

    private static RxBleClient rxBleClient;
    private static RxBleConnection connection;
    private static String connected_device_name;
    private final static HashMap<String, RxBleDevice> discovered_devices = new HashMap<>();


    // RxJava disposables to prevent memory leaks by cleaning up streams
    private static Disposable disposable_connection;
    private static Disposable scan_subscription;
    private static Disposable disposable_state;
    private static HashMap<UUID, Disposable> disposable_readers;

    /**
     * Used by activities to check if the phone is ready for BLE.
     */
    public interface StateInterface {
        void requestBluetoothEnable();    // Triggered if BT is off
        void requestLocationPermission(); // Triggered if permissions are missing
        void readyToConnect();            // Triggered when everything is OK
    }

    /**
     * Used during the device discovery phase.
     */
    public interface ScanInterface {
        void deviceDiscovered(RxBleDevice device); // Found a new device
        void exception(Throwable throwable);       // Scan failed
    }

    /**
     * Used to track the lifecycle of a connection.
     */
    public interface ConnectInterface {
        void connecting(String name);                 // Connection started
        void disconnected(String name, String error); // Connection lost/failed
        void connected(String name);                  // Successfully connected
    }

    /**
     * Confirms if data (like an IR command) was sent.
     */
    public interface WriteInterface {
        void success(byte[] bytes);          // Hardware received the data
        void exception(Throwable throwable); // Send failed
    }

    /**
     * Receives incoming data (like a recorded IR signal) from the device.
     */
    public interface ReadInterface {
        void gotNewData(byte[] bytes);       // New IR data captured
        void exception(Throwable throwable); // Read error
    }

    @Override
    public void onCreate() {
        super.onCreate();
        rxBleClient = RxBleClient.create(getApplicationContext());
        disposable_readers = new HashMap<>();
    }

    /**
     * Monitors Bluetooth hardware state (On/Off) and Location permissions.
     */
    public void checkState(StateInterface stateInterface){
        if(disposable_state != null) disposable_state.dispose();
        disposable_state = rxBleClient.observeStateChanges()
                .startWithItem(rxBleClient.getState())
                .subscribe(state -> {
                    switch (state) {
                        case BLUETOOTH_NOT_ENABLED:
                            stateInterface.requestBluetoothEnable();
                            break;
                        case LOCATION_PERMISSION_NOT_GRANTED:
                            stateInterface.requestLocationPermission();
                            break;
                        case READY:
                            stateInterface.readyToConnect();
                            break;
                    }
                });
    }

    /**
     * Scans for devices and filters for those with valid names.
     */
    static void scan(ScanInterface scanInterface){
        exitScan();

        scan_subscription = rxBleClient.scanBleDevices( new ScanSettings.Builder().build())
                .subscribe(
                        scanResult -> {
                            RxBleDevice device = scanResult.getBleDevice();
                            if(device.getName() != null){
                                discovered_devices.put(device.getName(), device);
                                scanInterface.deviceDiscovered(device);
                            }
                        },
                        throwable -> {
                            scanInterface.exception(throwable);
                        }
                );


    }

    static void exitScan(){
        if(scan_subscription != null){
            scan_subscription.dispose();
            scan_subscription = null;
        }
    }

    /**
     * Establishes a connection only if the required MTU size and connectional interval
     * for low power is accepted
     */
    static void connect(String name, ConnectInterface connectInterface){
        disconnect();
        if(!discovered_devices.containsKey(name)){
            connectInterface.disconnected(name, "Device is not found");
            return;
        }

        connectInterface.connecting(name);

        disposable_connection = discovered_devices.get(name).establishConnection(false)
                .flatMapSingle(rxBleConnection ->
                        rxBleConnection.requestMtu(MIN_REQUIRED_MTU)
                                .flatMap(negotiatedMtu -> {
                                    if (negotiatedMtu < MIN_REQUIRED_MTU) {
                                        return Single.error(new Exception("MTU size " + negotiatedMtu + " is too small!"));
                                    }
                                    return Single.just(rxBleConnection);
                                })
                )
                .subscribe(
                        rxBleConnection -> {
                            connection = rxBleConnection;
                            connected_device_name = name;
                            connectInterface.connected(name);
                        },
                        throwable -> {
                            disconnect();
                            connectInterface.disconnected(name, throwable.getMessage());
                        }
                );
    }

    static void disconnect(){
        connection = null;
        if(disposable_connection != null){
            disposable_connection.dispose();
            disposable_connection = null;
        }
    }
    static void write(UUID uuid, byte[] data, WriteInterface writeInterface){
        if(connection == null){
            writeInterface.exception(new RuntimeException("No connected device"));
            return;
        }

        connection.writeCharacteristic(uuid, data)
                .subscribe(
                        characteristicValue -> {
                           writeInterface.success(characteristicValue);
                        },
                        throwable -> {
                            writeInterface.exception(throwable);
                        }
                );
    }

    /**
     * Sets up a real-time notification listener for a specific characteristic.
     * Used for receiving IR codes captured by the hardware.
     */
    static void read(UUID uuid, ReadInterface readInterface){
        if(connection == null){
            readInterface.exception(new RuntimeException("No connected device"));
            return;
        }

        disposeReader(uuid);

        Disposable notificationDisposable = connection
                .setupNotification(uuid)
                .flatMap(notificationObservable -> notificationObservable) // Flatten to get Observable<byte[]>
                .subscribe(
                        bytes -> {
                           readInterface.gotNewData(bytes);
                        },
                        throwable -> {
                            readInterface.exception(throwable);
                            disposable_readers.remove(uuid);
                        }
                );

        disposable_readers.put(uuid, notificationDisposable);


    }

    /**
     * Safely stops listening to a characteristic.
     */
    static void disposeReader(UUID uuid){
        if(disposable_readers.containsKey(uuid)){
            disposable_readers.get(uuid).dispose();
            disposable_readers.remove(uuid);
        }
    }

}
