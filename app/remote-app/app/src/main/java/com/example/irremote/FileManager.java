package com.example.irremote;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Handles reading and writing text data to the app's internal storage.
 * This is used to save button layouts and the last connected BLE device.
 */
public class FileManager {

    private static final String TAG = "FileManager";

    /**
     * Saves a string of data (usually a JSON string) to a file.
     * @param context  The activity context.
     * @param filename The name of the file (e.g., "buttons.json").
     * @param data     The string content to be saved.
     */
    static void write(Context context, String filename, String data) {
        try {
            // MODE_PRIVATE ensures the file is only accessible by this application
            FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
            fos.write(data.getBytes()); // Convert string to bytes for writing
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to " + filename + ": " + e.toString());
        }
    }

    /**
     * Reads the entire content of a file and returns it as a String.
     * @param context  The activity context.
     * @param filename The name of the file to read.
     * @return The file contents as a string, or an empty string if an error occurs.
     */
    static String read(Context context, String filename) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            FileInputStream fis = context.openFileInput(filename);
            InputStreamReader inputStreamReader = new InputStreamReader(fis);

            // Using BufferedReader for efficient line-by-line reading
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }

            // Close all streams to prevent memory leaks
            bufferedReader.close();
            inputStreamReader.close();
            fis.close();

        } catch (IOException e) {
            Log.e(TAG, "Error reading from " + filename + ": " + e.toString());
        }
        return stringBuilder.toString();
    }
}