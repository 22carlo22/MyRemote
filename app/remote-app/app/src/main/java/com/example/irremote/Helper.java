package com.example.irremote;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Utility class providing static methods for data type conversions.
 * Primarily used for handling IR data formats between byte arrays (for BLE) and integer arrays (for logic).
 */
public class Helper {

    /**
     * Converts an array of 16-bit integers into a byte array.
     * Each integer is split into two bytes (Big-Endian format).
     * @param integers The source array of IR timing values.
     * @return A byte array twice the length of the input, ready for BLE transmission.
     */
    static byte[] uint16ToBytes(int[] integers){
        byte[] bytes = new byte[integers.length*2];
        for(int i = 0; i < integers.length; i++){
            bytes[i*2] = (byte) (integers[i] >> 8);
            bytes[i*2 + 1] = (byte) (integers[i] & 0xFF);
        }
        return bytes;
    }

    /**
     * Reconstructs an integer array from a byte array received via Bluetooth.
     * Combines pairs of bytes back into single 16-bit integers.
     * @param bytes The raw data received from the BLE device.
     * @return An array of integers representing the original IR signal.
     */
    static int[] bytesToUint16(byte[] bytes){
        int[] integers = new int[bytes.length/2];
        for(int i = 0; i < integers.length; i++){
            integers[i] = ((bytes[i*2] << 8) & 0xFF00) | (bytes[i*2+1] & 0xFF);
        }
        return integers;
    }

    /**
     * Parses a string representation of an array (e.g., "[100, 200, 300]") into an int array.
     * Useful for processing IR data stored in JSON or shared via Intents.
     * @param s The string containing comma-separated integers.
     * @return An array of parsed integers.
     * @throws RuntimeException if the string format is invalid or contains non-numeric values.
     */
    static int[] stringToIntArray(String s) throws RuntimeException{
        try{
            String[] stringArray = s.replaceAll("\\[", "").replaceAll("]", "").replaceAll(" ", "").split(",");
            return Arrays.stream(stringArray).mapToInt(Integer::parseInt).toArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
