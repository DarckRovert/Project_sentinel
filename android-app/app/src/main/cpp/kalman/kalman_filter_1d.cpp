#include "kalman_filter_1d.h"
#include <cmath>

KalmanFilter1D::KalmanFilter1D(float processNoise, float measureNoise)
    : process_noise_(processNoise),
      measure_noise_(measureNoise),
      distance_(0.0f),
      velocity_(0.0f),
      p_dist_(1.0f),
      p_vel_(1.0f),
      initialized_(false) {}

float KalmanFilter1D::update(float measuredDistance, float dt) {
    if (!initialized_) {
        distance_ = measuredDistance;
        velocity_ = 0.0f;
        initialized_ = true;
        return distance_;
    }

    // ---- PREDICT ----
    // Predicción de la distancia: d' = d + v*dt
    float predicted_dist = distance_ + velocity_ * dt;
    // La velocidad se asume constante en el corto plazo
    float predicted_vel  = velocity_;

    // Covarianza de predicción
    float p_dist_pred = p_dist_ + process_noise_;
    float p_vel_pred  = p_vel_  + process_noise_;

    // ---- UPDATE (Innovation) ----
    float innovation  = measuredDistance - predicted_dist;
    float innovation_cov = p_dist_pred + measure_noise_;

    // Ganancia de Kalman
    float K_dist = p_dist_pred / innovation_cov;
    float K_vel  = p_vel_pred  / innovation_cov;

    // Corrección del estado
    distance_ = predicted_dist + K_dist * innovation;
    velocity_ = predicted_vel  + K_vel  * innovation;

    // Actualización de covarianza
    p_dist_ = (1.0f - K_dist) * p_dist_pred;
    p_vel_  = (1.0f - K_vel)  * p_vel_pred;

    return distance_;
}

void KalmanFilter1D::reset() {
    distance_    = 0.0f;
    velocity_    = 0.0f;
    p_dist_      = 1.0f;
    p_vel_       = 1.0f;
    initialized_ = false;
}
