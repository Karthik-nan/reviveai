package com.reviveai.policy;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;
import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PolicyDecisionServiceImplTest {

    private final PolicyDecisionService service =
            new PolicyDecisionServiceImpl();


    @Test
    void shouldBlockRetryForHighValuePayment() {

        RecoveryAgentRequest request =
                RecoveryAgentRequest.builder()
                        .recoveryCaseId(UUID.randomUUID())
                        .paymentAmount(new BigDecimal("25000"))
                        .retryCount(0)
                        .build();

        RecoveryAgentResponse agentResponse =
                RecoveryAgentResponse.builder()
                        .recommendedStrategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.HIGH
                        )
                        .reason(
                                "High recovery probability"
                        )
                        .modelVersion(
                                "recovery-agent-v1"
                        )
                        .fallbackUsed(false)
                        .build();

        RecoveryAgentResponse result =
                service.validate(
                        request,
                        agentResponse
                );

        assertEquals(
                RecoveryStrategy.MANUAL_REVIEW,
                result.getRecommendedStrategy()
        );

        assertEquals(
                RecoveryPriority.HIGH,
                result.getPriority()
        );
    }


    @Test
    void shouldBlockRetryAfterMaximumRetries() {

        RecoveryAgentRequest request =
                RecoveryAgentRequest.builder()
                        .recoveryCaseId(UUID.randomUUID())
                        .paymentAmount(new BigDecimal("5000"))
                        .retryCount(3)
                        .build();

        RecoveryAgentResponse agentResponse =
                RecoveryAgentResponse.builder()
                        .recommendedStrategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.HIGH
                        )
                        .reason(
                                "High recovery probability"
                        )
                        .modelVersion(
                                "recovery-agent-v1"
                        )
                        .fallbackUsed(false)
                        .build();

        RecoveryAgentResponse result =
                service.validate(
                        request,
                        agentResponse
                );

        assertEquals(
                RecoveryStrategy.MANUAL_REVIEW,
                result.getRecommendedStrategy()
        );
    }


    @Test
    void shouldAllowNormalRetry() {

        RecoveryAgentRequest request =
                RecoveryAgentRequest.builder()
                        .recoveryCaseId(UUID.randomUUID())
                        .paymentAmount(new BigDecimal("5000"))
                        .retryCount(1)
                        .build();

        RecoveryAgentResponse agentResponse =
                RecoveryAgentResponse.builder()
                        .recommendedStrategy(
                                RecoveryStrategy.RETRY_PAYMENT
                        )
                        .priority(
                                RecoveryPriority.HIGH
                        )
                        .reason(
                                "High recovery probability"
                        )
                        .modelVersion(
                                "recovery-agent-v1"
                        )
                        .fallbackUsed(false)
                        .build();

        RecoveryAgentResponse result =
                service.validate(
                        request,
                        agentResponse
                );

        assertEquals(
                RecoveryStrategy.RETRY_PAYMENT,
                result.getRecommendedStrategy()
        );
    }
}
