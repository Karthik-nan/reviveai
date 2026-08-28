package com.reviveai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "subscription_health",
        indexes = {
                @Index(
                        name = "idx_subscription_health_subscription",
                        columnList = "subscription_id",
                        unique = true
                ),
                @Index(
                        name = "idx_subscription_health_risk_level",
                        columnList = "risk_level"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionHealth {

    // =========================================================
    // Primary Key
    // =========================================================

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    // =========================================================
    // Subscription
    // =========================================================

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "subscription_id",
            nullable = false,
            unique = true
    )
    private Subscription subscription;


    // =========================================================
    // Health Score
    // =========================================================

    /*
     * Overall subscription health score.
     *
     * Range:
     *
     * 0.00 -> Very unhealthy
     * 1.00 -> Very healthy
     */

    @Column(
            name = "health_score",
            precision = 5,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal healthScore =
            new BigDecimal("1.00");


    // =========================================================
    // Risk Level
    // =========================================================

    @Enumerated(EnumType.STRING)
    @Column(
            name = "risk_level",
            nullable = false,
            length = 20
    )
    @Builder.Default
    private RiskLevel riskLevel =
            RiskLevel.HEALTHY;


    // =========================================================
    // Payment Statistics
    // =========================================================

    @Column(
            name = "successful_payment_count",
            nullable = false
    )
    @Builder.Default
    private Integer successfulPaymentCount = 0;


    @Column(
            name = "failed_payment_count",
            nullable = false
    )
    @Builder.Default
    private Integer failedPaymentCount = 0;


    @Column(
            name = "payment_failure_rate",
            precision = 5,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal paymentFailureRate =
            BigDecimal.ZERO;


    // =========================================================
    // Recent Failure Information
    // =========================================================

    @Column(
            name = "consecutive_failures",
            nullable = false
    )
    @Builder.Default
    private Integer consecutiveFailures = 0;


    @Column(
            name = "last_failure_at"
    )
    private LocalDateTime lastFailureAt;


    @Column(
            name = "last_success_at"
    )
    private LocalDateTime lastSuccessAt;


    // =========================================================
    // Risk Signals
    // =========================================================

    /*
     * Number of payment failures during the
     * recent evaluation window.
     */

    @Column(
            name = "recent_failure_count",
            nullable = false
    )
    @Builder.Default
    private Integer recentFailureCount = 0;


    /*
     * Indicates whether the subscription has
     * shown a recent deterioration in payment behavior.
     */

    @Column(
            name = "payment_behavior_declining",
            nullable = false
    )
    @Builder.Default
    private Boolean paymentBehaviorDeclining = false;


    // =========================================================
    // Preventive Action
    // =========================================================

    /*
     * Prevents repeatedly triggering the same
     * preventive recovery workflow.
     */

    @Column(
            name = "preventive_action_triggered",
            nullable = false
    )
    @Builder.Default
    private Boolean preventiveActionTriggered = false;


    @Column(
            name = "last_evaluated_at"
    )
    private LocalDateTime lastEvaluatedAt;


    // =========================================================
    // Risk Level
    // =========================================================

    public enum RiskLevel {

        HEALTHY,

        LOW,

        MEDIUM,

        HIGH,

        CRITICAL
    }
}
