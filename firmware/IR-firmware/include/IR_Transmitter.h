#ifndef IR_TRANSMITTER_H
#define IR_TRANSMITTER_H

#include <Arduino.h>
#include "esp_pm.h"
#include "driver/rmt.h"

/**
 * @class IR_Transmitter
 * @brief Handles infrared signal transmission using the ESP32 RMT peripheral.
 * This class automatically searches for an available RMT channel.
 */
class IR_Transmitter{
    public:
        int pin;
        rmt_channel_t IR_TX_CHANNEL;
        esp_pm_lock_handle_t lock;

        /**
         * @brief Construct a new IR_Transmitter object.
         * @param pin The GPIO pin connected to the IR LED driver.
         */
        IR_Transmitter(int pin);

        /**
         * @brief Initializes the RMT driver, searches for an available TX channel (0-3),
         * and configures the 38kHz carrier wave.
         */
        void init();

        /**
         * @brief Transmits an array of pulse durations.
         * @param buf Array of durations in ticks (1 tick = 12.5ns at 80MHz).
         * @param len Number of elements in the buffer. Must be an ODD number; otherwise, 
         * it will not be transmitted.
         * @note This function blocks until the transmission is physically complete.
         */
        void transmit(uint16_t *buf, int len);
};

#endif