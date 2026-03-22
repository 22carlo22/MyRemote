package com.example.irremote;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.irremote.databinding.ActivityAddModifyBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Activity for creating or updating IR remote buttons.
 * It provides a live preview of the button while the user records signals and changes styles.
 */
public class AddModifyActivity extends AppCompatActivity {
    final static String TAG = "MY_TAG";

    private ActivityAddModifyBinding binding;
    private BLE ble;
    private MyButton buttonModel;           // The local button data being edited
    private Periodic requestListener;       // Handles the heartbeat request to the BLE hardware
    private Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);


        binding = ActivityAddModifyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ble = (BLE) getApplication();

        // Determine if we are creating a NEW button or editing an EXISTING one
        Intent intent = getIntent();
        int action = intent.getIntExtra("state", 0);
        setupTopBar(action);

        // Initialize/Restore the button data using a ViewModel
        buttonModel = getButtonModel(action , intent);

        // Manages the button that shows live style changes
        PreviewBox previewBox = new PreviewBox(binding.buttonPreview, buttonModel);
        previewBox.setInterface(new PreviewBox.PreviewInterface() {
            @Override
            public void onTransmitClick(byte[] bytes) {
                // Test the recorded IR signal immediately via BLE
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
            public void exception(Throwable throwable) {
                Snackbar.make(binding.main, throwable.getMessage(), Snackbar.LENGTH_SHORT).show();
            }
        });

        // Periodically pings the hardware to keep listening the IR receiver.
        // Period must be less than 10sec
        requestListener = new Periodic(5000, Looper.getMainLooper(), new Runnable() {
            @Override
            public void run() {
                byte[] dummy = {(byte) 0xFF};
                ble.write(BLE.Characteristic.REQUEST_LISTEN, dummy, new BLE.WriteInterface() {
                    @Override
                    public void success(byte[] bytes) {

                    }

                    @Override
                    public void exception(Throwable throwable) {

                    }
                });
            }
        });


        // Manages the text field and the "Microphone/Listen" button for IR capture
        DataBox dataBox = new DataBox(binding.imageView9, binding.imageView10,binding.editTextData, binding.textView18, binding.progressBar3);
        dataBox.setInterface(new DataBox.DataBoxInterface() {
            @Override
            public void onTextChanged(String text) {
                previewBox.setData(text);
                // Hide style options until data is actually present
                if(!text.isEmpty()) {
                    binding.cardViewColor.setVisibility(View.VISIBLE);
                    binding.cardViewSize.setVisibility(View.VISIBLE);
                    binding.cardViewPreview.setVisibility(View.VISIBLE);
                    binding.cardViewName.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onListenClick() {
                requestListener.start(); // Start heartbeat
                ble.read(BLE.Characteristic.IR, new BLE.ReadInterface() {
                    @Override
                    public void gotNewData(byte[] bytes) {
                        // Success: Hardware captured an IR signal. Display it.
                        int[] integers = Helper.bytesToUint16(bytes);
                        dataBox.setDataText(Arrays.toString(integers));
                        dataBox.performStopListenClick();
                    }

                    @Override
                    public void exception(Throwable throwable) {
                        Snackbar.make(binding.main, throwable.getMessage(), Snackbar.LENGTH_SHORT).show();
                        dataBox.performStopListenClick();
                    }
                });
            }

            @Override
            public void onStopListenClick() {
                ble.disposeReader(BLE.Characteristic.IR);
                requestListener.finish();
            }
        });
        dataBox.setDataText(buttonModel.data);

        // STYLE CONTROLS (Name, Color, Size)
        NameBox nameBox = new NameBox(binding.editTextName);
        nameBox.setInterface(new NameBox.NameBoxInterface() {
            @Override
            public void onTextChanged(String name) {
                previewBox.setName(name);
            }
        });
        nameBox.setNameText(buttonModel.name);

        ColorBox colorBox = new ColorBox(binding.linearLayoutColors);
        colorBox.setInterface(new ColorBox.ColorBoxInterface() {
            @Override
            public void onColorClick(int color) {
                previewBox.setColor(color);
            }
        });
        colorBox.setColor(buttonModel.color);

        SizeBox sizeBox = new SizeBox(binding.chipGroupSize);
        sizeBox.setInterface(new SizeBox.SizeBoxInterface() {
            @Override
            public void onSizeChanged(int size) {
                previewBox.setSize(size);
            }
        });
        sizeBox.setSize(buttonModel.size);
    }

    /**
     * Helper class to manage the Size selection ChipGroup.
     */
    private static class SizeBox{
        static final Map<String, Integer> map_size = Map.of("small", 30, "medium", 50, "large", 70);
        ChipGroup chipGroup;
        Chip chip_selected_size;

        public interface SizeBoxInterface{
            void onSizeChanged(int size);
        }

        SizeBoxInterface sizeBoxInterface = new SizeBoxInterface() {
            @Override
            public void onSizeChanged(int size) {

            }
        };
        SizeBox(ChipGroup chipGroup){
            this.chipGroup = chipGroup;
            init();
        }

        void setInterface(SizeBoxInterface sizeBoxInterface){
            this.sizeBoxInterface = sizeBoxInterface;
        }

        void setSize(int size){
            // let the default size be "medium"
            chip_selected_size = (Chip) chipGroup.getChildAt(1);
            for(int i = 0; i < chipGroup.getChildCount(); i++){
                Chip chip = (Chip) chipGroup.getChildAt(i);
                if(map_size.get(chip.getText().toString()) == size){
                    chip_selected_size = chip;
                }
            }

            chip_selected_size.post(new Runnable() {
                @Override
                public void run() {
                    chip_selected_size.performClick();
                }
            });
        }

        void init(){
            chipGroup.setOnCheckedStateChangeListener(new ChipGroup.OnCheckedStateChangeListener() {
                @Override
                public void onCheckedChanged(@NonNull ChipGroup group, @NonNull List<Integer> checkedIds) {
                    if (!checkedIds.isEmpty()) {
                        int checkedChipId = checkedIds.get(0);
                        String size_str = ((Chip) chipGroup.findViewById(checkedChipId)).getText().toString();
                        sizeBoxInterface.onSizeChanged(map_size.get(size_str));
                    }
                }
            });

        }
    }

    /**
     * Helper class to manage the color palette selection.
     */
    private static class ColorBox{
        LinearLayout linearLayout;
        ImageView imageView_selected_color;
        HashMap<Integer, ImageView> map_color_imageView = new HashMap<>();

        public interface ColorBoxInterface{
            void onColorClick(int color);
        }


        ColorBoxInterface colorBoxInterface = new ColorBoxInterface() {
            @Override
            public void onColorClick(int color) {

            }
        };
        ColorBox(LinearLayout linearLayout){
            this.linearLayout = linearLayout;
            init();
        }

        void setInterface(ColorBoxInterface colorBoxInterface){
            this.colorBoxInterface = colorBoxInterface;
        }

        void setColor(int color){
            ImageView toSelect;
            // let the default color be green
            if(map_color_imageView.containsKey(color)) toSelect = map_color_imageView.get(color);
            else toSelect = (ImageView) linearLayout.getChildAt(0);

            toSelect.post(new Runnable() {
                @Override
                public void run() {
                    toSelect.performClick();
                }
            });
        }

        void init(){
            for(int i = 0; i < linearLayout.getChildCount(); i++) {
                ImageView imageView = (ImageView) linearLayout.getChildAt(i);
                ColorStateList tintList = imageView.getImageTintList();
                int image_color = tintList.getDefaultColor();
                map_color_imageView.put(image_color, imageView);
                imageView.animate().alpha(0.2f).setDuration(200).start();

                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (imageView_selected_color != null) {
                            imageView_selected_color.animate().alpha(0.2f).setDuration(200).start();
                        }
                        view.animate().alpha(1f).setDuration(200).start();
                        imageView_selected_color = (ImageView) view;

                        colorBoxInterface.onColorClick(image_color);
                    }
                });
            }
        }




    }

    /**
     * Helper class to manage the name of the button.
     */
    private static class NameBox{
        EditText editText;

        public interface NameBoxInterface{
            void onTextChanged(String name);
        }

        NameBoxInterface nameBoxInterface = new NameBoxInterface() {
            @Override
            public void onTextChanged(String name) {

            }
        };
        NameBox(EditText editText){
            this.editText = editText;
            init();
        }

        void setInterface(NameBoxInterface nameBoxInterface){
            this.nameBoxInterface = nameBoxInterface;
        }

        void setNameText(String name){
            editText.post(new Runnable() {
                @Override
                public void run() {
                    editText.setText(name);
                }
            });
        }

        void init(){
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {

                }

                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    nameBoxInterface.onTextChanged(editText.getText().toString());
                }
            });
        }




    }


    /**
     * Helper class to handle the IR signal associated to the button.
     */
    private static class DataBox{
        ImageView listen;
        ImageView stop_listening;
        EditText editText_data;

        TextView textView_status;
        ProgressBar progressBar;

        public interface  DataBoxInterface{
            void onTextChanged(String text);
            void onListenClick();
            void onStopListenClick();
        }

        DataBoxInterface dataBoxInterface = new DataBoxInterface() {
            @Override
            public void onTextChanged(String text) {

            }

            @Override
            public void onListenClick() {

            }

            @Override
            public void onStopListenClick() {

            }
        };

        DataBox(ImageView listen, ImageView stop_listening, EditText editText_data, TextView textView_status, ProgressBar progressBar){
            this.listen = listen;
            this.stop_listening = stop_listening;
            this.editText_data = editText_data;
            this.textView_status = textView_status;
            this.progressBar = progressBar;
            init();
        }

        void setInterface(DataBoxInterface dataBoxInterface){
            this.dataBoxInterface = dataBoxInterface;
        }

        void setDataText(String data){
            editText_data.post(new Runnable() {
                @Override
                public void run() {
                    editText_data.setText(data);
                }
            });
        }

        void performStopListenClick(){
            stop_listening.post(new Runnable() {
                @Override
                public void run() {
                    stop_listening.performClick();
                }
            });
        }

        void init(){
            listen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dataBoxInterface.onListenClick();

                    listen.setVisibility(View.GONE);
                    stop_listening.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.VISIBLE);
                }
            });

            stop_listening.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dataBoxInterface.onStopListenClick();

                    listen.setVisibility(View.VISIBLE);
                    stop_listening.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                }
            });

            editText_data.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {

                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String text = editText_data.getText().toString();
                    dataBoxInterface.onTextChanged(text);
                    //If the data is in the wrong format, display an error message
                    if(MyButton.hasValidData(text) || text.isEmpty()) textView_status.setVisibility(View.GONE);
                    else textView_status.setVisibility(View.VISIBLE);
                }
            });
        }

    }


    /**
     * Helper class to show the edited button.
     */
    private static class PreviewBox{
        Button button;
        MyButton buttonModel;
        UUID uuid;

        public interface PreviewInterface{
            void onTransmitClick(byte[] bytes);
            void exception(Throwable throwable);
        }

        PreviewInterface previewInterface = new PreviewInterface() {
            @Override
            public void onTransmitClick(byte[] bytes) {

            }

            @Override
            public void exception(Throwable throwable) {

            }
        };

        PreviewBox(Button button, MyButton buttonModel){
            this.button = button;
            this.buttonModel = buttonModel;
            init();
        }

        void setInterface(PreviewInterface previewInterface){
            this.previewInterface = previewInterface;
        }

        void init(){
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(buttonModel.hasValidData()){
                        previewInterface.onTransmitClick(buttonModel.getDataBytes());
                    }
                    else{
                        previewInterface.exception(new RuntimeException("Can't transmit. Invalid data format."));
                    }
                }
            });
        }

        void setName(String name){
            buttonModel.name = name;
            button.setText(buttonModel.name);
        }

        void setData(String data){
            buttonModel.data = data;
        }

        void setColor(int color){
            buttonModel.color = color;
            button.setBackgroundTintList(ColorStateList.valueOf(buttonModel.color));
        }

        void setSize(int size){
            buttonModel.size = size;
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, buttonModel.size);
        }

    }


    /**
     * Configures the Toolbar menu based on whether we are adding or editing.
     */
    void setupTopBar(int action){
        setSupportActionBar(binding.myToolbar2);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        binding.myToolbar2.post(new Runnable() {
            @Override
            public void run() {
                if(action == MainActivity.ACTIVITY_CMD.GOING_TO_ADD_BUTTON){
                    // Hide delete/save, show add
                    menu.findItem(R.id.action_icon_save).setVisible(false);
                    menu.findItem(R.id.action_icon_delete).setVisible(false);
                    menu.findItem(R.id.action_icon_add).setVisible(true);
                    getSupportActionBar().setTitle("Add button");
                }else if(action == MainActivity.ACTIVITY_CMD.GOING_TO_EDIT_BUTTON){
                    // Show delete + save, hide add
                    menu.findItem(R.id.action_icon_save).setVisible(true);
                    menu.findItem(R.id.action_icon_delete).setVisible(true);
                    menu.findItem(R.id.action_icon_add).setVisible(false);
                    getSupportActionBar().setTitle("Edit button");
                }
            }
        });

    }

    public boolean onCreateOptionsMenu(Menu menu)
    {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    /**
     * Holds the style of the button.
     */
    public static class ButtonModel extends ViewModel {
        MyButton myButton;

    }

    /**
     *  Creates the button model to be changed.
     */
    MyButton getButtonModel(int action, Intent intent){
        ButtonModel model = new ViewModelProvider(this).get(ButtonModel.class);
        if(model.myButton != null) return model.myButton;

        if(action == MainActivity.ACTIVITY_CMD.GOING_TO_ADD_BUTTON){
            //When adding a button, assign default values
            int width=Resources.getSystem().getDisplayMetrics().widthPixels;
            int height=Resources.getSystem().getDisplayMetrics().heightPixels;

            model.myButton = new MyButton(null,
                    UUID.randomUUID().toString(),
                    "", "", 0,0, width/2, height/2);
        }
        else if(action == MainActivity.ACTIVITY_CMD.GOING_TO_EDIT_BUTTON){
            //When editing, ensure all values reflects the existing button (from intent)
            model.myButton = new MyButton(null, intent);
        }
        return model.myButton;
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if(id == android.R.id.home){
            // When user navigates back, just exit this activity
            setResult(MainActivity.ACTIVITY_CMD.DO_NOTHING, null);
            finish();
            return true;
        }
        else if(id == R.id.action_icon_add){
            // Only create the button only if it has a valid data
            if(buttonModel.hasValidData()){
                setResult(MainActivity.ACTIVITY_CMD.GOING_TO_ADD_BUTTON, buttonModel.getIntent());
                finish();
                return true;
            }
            else{
                Snackbar.make(binding.main, "Unable to add. Invalid data format.", Snackbar.LENGTH_SHORT).show();
            }
        }
        else if(id == R.id.action_icon_delete){
            Intent intent = new Intent();
            intent.putExtra("id", buttonModel.id);
            setResult(MainActivity.ACTIVITY_CMD.GOING_TO_DELETE_BUTTON, intent);

            finish();
            return true;
        }
        else if(id == R.id.action_icon_save){
            // Only update the button only if it has a valid data
            if(buttonModel.hasValidData()){
                setResult(MainActivity.ACTIVITY_CMD.GOING_TO_EDIT_BUTTON, buttonModel.getIntent());
                finish();
                return true;
            }
            else{
                Snackbar.make(binding.main, "Unable to save. Invalid data format.", Snackbar.LENGTH_SHORT).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @NonNull
    @Override
    public OnBackInvokedDispatcher getOnBackInvokedDispatcher() {
        setResult(MainActivity.ACTIVITY_CMD.DO_NOTHING, null);
        return super.getOnBackInvokedDispatcher();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ble.disposeReader(BLE.Characteristic.IR);
        requestListener.finish();
    }

    @Override
    public void finish() {
        super.finish();
        ble.disposeReader(BLE.Characteristic.IR);
        requestListener.finish();
    }
}