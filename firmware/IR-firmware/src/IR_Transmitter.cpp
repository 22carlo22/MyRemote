#include "IR_Transmitter.h"
#include "esp_log.h"

#define IR_FREQ 38000
#define IR_DUTY 33

static const char* TAG = "IR_Transmitter";

IR_Transmitter::IR_Transmitter(int pin){
    this->pin = pin;
}

void IR_Transmitter::init(){
    int i;
    // Claim an available RMT transmitter channel 
    for(i = 0; i <= 3; i++){
        rmt_channel_t ch = (rmt_channel_t) i;
        rmt_config_t config = RMT_DEFAULT_CONFIG_TX((gpio_num_t)pin, ch);
        config.tx_config.carrier_en = true;
        config.tx_config.carrier_freq_hz = IR_FREQ; 
        config.tx_config.carrier_duty_percent = IR_DUTY;

        if(rmt_config(&config) == ESP_OK && rmt_driver_install(ch, 0, 0) == ESP_OK){
            IR_TX_CHANNEL = ch;
            break;
        }
    }
    if(i == 4) ESP_LOGE(TAG, "No available RMT transmitter");

    // Power lock ensures timing stays consistent by locking the APB clock frequency
    esp_pm_lock_create(ESP_PM_APB_FREQ_MAX, 0, "IR_Transmitter_lock", &lock);
}

void IR_Transmitter::transmit(uint16_t *buf, int len){
    // IR signals usually end on a 'mark' (pulse), meaning the total count of 
    // pulses + spaces should be odd.
    if(len%2 == 0){
        ESP_LOGW(TAG, "Invalid data format: size is not odd");
        return;
    }

    // Pack our 16-bit durations into 32-bit hardware items.
    rmt_item32_t raw_signal[len/2 + 1];
    for(int i = 0; i < len; i += 2){
        uint16_t dur1 = buf[i];
        uint16_t dur2 = i+1 == len ? 0 : buf[i+1];
        raw_signal[i/2] = {dur1, 1, dur2, 0};
    }
    
    size_t size = sizeof(raw_signal) / sizeof(rmt_item32_t);

    ESP_LOGI(TAG, "Transmitting...");
    esp_pm_lock_acquire(lock);
    rmt_write_items(IR_TX_CHANNEL, raw_signal, size, true);
    esp_pm_lock_release(lock);
}