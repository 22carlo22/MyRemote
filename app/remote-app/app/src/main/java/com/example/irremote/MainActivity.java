package com.example.irremote;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.irremote.databinding.ActivityMainBinding;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.polidea.rxandroidble3.RxBleDevice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    final static String TAG = "MY_TAG";

    /**
     * Filenames for internal storage.
     */
    public static class FILENAME{
        final static String BUTTON = "buttons";
        final static String DEVICE = "device";
    }


    /**
     * Request codes for distinguishing between different Activity results.
     */
    public static class REQUEST_CODE{
        public final static int ADDMODIFY = 1;
        public final static int BLUETOOTH = 2;
        public final static int ENABLE_BT = 3;
        public final static int LOCATION_PERMISSION = 4;
        public final static int NEARBY_DEVICES = 5;
    }

    /**
     * Custom command codes sent back from other activities to tell MainActivity what to do.
     */
    public static class ACTIVITY_CMD{
        public final static int GOING_TO_EDIT_BUTTON = 0;
        public final static int GOING_TO_ADD_BUTTON = 1;
        public final static int GOING_TO_DELETE_BUTTON = 2;
        public final static int DO_NOTHING = 3;
        public final static int GOING_TO_CONNECT = 4;
        public final static int FAILED_TO_SCAN = 5;
    }

    private ActivityMainBinding binding;
    private MyButtonManager myButtonManager;
    private BLE ble;
    private MyTopBar myTopBar;
    private Periodic heartbeat;
    private BLE.ConnectInterface connectionInterface;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        Log.d(TAG, "onCreate");

        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ble = (BLE) getApplication();

        // Periodically pings the hardware to notify the connection is still active and performing correctly.
        // Period must be less than 5sec
        heartbeat = new Periodic(2500, Looper.getMainLooper(), new Runnable() {
            @Override
            public void run() {
                byte[] dummy = {0};
                ble.write(BLE.Characteristic.HEARTBEAT, dummy, new BLE.WriteInterface() {
                    @Override
                    public void success(byte[] bytes) {

                    }

                    @Override
                    public void exception(Throwable throwable) {

                    }
                });
            }
        });
        heartbeat.start();


        //Initialize the tool bar
        TopBarModel topBarModel = new ViewModelProvider(this).get(TopBarModel.class);
        myTopBar = new MyTopBar(binding.toolbarMain, R.menu.toolbar_main, topBarModel);

         //Update and show the connection status within the tool bar
        connectionInterface = new BLE.ConnectInterface() {
            BatteryManager batteryManager = new BatteryManager(ble, BLE.Characteristic.BATTERY);

            @Override
            public void connecting(String name) {
                myTopBar.setConnectionStatus("Connecting "+name+"...");
            }

            @Override
            public void disconnected(String name, String exception_msg) {
                myTopBar.setConnectionStatus("Not connected");
                batteryManager.finish();
                Snackbar.make(binding.main, exception_msg, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void connected(String name) {
                FileManager.write(MainActivity.this, FILENAME.DEVICE, name);
                myTopBar.setConnectionStatus(name);
                batteryManager.setInterface(new BatteryManager.BatteryInterface() {
                    @Override
                    public void newData(String percent) {
                        myTopBar.setConnectionStatus(name+" - "+percent);
                    }
                });
                batteryManager.run();
            }
        };

        //Request bluetooth permissions. If all are enabled, auto connect.
        ble.checkState(new BLE.StateInterface() {
            @Override
            public void requestBluetoothEnable() {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                startActivityForResult(enableBtIntent, REQUEST_CODE.ENABLE_BT);
            }

            @Override
            public void requestLocationPermission() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_CONNECT
                            },
                            REQUEST_CODE.NEARBY_DEVICES);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_CODE.LOCATION_PERMISSION);
                }
            }

            @Override
            public void readyToConnect() {
                //Auto connect to the device
                String to_connect = FileManager.read(MainActivity.this, FILENAME.DEVICE);
                if(!to_connect.isEmpty()){
                    ble.scan(new BLE.ScanInterface() {
                        @Override
                        public void deviceDiscovered(RxBleDevice device) {
                            if(device.getName().equals(to_connect)){
                                ble.connect(to_connect, connectionInterface);
                                ble.exitScan();
                            }
                        }
                        @Override
                        public void exception(Throwable throwable) {

                        }
                    });
                }
            }
        });

        // Initialize the button manager
        myButtonManager = new MyButtonManager(this, binding.containerButtons, new MyButton.MyButtonInterface() {
            @Override
            public void onTransmitClick(byte[] bytes) {
                // Send the IR data to the hardware via BLE
                ble.write(BLE.Characteristic.IR, bytes, new BLE.WriteInterface() {
                    @Override
                    public void success(byte[] bytes) {

                    }

                    @Override
                    public void exception(Throwable throwable) {
                        Snackbar.make(binding.main, throwable.getMessage(), Snackbar.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onEditClick(MyButton myButton) {
                // Open the editor for the clicked button
                Intent intent = myButton.getIntent();
                intent.setClass(MainActivity.this, AddModifyActivity.class);
                intent.putExtra("state", ACTIVITY_CMD.GOING_TO_EDIT_BUTTON);
                startActivityForResult(intent, REQUEST_CODE.ADDMODIFY);
            }
        });

        // Load existing buttons from internal storage
        if(myButtonManager.hasButtons()) myButtonManager.reinflateAll();     // If the activity restarts (ex. theme changes), just reinflate all
        else {
            try {
                myButtonManager.addFromJsonArray(new JSONArray(FileManager.read(MainActivity.this, FILENAME.BUTTON)));
            } catch (JSONException e) {
                Log.d(TAG, "Button file error");
            }
        }
    }


    /**
     * ViewModel for the TopBar to maintain state across configuration changes.
     */
    public static class TopBarModel extends ViewModel{
        String title = "Not connected";
        String connection_stat = title;
        boolean cancel_visibility = false;
        boolean add_visibility = true;
        boolean connect_visibility = true;
        boolean move_visibility = true;
        boolean edit_visibility = true;
        final static String DRAG_COMMENT = "Drag a button to move";
        final static String EDIT_COMMENT = "Click a button to edit";
    }

    /**
     * Controller for the custom Toolbar at the top of the screen.
     */
    private class MyTopBar{
        MenuItem cancel;
        MenuItem add;
        MenuItem connect;
        MenuItem move;
        MenuItem edit;
        int resource_menu;

        TopBarModel topBarModel;

        boolean click_move, click_close;

        MyTopBar(androidx.appcompat.widget.Toolbar toolbar, int resource_menu, TopBarModel topBarModel){
            setSupportActionBar(toolbar);
            this.resource_menu = resource_menu;
            this.topBarModel = topBarModel;
        }



        void setupMenu(Menu menu){
            getMenuInflater().inflate(resource_menu, menu);
            setTitle(topBarModel.title);
            cancel = menu.findItem(R.id.cancel);
            add = menu.findItem(R.id.add);
            connect = menu.findItem(R.id.connect);
            move = menu.findItem(R.id.move);
            edit = menu.findItem(R.id.edit);


            //For configuration changes; return to the previous state
            if(click_close) onOptionsItemSelected(cancel);
            if(click_move) onOptionsItemSelected(move);
            click_close = false;
            click_move = false;
        }


        void setConnectionStatus(String text){
            topBarModel.connection_stat = text;
            boolean currently_editing = topBarModel.cancel_visibility;
            if(!currently_editing){
                setTitle(text);
            }
        }

        void setTitle(String text){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    topBarModel.title = text;
                    getSupportActionBar().setTitle(text);
                }
            });
        }

        void setUIVisibility(boolean ca, boolean a, boolean co, boolean m, boolean e){
            topBarModel.cancel_visibility = ca;
            topBarModel.add_visibility = a;
            topBarModel.connect_visibility = co;
            topBarModel.move_visibility = m;
            topBarModel.edit_visibility = e;
        }

        void updateIcons(){
            cancel.setVisible(topBarModel.cancel_visibility);
            add.setVisible(topBarModel.add_visibility);
            connect.setVisible(topBarModel.connect_visibility);
            move.setVisible(topBarModel.move_visibility);
            edit.setVisible(topBarModel.edit_visibility);
        }

        void setMoving(){
            setTitle(topBarModel.DRAG_COMMENT);
            setUIVisibility(true, false, false, false, false);
            invalidateOptionsMenu();
        }

        /**
         * Show to the user that is in "edit" mode
         */
        void setEditing() {
            setTitle(topBarModel.EDIT_COMMENT);
            setUIVisibility(true, false, false, false, false);
            invalidateOptionsMenu();
        }


        /**
         * Show to the user that is in "transmit" mode
         */
        void setDefault(){
            setTitle(topBarModel.connection_stat);
            setUIVisibility(false, true, true, true, true);
            invalidateOptionsMenu();
        }

        /**
         * Update to "move" mode
         */
        void performMoveClick(){
            click_move = true;
            invalidateOptionsMenu();
        }

        /**
         * Update to "transmit" mode
         */
        void performCancelClick(){
            click_close = true;
            invalidateOptionsMenu();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Listen if one of the icons (items) in the tool bar is clicked
        int id = item.getItemId();
        if(id == R.id.add){
            // Open the editor (AddModifyActivity) to create a new button
            Intent intent = new Intent(MainActivity.this, AddModifyActivity.class);
            intent.putExtra("state", ACTIVITY_CMD.GOING_TO_ADD_BUTTON);
            startActivityForResult(intent, REQUEST_CODE.ADDMODIFY);
        }
        else if(id == R.id.connect){
            // Open the BleActivity to allow the user to select a device
            startActivityForResult(new Intent(MainActivity.this, BleActivity.class), REQUEST_CODE.BLUETOOTH);
        }
        else if(id == R.id.edit){
            myButtonManager.updateButtonState(MyButton.State.EDIT);
            myTopBar.setEditing();

        }
        else if(id == R.id.move){
            myButtonManager.updateButtonState(MyButton.State.MOVE);
            myTopBar.setMoving();
        }
        else if(id == R.id.cancel){
            myButtonManager.updateButtonState(MyButton.State.TRANSMIT);
            myTopBar.setDefault();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        myTopBar.updateIcons();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        myTopBar.setupMenu(menu);
        return true;
    }

    /**
     * Listens for the battery characteristic from the connected device.
     */
    private static class BatteryManager{
        BLE ble;
        UUID uuid;

        public interface BatteryInterface{
            void newData(String percent);
        }

        BatteryInterface batteryInterface = new BatteryInterface() {
            @Override
            public void newData(String percent) {

            }
        };

        BatteryManager(BLE ble, UUID uuid){
            this.ble = ble;
            this.uuid = uuid;
        }

        void setInterface(BatteryInterface batteryInterface){
            this.batteryInterface = batteryInterface;
        }

        void run(){
            ble.read(uuid, new BLE.ReadInterface() {
                @Override
                public void gotNewData(byte[] bytes) {
                    int battery_percent = (bytes[0] & 0xFF);
                    batteryInterface.newData(battery_percent+"%");
                }

                @Override
                public void exception(Throwable throwable) {

                }
            });
        }

        void finish(){
            ble.disposeReader(uuid);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if(requestCode == REQUEST_CODE.ADDMODIFY){
            if(resultCode == ACTIVITY_CMD.GOING_TO_ADD_BUTTON){
                // When a new button is added, allow the user to move it
                myTopBar.performMoveClick();
                myButtonManager.putButton(intent);
            }
            else if(resultCode == ACTIVITY_CMD.GOING_TO_EDIT_BUTTON){
                // When a button is edited, all buttons will now transmit IR signal
                myTopBar.performCancelClick();
                myButtonManager.putButton(intent);
            }
            else if(resultCode == ACTIVITY_CMD.GOING_TO_DELETE_BUTTON){
                // When a button is deleted, all buttons will now transmit IR signal
                myTopBar.performCancelClick();
                myButtonManager.deleteButton(intent.getStringExtra(MyButton.KEY.ID));
            }
        }
        else if(requestCode == REQUEST_CODE.BLUETOOTH){
            if(resultCode == ACTIVITY_CMD.GOING_TO_CONNECT){
                // Connect to the selected device
                String name = intent.getStringExtra("name");
                ble.connect(name, connectionInterface);
            }
            else if(resultCode == ACTIVITY_CMD.FAILED_TO_SCAN){
                String msg = intent.getStringExtra("msg");
                Snackbar.make(binding.main,  msg == null ? "Scanning failed" : msg, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FileManager.write(MainActivity.this, FILENAME.BUTTON, myButtonManager.toJsonArray().toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ble.disconnect();
        heartbeat.finish();
    }
}