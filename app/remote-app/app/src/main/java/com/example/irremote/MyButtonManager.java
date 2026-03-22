package com.example.irremote;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the collection of buttons on the main screen.
 * Acts as a bridge between the Activity logic and the individual button instances.
 */
public class MyButtonManager {

    private Map<String, MyButton> myButtons = new HashMap<>();

    private Context context;
    private ConstraintLayout container;

    /**
     * Initializes the manager and sets up global button behaviors.
     */
    public MyButtonManager(Context context, ConstraintLayout container, MyButton.MyButtonInterface func){
        this.context = context;
        this.container = container;

        MyButton.setInterface(func);

        // Keeps buttons from being dragged off the screen
        container.post(new Runnable() {

            @Override
            public void run() {
                MyButton.setBoundary(new MyButton.Boundary() {
                    @Override
                    public void bounded(float[] pos, int[] size) {
                        if(pos[0] < 0) pos[0] = 0;
                        else if(pos[0] + size[0] > container.getWidth()) pos[0] = container.getWidth()-size[0];

                        if(pos[1] < 0) pos[1] = 0;
                        else if(pos[1] + size[1] > container.getHeight()) pos[1] = container.getHeight()-size[1];
                    }
                });
            }
        });
    }

    /**
     * Creates or updates a button on the layout.
     * If a button with the same ID exists, it replaces the old one.
     */
    public void putButton(String id, String name, String data, int color, int size, float x, float y){
        if(myButtons.containsKey(id)){
            myButtons.get(id).setName(name);
            myButtons.get(id).setData(data);
            myButtons.get(id).setColor(color);
            myButtons.get(id).setSize(size);
            myButtons.get(id).updatePos(x, y);
        }
        else{
            MyButton myButton = new MyButton(context, id, name, data, color, size, x, y);
            myButton.inflate();
            container.addView(myButton.button);
            myButtons.put(id, myButton);

        }
    }
    public void putButton(JSONObject json) throws JSONException{
        MyButton button = new MyButton(context, json);
        putButton(button.id, button.name, button.data, button.color, button.size, button.x, button.y);
    }

    public void putButton(Intent intent){
        MyButton button = new MyButton(context, intent);
        putButton(button.id, button.name, button.data, button.color, button.size, button.x, button.y);
    }

    public boolean hasButtons(){
        return !myButtons.isEmpty();
    }

    public void reinflateAll(){
        for(MyButton b:myButtons.values()){
            b.inflate();
            container.addView(b.button);
        }
    }

    /**
     * Removes a button from both the UI and the data collection.
     */
    public void deleteButton(String id){
        if(myButtons.containsKey(id)){
            container.removeView(myButtons.get(id).button);
            myButtons.remove(id);
        }
    }

    /**
     * Changes the mode for all buttons.
     */
    public void updateButtonState(int state){
        MyButton.state = state;
    }

    /**
     * Loads a set of buttons from a JSON Array (used when the app starts).
     */
    public void addFromJsonArray(JSONArray json_arr) throws JSONException{
        for(int i = 0; i < json_arr.length(); i++){
            JSONObject json_obj = json_arr.getJSONObject(i);
            putButton(json_obj);
        }
    }

    /**
     * Converts the entire collection of buttons into a JSON Array for saving to a file.
     */
    public JSONArray toJsonArray() {
        ArrayList<JSONObject> arr = new ArrayList<>();
        for (MyButton button : myButtons.values()) arr.add(button.getJson());
        return new JSONArray(arr);
    }
}
