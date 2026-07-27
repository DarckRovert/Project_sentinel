/**
 * sentinel_jni.cpp
 *
 * Puente JNI entre el Kotlin del proyecto Sentinel y el motor C++
 * de fusión de sensores (Kalman Filter + TTC Calculator).
 *
 * Expone funciones nativas para:
 *  1. Inicializar el motor por cada target (ID 0-2)
 *  2. Actualizar el filtro Kalman con nuevas mediciones del radar
 *  3. Obtener el TTC estimado en segundos
 */

#include <jni.h>
#include <android/log.h>
#include <array>
#include <chrono>
#include "kalman/kalman_filter_1d.h"

#define LOG_TAG "SentinelJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// LD2450 tiene máximo 3 targets simultáneos
static constexpr int MAX_TARGETS = 3;

// Un filtro Kalman por target para mantener continuidad entre frames
static std::array<KalmanFilter1D, MAX_TARGETS> kalmanFilters;
static std::array<long long, MAX_TARGETS> lastUpdateMs;

// -----------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------
static long long currentTimeMs() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
}

// -----------------------------------------------------------------------
// JNI: com.sentinel.app.core.jni.SentinelEngine
// -----------------------------------------------------------------------
extern "C" {

/**
 * Inicializa o reinicia los filtros Kalman.
 * Llamar al conectar el radar o al cambiar de perfil.
 */
JNIEXPORT void JNICALL
Java_com_sentinel_app_core_jni_SentinelEngine_nativeInit(
    JNIEnv* env, jobject /* this */,
    jfloat processNoise, jfloat measureNoise
) {
    for (int i = 0; i < MAX_TARGETS; i++) {
        kalmanFilters[i] = KalmanFilter1D(processNoise, measureNoise);
        lastUpdateMs[i] = 0;
    }
    LOGI("SentinelEngine inicializado: Q=%.3f R=%.3f", processNoise, measureNoise);
}

/**
 * Actualiza el filtro Kalman para un target específico y retorna
 * el TTC calculado con la distancia suavizada y velocidad estimada.
 *
 * @param targetId        ID del target (0, 1, 2)
 * @param rawDistanceM    Distancia cruda del radar en metros
 * @param radarSpeedMps   Velocidad del radar en m/s (negativa = acercándose)
 * @return                TTC en segundos. Float.MAX_VALUE si sin colisión proyectada.
 */
JNIEXPORT jfloat JNICALL
Java_com_sentinel_app_core_jni_SentinelEngine_nativeUpdateTarget(
    JNIEnv* env, jobject /* this */,
    jint targetId, jfloat rawDistanceM, jfloat radarSpeedMps
) {
    if (targetId < 0 || targetId >= MAX_TARGETS) {
        LOGW("targetId inválido: %d", targetId);
        return std::numeric_limits<float>::max();
    }

    long long now = currentTimeMs();
    float dt = (lastUpdateMs[targetId] == 0)
        ? 0.1f  // Primera actualización: asumir 100ms
        : (float)(now - lastUpdateMs[targetId]) / 1000.0f;
    lastUpdateMs[targetId] = now;

    // Limitar dt a un máximo razonable (si el target estuvo ausente)
    if (dt > 1.0f) {
        kalmanFilters[targetId].reset();
        dt = 0.1f;
    }

    float smoothedDistance = kalmanFilters[targetId].update(rawDistanceM, dt);
    float estimatedVelocity = kalmanFilters[targetId].getEstimatedVelocity();

    // Usar la velocidad estimada del Kalman si el radar reporta un valor ruidoso
    // Prioridad: velocidad del radar si es confiable, sino la estimada
    float effectiveVelocity = (fabsf(radarSpeedMps) > 0.3f) ? radarSpeedMps : estimatedVelocity;

    // TTC solo si el objeto se acerca
    if (effectiveVelocity >= -0.1f) {
        return std::numeric_limits<float>::max();
    }

    float ttc = -smoothedDistance / effectiveVelocity;

    // Filtrar TTCs negativos o irrealmente bajos (artefactos del filtro)
    if (ttc < 0.0f || ttc > 30.0f) {
        return std::numeric_limits<float>::max();
    }

    return ttc;
}

/**
 * Reinicia el filtro de un target específico.
 * Llamar cuando el target desaparece del campo del radar.
 */
JNIEXPORT void JNICALL
Java_com_sentinel_app_core_jni_SentinelEngine_nativeResetTarget(
    JNIEnv* env, jobject /* this */, jint targetId
) {
    if (targetId >= 0 && targetId < MAX_TARGETS) {
        kalmanFilters[targetId].reset();
        lastUpdateMs[targetId] = 0;
    }
}

} // extern "C"
