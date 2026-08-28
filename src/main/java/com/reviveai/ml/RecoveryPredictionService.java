package com.reviveai.ml;

public interface RecoveryPredictionService {

    RecoveryPredictionResponse predict(
            RecoveryPredictionRequest request
    );
}