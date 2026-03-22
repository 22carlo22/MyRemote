#include "IR_Receiver.h"
#include "esp_log.h"

// RMT Tuning Constants
#define IR_IDLE_THRESH 10000   // Time in ticks to consider the line "idle" (end of signal)
#define IR_FILTER 100          // Filter out pulses shorter than this (removes noise)
#define IR__RMT_BUF_SIZE 1000  // Buffer reserved for the RMT receiver

static const char* TAG = "IR_Receiver";

IR_Receiver::IR_Receiver(int pin_rx){
    this->pin_rx = pin_rx;
}

void IR_Receiver::init(){
    int i;

    // Claim an available RMT receiver channel 
    for(i = 4; i <= 7; i++){
        rmt_channel_t ch = (rmt_channel_t) i;
        rmt_config_t rmt_rx = RMT_DEFAULT_CONFIG_RX((gpio_num_t)pin_rx, ch);
        rmt_rx.rx_config.filter_en = true;
        rmt_rx.rx_config.filter_ticks_thresh = IR_FILTER;
        rmt_rx.rx_config.idle_threshold = IR_IDLE_THRESH;   
        
        if(rmt_config(&rmt_rx) == ESP_OK && rmt_driver_install(ch, IR__RMT_BUF_SIZE, 0) == ESP_OK){
            IR_RX_CHANNEL = ch;
            break;
        }

    }
    if(i == 8) ESP_LOGE(TAG, "No available RMT receiver");
    
    // This prevents the CPU from changing frequency during RX, which would ruin timing.
    esp_pm_lock_create(ESP_PM_APB_FREQ_MAX, 0, "IR_Receiver_lock", &lock);
}

int IR_Receiver::listen(uint16_t *buf, int max, unsigned int dur){

    RingbufHandle_t rb = NULL;
    rmt_get_ringbuf_handle(IR_RX_CHANNEL, &rb);

    //Start listening for an IR signal
    esp_pm_lock_acquire(lock);
    rmt_rx_start(IR_RX_CHANNEL, 1);
    size_t length = 0;
    rmt_item32_t* items = (rmt_item32_t*)xRingbufferReceive(rb, &length, dur / portTICK_PERIOD_MS);
    rmt_rx_stop(IR_RX_CHANNEL);
    esp_pm_lock_release(lock);
    
    // Unpack the RMT hardware 32-bit items into the 16-bit duration buffer
    int len = 0;
    if(items != NULL){
        size_t num_items = length / sizeof(rmt_item32_t);
        len = num_items*2-1;
        if(len > max){
            ESP_LOGW(TAG, "Buffer overflow");
            return -1;
        }
        for(int i = 0; i < num_items; i++){
            buf[i*2] = items[i].duration0;
            if(i < num_items-1) buf[i*2+1] = items[i].duration1;
        }

        vRingbufferReturnItem(rb, (void*)items);

    }

    return len;
}

