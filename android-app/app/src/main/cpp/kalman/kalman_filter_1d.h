#pragma once

/**
 * Filtro de Kalman 1D para Sentinel.
 *
 * Aplica suavizado predictivo a las mediciones de distancia del radar
 * para reducir el ruido y los falsos positivos en el cálculo del TTC.
 *
 * ## Modelo de estado:
 *   x[k] = distancia estimada (metros)
 *   v[k] = velocidad relativa estimada (m/s)
 *
 * ## Matrices del filtro:
 *   A = [[1, dt], [0, 1]]   (transición de estado)
 *   H = [1, 0]              (observación: solo medimos distancia)
 *   Q = ruido de proceso
 *   R = ruido de medición (varianza del radar LD2450, empiríco ~0.1m)
 */
class KalmanFilter1D {
public:
    /**
     * @param processNoise   Covarianza del ruido de proceso Q. Default: 0.01
     * @param measureNoise   Covarianza del ruido de medición R. Default: 0.1
     */
    explicit KalmanFilter1D(float processNoise = 0.01f, float measureNoise = 0.1f);

    /**
     * Actualiza el filtro con una nueva medición de distancia.
     *
     * @param measuredDistance  Distancia cruda del radar en metros.
     * @param dt                Delta tiempo desde la última medición en segundos.
     * @return                  Distancia estimada suavizada en metros.
     */
    float update(float measuredDistance, float dt);

    /** Retorna la velocidad relativa estimada (m/s). Negativo = acercándose. */
    float getEstimatedVelocity() const { return velocity_; }

    /** Reinicia el filtro al estado inicial (usar al cambiar de target). */
    void reset();

private:
    float process_noise_;
    float measure_noise_;

    float distance_;        // Distancia estimada (m)
    float velocity_;        // Velocidad estimada (m/s)
    float p_dist_;          // Covarianza de distancia
    float p_vel_;           // Covarianza de velocidad
    bool  initialized_;
};
