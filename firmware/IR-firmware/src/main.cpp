#include <Arduino.h>
#include "MyBLE.h"
#include "IR_Receiver.h"
#include "IR_Transmitter.h"
#include "Battery.h"
#include "esp_pm.h"
#include "esp_log.h"
#include <atomic>

// Pin Mapping
#define IR_RX_PIN     2
#define IR_TX_PIN     5
#define BATTERY_PIN   1
#define BUILTIN_RGB   21

// Battery thresholds and ratio 
#define BATTERY_DIVIDER_RATIO   1.0/2.0
#define BATTERY_LOW             3600      // mV
#define BATTERY_FULL            6000      // mV

// Timing (ms)
#define BATTERY_READ_PERIOD   5000      
#define IR_TX_DELAY           100      
#define IR_RX_EXPIRATION      10000    
#define HEARTBEAT_PERIOD      10000

#define IR_RX_BUFFER          200
#define AUTO_SLEEP
#define DEBUG_RGB

// BLE UUIDs
#define BLE_NAME        "MyRemote"
#define UUID_SERVICE    "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define UUID_IR         "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define UUID_BATTERY    "cba1d466-344c-4be3-ab65-5fccf933c572"
#define UUID_LISTEN     "cba1d466-344c-4be3-ab65-5fccf933c571"
#define UUID_HEARTBEAT  "cba1d466-344c-4be3-ab65-5fccf933c573"

// Global Objects & Synchronization
IR_Receiver ir_rx(IR_RX_PIN);
IR_Transmitter ir_tx(IR_TX_PIN);
MyBLE ble(BLE_NAME, UUID_SERVICE);
Battery battery(BATTERY_PIN, BATTERY_DIVIDER_RATIO, BATTERY_LOW, BATTERY_FULL);

SemaphoreHandle_t batteryStart_sem;
SemaphoreHandle_t batteryEnd_sem;
SemaphoreHandle_t heartbeatStart_sem;
SemaphoreHandle_t heartbeatEnd_sem;
SemaphoreHandle_t connectionAlive_sem;
SemaphoreHandle_t listen_sem;
QueueHandle_t transmit_queue;
std::atomic<bool> transmitting(false);
 
int battery_key;
int ir_signal_key;

// =============================================================================
// UTILITY FUNCTIONS
// =============================================================================

// For debugging purposes
void blinkRGB(byte r, byte g, byte b){
  #ifdef DEBUG_RGB
    esp_pm_lock_handle_t lock;
    esp_pm_lock_create(ESP_PM_APB_FREQ_MAX, 0, "setup", &lock);
    esp_pm_lock_acquire(lock);

    neopixelWrite(BUILTIN_RGB, g, r, b);
    delay(1000);
    neopixelWrite(BUILTIN_RGB, 0, 0, 0);
    delay(1000);

    esp_pm_lock_release(lock);
    esp_pm_lock_delete(lock);
  #endif
}

// =============================================================================
// TASKS
// =============================================================================

// Will attempt to disconnect if the client is not responding.
void HeartbeatTask(void *parameter){
  while(1){
    if(xSemaphoreTake(heartbeatStart_sem, portMAX_DELAY) == pdTRUE){
      while(xSemaphoreTake(heartbeatEnd_sem, HEARTBEAT_PERIOD / portTICK_PERIOD_MS) == pdFALSE){
        if(xSemaphoreTake(connectionAlive_sem, 0) == pdFALSE){
          ESP_LOGE("HeartbeatTask", "Client is unresponsive. Disconnecting...");
          ble.disconnect();
        }
      }
    }
  }
}

// Periodic battery reporting. Only active when a client is connected.
void BatteryTask(void *parameter){
  while(1){
    if(xSemaphoreTake(batteryStart_sem, portMAX_DELAY) == pdTRUE){
      while(xSemaphoreTake(batteryEnd_sem, BATTERY_READ_PERIOD / portTICK_PERIOD_MS) == pdFALSE){
        byte level = battery.read(); 
        ble.write(battery_key, level);
      }
    }
  }
}

// Transmits IR pulses. Memory allocated in Callback must be freed here.
void irTransmitTask(void *parameter){
    while(1){
      uint16_t* buf;
      if(xQueueReceive(transmit_queue, &buf, portMAX_DELAY) == pdPASS) {
        transmitting = true;
        ir_tx.transmit(&buf[1], buf[0]);
        vPortFree(buf);
        vTaskDelay(IR_TX_DELAY / portTICK_PERIOD_MS);
        transmitting = false;
      }
  }
}

