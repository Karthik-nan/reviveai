package com.reviveai.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
public class RecoveryPredictionServiceImpl
        implements RecoveryPredictionService {

    private static final String MODEL_VERSION = "tier2-v1";

    @Override
    public RecoveryPredictionResponse predict(
            RecoveryPredictionRequest request
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Recovery prediction request cannot be null"
            );
        }

        /*
         * Temporary Tier 2 prediction.
         *
         * This is NOT the final ML model.
         * The real scikit-learn model will replace
         * this calculation in the next steps.
         */

        BigDecimal probability =
                calculateBaselineProbability(request);

        log.info(
                "Tier 2 prediction generated. " +
                        "probability={}, modelVersion={}",
                probability,
                MODEL_VERSION
        );

        return RecoveryPredictionResponse.builder()
                .recoveryProbability(probability)
                .modelVersion(MODEL_VERSION)
                .predictionReason(
                        "Tier 2 baseline prediction"
                )
                .build();
    }

    private BigDecimal calculateBaselineProbability(
            RecoveryPredictionRequest request
    ) {

        double score = 0.50;

        /*
         * Successful payment history increases
         * recovery likelihood.
         */
        if (request.getPreviousSuccessfulPayments() >= 5) {
            score += 0.15;
        }

        /*
         * Failed payment history decreases
         * recovery likelihood.
         */
        if (request.getPreviousFailedPayments() >= 3) {
            score -= 0.15;
        }

        /*
         * Higher payment failure rate decreases
         * recovery likelihood.
         */
        if (request.getPaymentFailureRate() != null &&
                request.getPaymentFailureRate()
                        .compareTo(new BigDecimal("0.50")) > 0) {

            score -= 0.10;
        }

        /*
         * Keep probability between 0 and 1.
         */
        score = Math.max(
                0.0,
                Math.min(1.0, score)
        );

        return BigDecimal
                .valueOf(score)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }
}
