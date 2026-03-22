#ifndef MYBLE_H
#define MYBLE_H

#include <NimBLEDevice.h>
#include <Arduino.h>

#define CHARACTERISTICS_MAX 10
#define SEMAPHORES_MAX 10

/**
 * @class Callback
 * @brief Bridge class to handle BLE write events.
 * @param runnable A function pointer that executes when a client writes to a characteristic.
 */
class Callback: public NimBLECharacteristicCallbacks {
    public:
        void (*runnable)(uint8_t*, size_t);
        Callback(void (*runnable)(uint8_t*, size_t));
        void onWrite(NimBLECharacteristic *pCharacteristic);
};

/**
 * @class Characteristic
 * @brief Container that links a numeric key to a NimBLE characteristic and its callbacks.
 */
class Characteristic {
    public:
        int key;
        NimBLECharacteristic *pCharacteristic;
        Callback *callback;

        Characteristic(int key, NimBLECharacteristic *pCharacteristic, void (*runnable)(uint8_t*, size_t));
        Characteristic(int key, NimBLECharacteristic *pCharacteristic);
};

/**
 * @class MyBLE
 * @brief High-level wrapper for NimBLE Server management.
 * Manages a single Service with up to 10 Characteristics. Supports only a single client.
 */
class MyBLE {
    public:
        Characteristic *characterics[CHARACTERISTICS_MAX];
        int char_len = 0;

        NimBLEServer *pServer;
        NimBLEService *pService;

        const char *name;
        const char *service_uuid;

        MyBLE(const char *name, const char *service_uuid);

        /**
         * @brief Initializes the BLE stack and advertising.
         * @param connected callback when the client connects.
         * @param disconnect callback when the client gets disconnected.
         */
        void init(void (*connected)(), void (*disconnected)());

        /** @brief Starts the service and begins advertising. */
        void start();

        /** @brief Adds a Read/Write/Notify characteristic.
         * @return int The 'key' (ID) used to reference this characteristic later, or -1 if full.
         */
        int addCharacteristic(const char *characteristic_uuid);
        
        /** @brief Adds a Read/Write/Notify characteristic with a callback for write events.
         * @return int The 'key' (ID) used to reference this characteristic later, or -1 if full.
         */
        int addCharacteristic(const char *characteristic_uuid, void (*runnable)(uint8_t*, size_t));

        NimBLECharacteristic *findCharacteristic(int key);

        /** @brief Updates characteristic value (up to 512 bytes) and sends a BLE Notification to the client. */
        void write(int key, byte arr[], int len);

        /** @brief Updates characteristic value and sends a BLE Notification to the client. */
        void write(int key, byte val);

        /** @brief disconnects the connected client */
        void disconnect();
};

/**
 * @class MyServerCallbacks
 * @brief Handles Server-level events like connection and disconnection.
 */
class MyServerCallbacks : public NimBLEServerCallbacks {
    public:

        void (*connected)() = NULL;
        void (*disconnected)() = NULL;

        void setInterface(void (*connected)(), void (*disconnected)());
        void onConnect(NimBLEServer *pServer); 
        void onDisconnect(NimBLEServer *pServer);
};

#endif