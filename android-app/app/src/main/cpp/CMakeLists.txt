cmake_minimum_required(VERSION 3.22.1)

project("sentinel_engine" LANGUAGES CXX)

# Sentinel Sensor Fusion Engine
# Compilado como biblioteca compartida cargada vía JNI en Android
add_library(
    sentinel_engine
    SHARED
    sentinel_jni.cpp
    kalman/kalman_filter_1d.cpp
    ttc/ttc_engine.cpp
)

target_compile_options(sentinel_engine PRIVATE
    -std=c++17
    -O2
    -ffast-math         # Optimización de punto flotante (seguro para nuestro caso)
    -fno-exceptions     # Reducir overhead en runtime embebido
)

# Linkear con la biblioteca de log de Android
find_library(log-lib log)
target_link_libraries(sentinel_engine ${log-lib})
