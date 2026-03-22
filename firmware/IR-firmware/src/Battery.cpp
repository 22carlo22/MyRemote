#include "Battery.h"
#include "esp_log.h"

// Constants for signal processing
#define SAMPLES 10             // Number of readings to average/filter
#define BATTERY_PERIOD 100     // Delay between samples in ms
#define BATTERY_FILTER 0.9     // Smoothing factor (closer to 1.0 = smoother/slower)

static const char* TAG = "Battery";

Battery::Battery(int pin, float voltage_divider_ratio, int low_bat, int full_bat){
    this->pin = pin;
    this->voltage_divider_ratio = voltage_divider_ratio;
    this->low_bat = low_bat;
    this->full_bat = full_bat;
}

byte Battery::read(){
    esp_log_level_set("gpio", ESP_LOG_WARN); 

    static double avg_voltage = full_bat;

    for(int i = 0; i < SAMPLES; i++){
        int raw = analogReadMilliVolts(pin);

        //Apply low-pass filter filter
        avg_voltage = (1-BATTERY_FILTER)*(raw / voltage_divider_ratio)+BATTERY_FILTER*avg_voltage;

        //Give time for the sampling capacitor to stabilize
        vTaskDelay(BATTERY_PERIOD / portTICK_PERIOD_MS);        
    }

    //Convert to percentage
    byte result;
    if(avg_voltage <= low_bat)  result = 0;
    else if(avg_voltage >= full_bat) result = 100;
    else result = round(100.0* (avg_voltage - low_bat) / (full_bat - low_bat));
    ESP_LOGI(TAG, "Battery voltage (mV): %f, percentage: %d", avg_voltage, result);
    return result;
}

