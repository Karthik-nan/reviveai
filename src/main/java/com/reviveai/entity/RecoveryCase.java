package com.reviveai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "recovery_cases",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_recovery_failed_payment",
                        columnNames = "failed_payment_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_recovery_subscription_id",
                        columnList = "subscription_id"
                ),
                @Index(
                        name = "idx_recovery_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_recovery_potential",
                        columnList = "recovery_potential"
                ),
                @Index(
                        name = "idx_recovery_created_at",
                        columnList = "created_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveryCase {

    // =========================================================
    // ID
    // =========================================================

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    // =========================================================
    // SUBSCRIPTION
    // =========================================================

    /**
     * Subscription whose payment failed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "subscription_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_recovery_subscription"
            )
    )
    private Subscription subscription;


    // =========================================================
    // FAILED PAYMENT
    // =========================================================

    /**
     * Payment attempt that created this recovery case.
     *
     * One failed payment can create only one recovery case.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "failed_payment_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_recovery_failed_payment"
            )
    )
    private PaymentAttempt failedPayment;


    // =========================================================
    // STATUS
    // =========================================================

    /**
     * Current lifecycle state of the recovery case.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    @Builder.Default
    private RecoveryStatus status =
            RecoveryStatus.OPEN;


    // =========================================================
    // RECOVERY POTENTIAL
    // =========================================================

    /**
     * Business classification of how valuable/recoverable
     * this failed payment is.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "recovery_potential",
            nullable = false,
            length = 10
    )
    @Builder.Default
    private RecoveryPotential recoveryPotential =
            RecoveryPotential.MEDIUM;


    // =========================================================
    // ML RECOVERY SCORE
    // =========================================================

    /**
     * ML-generated probability that the payment can be recovered.
     *
     * Expected range:
     *
     * 0.00 -> 1.00
     */
    @Column(
            name = "recovery_score",
            nullable = false,
            precision = 3,
            scale = 2
    )
    @Builder.Default
    private BigDecimal recoveryScore =
            BigDecimal.ZERO;


    // =========================================================
    // AMOUNT AT RISK
    // =========================================================

    /**
     * Amount currently at risk because of the failed payment.
     */
    @Column(
            name = "amount_at_risk",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal amountAtRisk;


    // =========================================================
    // AMOUNT RECOVERED
    // =========================================================

    /**
     * Amount successfully recovered.
     *
     * This remains zero until a successful payment webhook
     * confirms the recovery.
     */
    @Column(
            name = "amount_recovered",
            nullable = false,
            precision = 12,
            scale = 2
    )
    @Builder.Default
    private BigDecimal amountRecovered =
            BigDecimal.ZERO;


    // =========================================================
    // CREATED AT
    // =========================================================

    /**
     * Time at which the recovery case was created.
     */
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;


    // =========================================================
    // RESOLVED AT
    // =========================================================

    /**
     * Time at which the recovery case reached a final state.
     *
     * This should remain null while the case is:
     *
     * OPEN
     * IN_PROGRESS
     * ESCALATED
     */
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;


    // =========================================================
    // PRE-PERSIST
    // =========================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }

        if (amountRecovered == null) {
            amountRecovered = BigDecimal.ZERO;
        }

        if (recoveryScore == null) {
            recoveryScore = BigDecimal.ZERO;
        }

        if (status == null) {
            status = RecoveryStatus.OPEN;
        }

        if (recoveryPotential == null) {
            recoveryPotential = RecoveryPotential.MEDIUM;
        }
    }


    // =========================================================
    // RECOVERY STATUS
    // =========================================================

    public enum RecoveryStatus {

        /**
         * Recovery case has been created but no action
         * has been executed yet.
         */
        OPEN,

        /**
         * Recovery action has been submitted and the
         * final payment result is still pending.
         */
        IN_PROGRESS,

        /**
         * Payment recovery was confirmed successfully
         * by the payment provider.
         */
        RECOVERED,

        /**
         * Recovery attempt failed.
         */
        FAILED,

        /**
         * Automated recovery could not safely continue
         * and requires further handling.
         */
        ESCALATED
    }


    // =========================================================
    // RECOVERY POTENTIAL
    // =========================================================

    public enum RecoveryPotential {

        HIGH,

        MEDIUM,

        LOW
    }
}
