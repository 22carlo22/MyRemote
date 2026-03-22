#include "MyBLE.h"
#include "esp_log.h"

#define MAX_MTU_SIZE 512
#define ADVERITISING_INTERVAL 800  //Advertise every ~0.5 seconds (units of 0.625ms)

static const char* TAG = "MyBLE";

MyBLE::MyBLE(const char *name, const char *service_uuid){
    this->name = name;
    this->service_uuid = service_uuid;
}

void MyBLE::init(void (*connected)(), void (*disconnected)()){
    esp_log_level_set("NimBLE", ESP_LOG_WARN);
    NimBLEDevice::init(name);
    NimBLEDevice::setMTU(MAX_MTU_SIZE);
    pServer = NimBLEDevice::createServer();
    
    // Setup server callbacks to handle connection signaling
    MyServerCallbacks *serverCallbacks = new MyServerCallbacks();
    serverCallbacks->setInterface(connected, disconnected);
    pServer->setCallbacks(serverCallbacks);
    pService = pServer->createService(service_uuid);

    // Configure Advertising
    NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
    pAdvertising->setMinInterval(ADVERITISING_INTERVAL);
    pAdvertising->setMaxInterval(ADVERITISING_INTERVAL);    
    pAdvertising->addServiceUUID(service_uuid);
}

void MyBLE::start(){
    pService->start();
    NimBLEDevice::startAdvertising();
}

int MyBLE::addCharacteristic(const char *characteristic_uuid){
    if(char_len == CHARACTERISTICS_MAX) return -1;
    
    uint32_t property = NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY; 
    NimBLECharacteristic *pCharacteristic = pService->createCharacteristic(characteristic_uuid, property);
    
    characterics[char_len] = new Characteristic(char_len, pCharacteristic);
    return char_len++;
}

int MyBLE::addCharacteristic(const char *characteristic_uuid, void (*runnable)(uint8_t*, size_t)){
    if(char_len == CHARACTERISTICS_MAX) return -1;

    uint32_t property = NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::NOTIFY;
    NimBLECharacteristic *pCharacteristic = pService->createCharacteristic(characteristic_uuid, property);
    
    Characteristic *c = new Characteristic(char_len, pCharacteristic, runnable);
    pCharacteristic->setCallbacks(c->callback);
    characterics[char_len] = c;
    return char_len++;
}

void MyBLE::disconnect(){
    uint16_t connHandle = pServer->getPeerInfo(0).getConnHandle(); 
    pServer->disconnect(connHandle); 
}


NimBLECharacteristic *MyBLE::findCharacteristic(int key){
    for(int i = 0; i < char_len; i++){
        if(characterics[i]->key == key) return characterics[i]->pCharacteristic;
    }
    return NULL;
}

void MyBLE::write(int key, byte arr[], int len){
    NimBLECharacteristic *c = findCharacteristic(key);
    if(c == NULL) return; 

    c->setValue(arr, len);
    c->notify();
}

void MyBLE::write(int key, byte value){
    write(key, &value, 1);
}

void MyServerCallbacks::onConnect(NimBLEServer *pServer){
    ESP_LOGI(TAG, "Client connected");
    // Connection interval: 200ms 
    pServer->updateConnParams(pServer->getPeerInfo(0).getConnHandle(), 160, 160, 0, 300);
    if(connected != NULL) connected();

}

void MyServerCallbacks::onDisconnect(NimBLEServer *pServer){
    ESP_LOGI(TAG, "Client disconnected");
    if(disconnected != NULL) disconnected();
    NimBLEDevice::startAdvertising();
}

void MyServerCallbacks::setInterface(void (*connected)(), void (*disconnected)()){
    this->connected = connected;
    this->disconnected = disconnected;
}

Callback::Callback(void (*runnable)(uint8_t*, size_t)){
    this->runnable = runnable;
}

void Callback::onWrite(NimBLECharacteristic *pCharacteristic){
    NimBLEAttValue value = pCharacteristic->getValue();
    runnable((uint8_t*)value.data(), value.length());
}

Characteristic::Characteristic(int key, NimBLECharacteristic *pCharacteristic, void (*runnable)(uint8_t*, size_t)){
    this->key = key;
    this->pCharacteristic = pCharacteristic;
    this->callback = new Callback(runnable);
}

Characteristic::Characteristic(int key, NimBLECharacteristic *pCharacteristic){
    this->key = key;
    this->pCharacteristic = pCharacteristic;
    this->callback = NULL;
}
