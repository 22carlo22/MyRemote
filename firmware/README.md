# Table of Contents
- [Requirements](#Requirements)

# Requirements
| Component | Value / Part #s | Quantity | Purpose |
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

