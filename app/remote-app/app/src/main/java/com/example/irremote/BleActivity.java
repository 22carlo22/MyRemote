package com.example.irremote;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.irremote.databinding.ActivityBleBinding;
import com.example.irremote.databinding.ViewDeviceBinding;
import com.example.irremote.databinding.ViewSeperatorBinding;
import com.polidea.rxandroidble3.RxBleDevice;
import com.polidea.rxandroidble3.scan.ScanResult;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Activity responsible for scanning and displaying available BLE devices.
 * When a user selects a device, it returns the device name to MainActivity to initiate a connection.
 */
public class BleActivity extends AppCompatActivity{

    private ActivityBleBinding binding;
    private BLE ble;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityBleBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setSupportActionBar(binding.myToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ble = (BLE) getApplication();

        processScanDevices();

    }


    /**
     * Handles the "Back" arrow click in the toolbar.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Initiates the BLE scan and dynamically populates the UI list with discovered devices.
     */
    public void processScanDevices(){
        ble.scan(new BLE.ScanInterface() {
            boolean display_divider = false; // Tracks if a separator line should be drawn
            HashSet<String> devices = new HashSet<>(); // Ensures each unique device name only appears once in the list
            @Override
            public void deviceDiscovered(RxBleDevice device) {
                // Filter out duplicates and null names
                if(devices.contains(device.getName())) return;
                devices.add(device.getName());

                // Add a visual separator between items starting from the second device
                if(display_divider){
                    ViewSeperatorBinding viewSeperatorBinding = ViewSeperatorBinding.inflate(
                            LayoutInflater.from(binding.linearLayoutDevices.getContext()),
                            binding.linearLayoutDevices, false);
                    binding.linearLayoutDevices.addView(viewSeperatorBinding.getRoot());
                }
                else display_divider = true;

                ViewDeviceBinding viewDeviceBinding = ViewDeviceBinding.inflate(
                        LayoutInflater.from(binding.linearLayoutDevices.getContext()),
                        binding.linearLayoutDevices, false);

                viewDeviceBinding.textView4.setText(device.getName());
                viewDeviceBinding.textView9.setText(device.getMacAddress());

                // Set click listener: When a device is clicked, return it to MainActivity
                viewDeviceBinding.cardView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent();
                        intent.putExtra("name", device.getName());
                        setResult(MainActivity.ACTIVITY_CMD.GOING_TO_CONNECT, intent);
                        finish();
                    }
                });

                binding.linearLayoutDevices.addView(viewDeviceBinding.getRoot());
            }

            @Override
            public void exception(Throwable throwable) {
                // If scanning fails, notify MainActivity with the error message
                Intent intent = new Intent();
                intent.putExtra("msg", throwable.getMessage());
                setResult(MainActivity.ACTIVITY_CMD.FAILED_TO_SCAN, intent);
                finish();
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        ble.exitScan();
    }
}