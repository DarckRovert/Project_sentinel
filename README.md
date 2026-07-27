# Sentinel 🛡️

> **Sistema ADAS Retrofit COTS & Tapete Háptico 360°** — Alerta temprana de colisiones para vehículos sin tecnología de asistencia de fábrica.

🌐 **Web del Proyecto & Demo en vivo:** [https://darckrovert.github.io/Project_sentinel/](https://darckrovert.github.io/Project_sentinel/)

---

## Stack Tecnológico

| Capa | Tecnología |
|---|---|
| **Unidad Central** | Retrovisor Android (COTS) — min. Android 9 / API 28 |
| **Radar Principal** | HLK-LD2450 (mmWave, UART @ 256kbaud) → Kit Premium: TI AWR1843 |
| **Visión** | Cámara USB UVC 1080p → Camera2 API + TensorFlow Lite |
| **IMU** | BNO055 (I²C vía USB-Serial) |
| **Háptico** | ESP32-S3 + 4x DRV2605L + Motores LRA → BLE 5.0 (GATT 1-byte) |
| **App Android** | Kotlin + Jetpack Compose + Clean Architecture + MVVM |
| **DSP/ML** | C++ (JNI) — Kalman Filter 1D, TTC Calculator Engine |
| **Firmware** | ESP-IDF / Arduino Framework (NimBLE + Adafruit DRV2605) |

---

## Estructura del Repositorio

```
Project_sentinel/
├── android-app/        # Aplicación Android (Clean Architecture + JNI C++)
├── firmware-haptic/    # Firmware ESP32 (BLE GATT Server + DRV2605L)
└── docs/              # GitHub Pages Website & Simulador Interactivo
```

---

## Kits de Producto Modulares

### Kit Esencial ($119 USD MSRP Est.)
- Retrovisor Android + Software Sentinel
- Radar mmWave HLK-LD2450
- Cámara USB 1080p gran angular
- IMU 9-DoF BNO055
- Alertas HUD visuales y de audio

### Kit Completo / Premium ($219 USD MSRP Est.)
- Todo lo incluido en el Kit Esencial
- **Tapete de asiento con retroalimentación háptica 360°** (ESP32 + BLE 5.0)
- 4x Drivers DRV2605L con actuadores LRA (Frente, Atrás, Izquierda, Derecha)
- Radar TI AWR1843BOOST (Rango 80m+)
- GPS u-blox externo + Sensores ultrasónicos IP67

---

## Latencia Target
- **Sensor → Alerta visual:** < 100ms end-to-end
- **Android → BLE Tapete háptico:** < 30ms

---

## GitHub Pages Deployment
La landing page interactiva para inversionistas y socios se encuentra en la carpeta `/docs`. Para activarla en GitHub:
1. Ve a **Settings** > **Pages** en tu repositorio de GitHub.
2. En **Build and deployment** > **Source**, selecciona `Deploy from a branch`.
3. Selecciona la rama `main` y la carpeta `/docs`, y haz clic en **Save**.

---
*Proyecto Sentinel — Democratizando la seguridad vehicular.*

