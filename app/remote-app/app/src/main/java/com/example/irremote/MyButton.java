package com.example.irremote;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a single programmable button on the remote.
 * Manages its own visual properties (color, size, position).
 */
public class MyButton {
    final static String TAG = "MY_TAG";

    /**
     * Intent and JSON keys for passing button data between activities.
     */
    public static class KEY{
        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String DATA = "data";
        public static final String COLOR = "color";
        public static final String SIZE = "size";
        public static final String X = "x";
        public static final String Y = "y";
    }

    /**
     * Defines the operational mode of the button globally.
     */
    public static class State{
        public static final int TRANSMIT = 0;
        public static final int MOVE = 1;
        public static final int EDIT = 2;
    }

    static Boundary boundary = new Boundary() {
        @Override
        public void bounded(float[] pos, int[] size) {

        }
    };

    static int state = State.TRANSMIT;
    static MyButtonInterface myButtonInterface = new MyButtonInterface() {
        @Override
        public void onTransmitClick(byte[] bytes) {

        }

        @Override
        public void onEditClick(MyButton myButton) {

        }
    };

    // Button Properties
    String id;
    String name;
    String data;
    int color;
    int size;
    float x, y;

    float dX, dY; // Touch offsets for smooth dragging
    Button button;
    Context context;

    /**
     * Communicates button interactions back to the Activity/Manager.
     */
    public interface MyButtonInterface {
        void onTransmitClick(byte[] bytes); // Triggered in TRANSMIT state
        void onEditClick(MyButton myButton); // Triggered in EDIT state
    }

    /**
     * Used to keep buttons within the screen boundaries during dragging.
     */
    public interface  Boundary{
        void bounded(float[] pos, int[] size);
    }


    MyButton(Context context, String id, String name, String data, int color, int size, float x, float y){
        this.context = context;
        this.id = id;
        this.name = name;
        this.data = data;
        this.color = color;
        this.size = size;
        this.x = x;
        this.y = y;
    }

    MyButton(Context context, JSONObject json_obj) throws JSONException {
        this.context = context;
        this.id = json_obj.getString(KEY.ID);
        this.name = json_obj.getString(KEY.NAME);
        this.data = json_obj.getString(KEY.DATA);
        this.color = json_obj.getInt(KEY.COLOR);
        this.size = json_obj.getInt(KEY.SIZE);
        this.x = (float) json_obj.getDouble(KEY.X);
        this.y = (float) json_obj.getDouble(KEY.Y);
    }

    MyButton(Context context, Intent intent){
        this.context = context;
        id = intent.getStringExtra(KEY.ID);
        name = intent.getStringExtra(KEY.NAME);
        data = intent.getStringExtra(KEY.DATA);
        color = intent.getIntExtra(KEY.COLOR, 0);
        size = intent.getIntExtra(KEY.SIZE, 50);
        x = intent.getFloatExtra(KEY.X, 0);
        y = intent.getFloatExtra(KEY.Y, 0);
    }

    /**
     * Check if the data is safe to be sent for IR transmission.
     */
    static boolean hasValidData(String data){
        try{
            // A data is valid only if the number of element is odd and they are all positive.
            int[] integers = Helper.stringToIntArray(data);
            if(integers.length%2 == 0) throw new RuntimeException();
            for(Integer i:integers){
                if(i <= 0) throw new RuntimeException();
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }


    boolean hasValidData(){
        return hasValidData(data);
    }

    Intent  getIntent() {
        Intent intent = new Intent();
        intent.putExtra(KEY.ID, id);
        intent.putExtra(KEY.NAME, name);
        intent.putExtra(KEY.DATA, data);
        intent.putExtra(KEY.COLOR, color);
        intent.putExtra(KEY.SIZE, size);
        intent.putExtra(KEY.X, x);
        intent.putExtra(KEY.Y, y);
        return intent;
    }

    static void setInterface(MyButtonInterface myButtonInterface){
        MyButton.myButtonInterface = myButtonInterface;
    }

    void inflate(){
        View view = LayoutInflater.from(context).inflate(R.layout.view_button, null);
        this.button = (Button) view;
        setupButton();
    }

    /**
     * Converts the stored IR string into a byte array for BLE transmission.
     */
    byte[] getDataBytes(){
        int[] integers = Helper.stringToIntArray(data);
        return Helper.uint16ToBytes(integers);
    }

    /**
     * Updates the button's coordinate and enforces boundary limits.
     */
    void updatePos(float x, float y){
        float[] pos = {x, y};
        int[] size = {button.getWidth(), button.getHeight()};
        boundary.bounded(pos, size);
        this.x = pos[0];
        this.y = pos[1];
        button.setX(this.x);
        button.setY(this.y);
    }

    static void setBoundary(Boundary boundary){
        MyButton.boundary = boundary;
    }

    void setData(String data){
        this.data = data;
    }

    void setName(String name){
        this.name = name;
        button.setText(name);
    }

    void setColor(int color){
        this.color = color;
        button.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    void setSize(int size){
        this.size = size;
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    }

    JSONObject getJson(){
        JSONObject jsonData = new JSONObject();
        try {
            jsonData.put("id", id);
            jsonData.put("name", name);
            jsonData.put("data", data);
            jsonData.put("color", color);
            jsonData.put("size", size);
            jsonData.put("x", x);
            jsonData.put("y", y);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        return jsonData;
    }

    /**
     * Creates the visual Button view and attaches touch listeners.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupButton(){
        button.post(new Runnable() {
            @Override
            public void run() {
                updatePos(x, y);
                setName(name);
                setColor(color);
                setSize(size);
            }
        });

        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                int action = event.getAction();
                if(action == MotionEvent.ACTION_DOWN){
                    if(state == State.TRANSMIT){
                        view.startAnimation(AnimationUtils.loadAnimation(context, R.anim.button_click));
                        myButtonInterface.onTransmitClick(getDataBytes());
                    }
                    else if(state == State.EDIT){
                        myButtonInterface.onEditClick(MyButton.this);
                    }
                    dX = x - event.getRawX();
                    dY = y - event.getRawY();
                }
                else if(action == MotionEvent.ACTION_MOVE && state == State.MOVE){
                    updatePos(event.getRawX() + dX, event.getRawY() + dY);
                }
                return true;
            }
        });
    }
}
