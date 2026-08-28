package com.reviveai.ml;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class RecoveryMLClassifierImpl implements RecoveryMLClassifier {

    @Override
    public RecoveryMLPrediction predict(RecoveryFeatures features) {

        if (features == null) {
            throw new IllegalArgumentException(
                    "Recovery features cannot be null"
            );
        }

        /*
         * Temporary Tier 2 scoring implementation.
         *
         * This is NOT the final trained ML model.
         * It allows us to validate the complete ML pipeline
         * before connecting a real trained model.
         */

        BigDecimal score = features.getTier1RecoveryScore();

        if (score == null) {
            score = BigDecimal.ZERO;
        }

        // Previous successful payments increase confidence.
        if (features.getPreviousSuccessfulPayments() > 0) {
            score = score.add(new BigDecimal("0.05"));
        }

        // Repeated failures reduce recovery confidence.
        if (features.getPreviousFailedPayments() >= 3) {
            score = score.subtract(new BigDecimal("0.10"));
        }

        // Previous recovery success is a strong positive signal.
        if (features.isPreviousRecoverySuccess()) {
            score = score.add(new BigDecimal("0.10"));
        }

        // Keep probability within [0.00, 1.00].
        score = score.max(BigDecimal.ZERO)
                .min(BigDecimal.ONE)
                .setScale(2, RoundingMode.HALF_UP);

        boolean recoverable =
                score.compareTo(new BigDecimal("0.40")) >= 0;

        return RecoveryMLPrediction.builder()
                .recoveryProbability(score)
                .recoverable(recoverable)
                .modelVersion("tier2-placeholder-v1")
                .build();
    }
}