// Listens for a single IR code when requested by the client
void irListenTask(void *parameter){
   while(1){
    if(xSemaphoreTake(listen_sem, portMAX_DELAY) == pdTRUE){
      ESP_LOGI("irListenTask", "listening...");

      static uint16_t buf[IR_RX_BUFFER];
      int len = ir_rx.listen(buf, IR_RX_BUFFER, IR_RX_EXPIRATION);

      if(len > 0){
        if(transmitting){
          ESP_LOGW("irListenTask", "Crosstalk detected");
        } else {
          // Send raw pulse data back to the client as a byte array in big endian.
          static uint8_t bytes[IR_RX_BUFFER*2];
          for(int i = 0; i < len; i++){
              bytes[i*2] = (buf[i] >> 8) & 0xFF;
              bytes[i*2+1] = buf[i] & 0xFF;
          }

          ble.write(ir_signal_key, bytes, len*2);
        }
      }
    }
  }
}

// =============================================================================
// BLE CALLBACKS
// =============================================================================

void clientTransmitCallback(uint8_t *data, size_t len){
  if(len % 2 != 0) return;

  uint16_t* buf = (uint16_t*) pvPortMalloc((len/2 + 1) * sizeof(uint16_t));

  if(buf != NULL){
    // Convert the byte array into 16-bit array. Put the size of the buffer in the first element.
    buf[0] = len / 2;
    for(int i = 0; i < len; i += 2){
      buf[i/2 + 1] = data[i] << 8 | data[i+1];
    }

    // Send the buffer to be transmitted.
    if(xQueueSend(transmit_queue, &buf, 0) == pdFALSE){
      vPortFree(buf);
    };
  }
}

void clientListenRequest(uint8_t *data, size_t len){
  xSemaphoreGive(listen_sem);
}

void clientHeartbeat(uint8_t *data, size_t len){
  xSemaphoreGive(connectionAlive_sem);
}

void connected(){
  xSemaphoreGive(batteryStart_sem);
  xSemaphoreGive(heartbeatStart_sem);
}

void disconnected(){
  xSemaphoreGive(batteryEnd_sem);
  xSemaphoreGive(heartbeatEnd_sem);
}

// =============================================================================
// MAIN SETUP & LOOP
// =============================================================================

void setup() {
  esp_err_t err_pm = ESP_FAIL;

  #ifdef AUTO_SLEEP
    // Enable Automatic Light Sleep and Dynamic Frequency Scaling (DFS). 
    esp_pm_config_esp32s3_t pm_config = {
      .max_freq_mhz = 80,
      .min_freq_mhz = 40,
      .light_sleep_enable = true
    };
    err_pm = esp_pm_configure(&pm_config);
  #endif

  // RED: PM Configured Successfully
  if(err_pm == ESP_OK) blinkRGB(10, 0, 0); 

  // GREEN: External Clock Found
  if(rtc_clk_slow_freq_get() == RTC_SLOW_FREQ_32K_XTAL) blinkRGB(0, 10, 0); 

  // BLUE: Initialization starting
  blinkRGB(0, 0, 10); 

  ESP_LOGI("setup", "Starting setup");

  // Create RTOS primitives
  listen_sem = xSemaphoreCreateBinary();
  transmit_queue = xQueueCreate(5, sizeof(uint16_t*));
  batteryStart_sem = xSemaphoreCreateBinary();
  batteryEnd_sem = xSemaphoreCreateBinary();
  heartbeatStart_sem = xSemaphoreCreateBinary();
  heartbeatEnd_sem = xSemaphoreCreateBinary();
  connectionAlive_sem = xSemaphoreCreateBinary();

  // Peripheral Init
  ir_rx.init();
  ir_tx.init();

  // BLE Configuration
  ble.init(connected, disconnected);
  ir_signal_key = ble.addCharacteristic(UUID_IR, clientTransmitCallback);
  battery_key = ble.addCharacteristic(UUID_BATTERY);
  ble.addCharacteristic(UUID_LISTEN, clientListenRequest);
  ble.addCharacteristic(UUID_HEARTBEAT, clientHeartbeat);
  ble.start();

  // Task Initialization
  xTaskCreate(BatteryTask, "BatteryTask", 8192, NULL, 1, NULL);
  xTaskCreate(HeartbeatTask, "HeartbeatTask", 8192, NULL, 1, NULL);
  xTaskCreate(irTransmitTask, "irTransmitTask", 8192, NULL, 1, NULL);
  xTaskCreate(irListenTask, "irListenTask", 8192, NULL, 1, NULL);
}

// Block this task forever. This is not used.
void loop() {
  vTaskDelay(portMAX_DELAY);
}