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

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Subscription whose failed payment needs recovery.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "subscription_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_recovery_subscription")
    )
    private Subscription subscription;

    /**
     * Payment attempt that caused this recovery case.
     *
     * One failed payment creates at most one recovery case.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "failed_payment_id",
            foreignKey = @ForeignKey(name = "fk_recovery_failed_payment")
    )
    private PaymentAttempt failedPayment;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    @Builder.Default
    private RecoveryStatus status = RecoveryStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "recovery_potential",
            nullable = false,
            length = 10
    )
    @Builder.Default
    private RecoveryPotential recoveryPotential = RecoveryPotential.MEDIUM;

    /**
     * Recovery probability/score between 0.00 and 1.00.
     */
    @Column(
            name = "recovery_score",
            nullable = false,
            precision = 3,
            scale = 2
    )
    @Builder.Default
    private BigDecimal recoveryScore = BigDecimal.ZERO;

    /**
     * Revenue currently at risk.
     */
    @Column(
            name = "amount_at_risk",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal amountAtRisk;

    /**
     * Revenue successfully recovered.
     */
    @Column(
            name = "amount_recovered",
            nullable = false,
            precision = 12,
            scale = 2
    )
    @Builder.Default
    private BigDecimal amountRecovered = BigDecimal.ZERO;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {

        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }

        if (this.amountRecovered == null) {
            this.amountRecovered = BigDecimal.ZERO;
        }

        if (this.recoveryScore == null) {
            this.recoveryScore = BigDecimal.ZERO;
        }

        if (this.status == null) {
            this.status = RecoveryStatus.OPEN;
        }

        if (this.recoveryPotential == null) {
            this.recoveryPotential = RecoveryPotential.MEDIUM;
        }
    }

    public enum RecoveryStatus {
        OPEN,
        IN_PROGRESS,
        RECOVERED,
        FAILED,
        ESCALATED
    }

    public enum RecoveryPotential {
        HIGH,
        MEDIUM,
        LOW
    }
}