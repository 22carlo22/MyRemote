#ifndef BATTERY_H
#define BATTERY_H

#include <Arduino.h>

/**
 * @class Battery
 * @brief Handles battery voltage monitoring using an analog pin and a voltage divider.
 */
class Battery{
    public:
        int pin;
        float voltage_divider_ratio;
        int low_bat; 
        int full_bat;
        
        /**
         * @brief Construct a new Battery object
         * @param pin The analog pin to read from.
         * @param voltage_divider_ratio The divider ratio.
         * @param low_bat The minimum battery voltage in millivolts.
         * @param full_bat The maximum battery voltage in millivolts.
         */
        Battery(int pin, float voltage_divider_ratio, int low_bat, int full_bat);

        /**
         * @brief Samples the battery level and applies a low-pass filter.
         * @note This is a BLOCKING call. It takes approximately 1 second.
         * @return byte Battery percentage (0-100).
         */
        byte read();
};

#endif