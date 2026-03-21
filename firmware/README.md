# Table of Contents
- [Requirements](#Requirements)

# Requirements
| Component | Specification | Quantity | Purpose |
| :--- | :--- | :--- | :--- |
| **Microcontroller** | Waveshare ESP32-S3 Mini Development Board | 1 | Core processing |
| **Transmitter** | 940nm IR LED | 1 | Emitting IR signals |
| **Receiver** | TSOP4838 | 1 | Capturing IR signals |
| **Switching Transistor** | NPN | 1 | High-current driver for IR LED |
| **RTC Crystal** | 32.768 kHz | 1 | Precise timing for the BLE during sleep mode |
| **Gate Resistor** | 330 Ω | 1 | Limits current from ESP32 to Transistor |
| **Current Limiter** | 10 Ω | 1 | Protects IR LED from overcurrent |
| **Voltage Divider** | 100 kΩ | 2 | Battery level monitoring (ADC) |
| **Load Capacitors** | 22 pF | 2 | Tuning for the external crystal |
| **Bypass Capacitor** | 104 pF | 1 | Noise filtering for the ADC channel |

# Assembly 
Use the following schematics to assemble the hardware.
## Board
The board used in this project is a Waveshare ESP32-S3 Mini Development Board. It is compact and offers better power efficiency compared to the standard ESP32 DevKit. While the firmware is not strictly limited to this model, you can use other boards if power consumption is not a primary concern. If you choose a different hardware platform, please keep the following in mind:
- You must configure your own sdkconfig based on the provided defconfig
- You may need to comment out the DEBUG_RGB definition if your specific board does not include a built-in RGB LED

The board can be powered using a voltage source between 3.7V and 6V. Exceeding the maximum rating will obviously damage the voltage regulator over time. Conversely, dropping below the minimum rating may cause unexpected brownouts, due to the 300mA peak current associated with radio operations, particularly during RF recalibration.

