#ifndef IR_RECEIVER_H
#define IR_RECEIVER_H

#include <Arduino.h>
#include "esp_pm.h"
#include "driver/rmt.h"

/**
 * @class IR_Receiver
 * @brief Handles infrared signal reception using the ESP32 RMT (Remote Control) peripheral.
 * This class automatically searches for an available RMT channel.
 */
class IR_Receiver{
    public:
        int pin_rx;
        rmt_channel_t IR_RX_CHANNEL;
        esp_pm_lock_handle_t lock;
        
        /**
         * @brief Construct a new IR_Receiver object.
         * @param pin_rx The GPIO pin connected to the IR receiver output.
         */
        IR_Receiver(int pin_rx);

        /**
         * @brief Finds an available RMT channel (4-7), configures hardware filters, 
         * and initializes the power management lock.
         */
        void init();

        /**
         * @brief Listens for an incoming IR signal.
         * @param buf Pointer to the buffer where pulse durations will be stored. 
         * Values are stored in hardware ticks (1 tick = 12.5ns at 80MHz).
         * @param max The maximum size of the provided buffer.
         * @param dur The timeout duration to wait for a signal (in milliseconds).
         * @return int The number of pulses received, or -1 if a buffer overflow occurs.
         */
        int listen(uint16_t *buf, int max, unsigned int dur);
};

#endif