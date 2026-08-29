package com.reviveai.agent;

import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
@Service
public class RecoveryAgentServiceImpl
        implements RecoveryAgentService {

    private static final String AGENT_MODEL_VERSION =
            "recovery-agent-v1";

    private static final String FALLBACK_MODEL_VERSION =
            "recovery-agent-fallback-v1";

    private static final BigDecimal HIGH_RECOVERY_THRESHOLD =
            new BigDecimal("0.75");

    private static final BigDecimal MEDIUM_RECOVERY_THRESHOLD =
            new BigDecimal("0.40");

    private static final int MAX_AUTOMATIC_RETRIES = 3;


    // =========================================================
    // RECOMMEND
    // =========================================================

    @Override
    public RecoveryAgentResponse recommend(
            RecoveryAgentRequest request
    ) {

        // =====================================================
        // 1. VALIDATE REQUEST
        // =====================================================

        if (request == null) {

            log.warn(
                    "Recovery agent received null request"
            );

            return fallback(
                    "Recovery agent received an invalid request."
            );
        }


        // =====================================================
        // 2. LOG INPUT
        // =====================================================

        log.info(
                "Recovery agent evaluating case. " +
                        "recoveryCaseId={}, recoveryScore={}, " +
                        "paymentAmount={}, recoveryPotential={}, " +
                        "errorCode={}, retryCount={}, " +
                        "failureRate={}, ruleStrategy={}, " +
                        "rulePriority={}",

                request.getRecoveryCaseId(),
                request.getRecoveryScore(),
                request.getPaymentAmount(),
                request.getRecoveryPotential(),
                request.getPaymentErrorCode(),
                request.getRetryCount(),
                request.getPaymentFailureRate(),
                request.getRuleBasedStrategy(),
                request.getRuleBasedPriority()
        );


        // =====================================================
        // 3. VALIDATE RECOVERY SCORE
        // =====================================================

        BigDecimal recoveryScore =
                request.getRecoveryScore();

        if (recoveryScore == null) {

            log.warn(
                    "Recovery score unavailable. " +
                            "Using fallback. recoveryCaseId={}",
                    request.getRecoveryCaseId()
            );

            return fallback(
                    "Recovery probability is unavailable."
            );
        }


        // =====================================================
        // 4. VALIDATE SCORE RANGE
        // =====================================================

        if (recoveryScore.compareTo(BigDecimal.ZERO) < 0
                || recoveryScore.compareTo(BigDecimal.ONE) > 0) {

            log.warn(
                    "Invalid recovery score. " +
                            "recoveryCaseId={}, score={}",
                    request.getRecoveryCaseId(),
                    recoveryScore
            );

            return fallback(
                    "Recovery probability must be between 0.00 and 1.00."
            );
        }


        // =====================================================
        // 5. NORMALIZE ERROR CODE
        // =====================================================

        String errorCode =
                normalize(
                        request.getPaymentErrorCode()
                );


        // =====================================================
        // 6. PAYMENT METHOD PROBLEM
        // =====================================================

        /*
         * Payment-method failures should not be blindly retried.
         */
        if (isPaymentMethodProblem(errorCode)) {

            log.info(
                    "Payment method problem detected. " +
                            "caseId={}, errorCode={}",
                    request.getRecoveryCaseId(),
                    errorCode
            );

            return buildResponse(
                    RecoveryStrategy.UPDATE_PAYMENT_METHOD,
                    RecoveryPriority.HIGH,
                    "The payment failure indicates a payment-method " +
                            "problem. Updating the payment method is " +
                            "preferred over an immediate retry."
            );
        }


        // =====================================================
        // 7. RETRY COUNT
        // =====================================================

        int retryCount =
                request.getRetryCount() == null
                        ? 0
                        : Math.max(
                        request.getRetryCount(),
                        0
                );


        if (retryCount >= MAX_AUTOMATIC_RETRIES) {

            log.info(
                    "Automatic retry limit reached. " +
                            "caseId={}, retryCount={}",
                    request.getRecoveryCaseId(),
                    retryCount
            );

            return buildResponse(
                    RecoveryStrategy.MANUAL_REVIEW,
                    RecoveryPriority.HIGH,
                    "The maximum number of automatic retries " +
                            "has already been reached. Manual review " +
                            "is recommended."
            );
        }


        // =====================================================
        // 8. HIGH RECOVERY PROBABILITY
        // =====================================================

        if (recoveryScore.compareTo(
                HIGH_RECOVERY_THRESHOLD
        ) >= 0) {

            return buildResponse(
                    RecoveryStrategy.RETRY_PAYMENT,
                    RecoveryPriority.HIGH,
                    "The recovery model predicts a high probability " +
                            "of successful recovery. A controlled " +
                            "payment retry is recommended."
            );
        }


        // =====================================================
        // 9. MEDIUM RECOVERY PROBABILITY
        // =====================================================

        if (recoveryScore.compareTo(
                MEDIUM_RECOVERY_THRESHOLD
        ) >= 0) {

            RecoveryStrategy strategy =
                    request.getRuleBasedStrategy();

            RecoveryPriority priority =
                    request.getRuleBasedPriority();


            if (strategy == null) {

                strategy =
                        RecoveryStrategy.CUSTOMER_ACTION_REQUIRED;
            }


            if (priority == null) {

                priority =
                        RecoveryPriority.MEDIUM;
            }


            return buildResponse(
                    strategy,
                    priority,
                    "The recovery model indicates moderate recovery " +
                            "potential. The rule-based recovery strategy " +
                            "is retained."
            );
        }


        // =====================================================
        // 10. LOW RECOVERY PROBABILITY
        // =====================================================

        return buildResponse(
                RecoveryStrategy.MANUAL_REVIEW,
                RecoveryPriority.HIGH,
                "The recovery model predicts a low probability " +
                        "of automatic recovery. Manual review is " +
                        "recommended."
        );
    }


    // =========================================================
    // PAYMENT METHOD PROBLEM
    // =========================================================

    private boolean isPaymentMethodProblem(
            String errorCode
    ) {

        if (errorCode == null
                || errorCode.isBlank()) {

            return false;
        }

        return errorCode.contains("authentication")
                || errorCode.contains("invalid")
                || errorCode.contains("expired")
                || errorCode.contains("card_not_supported")
                || errorCode.contains("payment_method")
                || errorCode.contains("instrument")
                || errorCode.contains("card_expired")
                || errorCode.contains("invalid_card");
    }


    // =========================================================
    // NORMALIZE
    // =========================================================

    private String normalize(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT);
    }


    // =========================================================
    // BUILD RESPONSE
    // =========================================================

    private RecoveryAgentResponse buildResponse(
            RecoveryStrategy strategy,
            RecoveryPriority priority,
            String reason
    ) {

        log.info(
                "Recovery agent recommendation. " +
                        "strategy={}, priority={}, reason={}",
                strategy,
                priority,
                reason
        );

        return RecoveryAgentResponse.builder()
                .recommendedStrategy(strategy)
                .priority(priority)
                .reason(reason)
                .modelVersion(AGENT_MODEL_VERSION)
                .fallbackUsed(false)
                .build();
    }


    // =========================================================
    // FALLBACK
    // =========================================================

    private RecoveryAgentResponse fallback(
            String reason
    ) {

        log.warn(
                "Recovery agent fallback activated. reason={}",
                reason
        );

        return RecoveryAgentResponse.builder()
                .recommendedStrategy(
                        RecoveryStrategy.MANUAL_REVIEW
                )
                .priority(
                        RecoveryPriority.HIGH
                )
                .reason(reason)
                .modelVersion(
                        FALLBACK_MODEL_VERSION
                )
                .fallbackUsed(true)
                .build();
    }
}