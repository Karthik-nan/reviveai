package com.reviveai.ml;

public interface RecoveryMLClassifier {

    RecoveryMLPrediction predict(
            RecoveryFeatures features
    );
}