package com.reviveai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_subscription_customer_external",
                        columnNames = {"customer_id", "external_subscription_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_subscriptions_customer_id",
                        columnList = "customer_id"
                ),
                @Index(
                        name = "idx_subscriptions_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_subscriptions_next_billing",
                        columnList = "next_billing_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Every subscription belongs to one customer.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "customer_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_subscription_customer")
    )
    private Customer customer;

    /**
     * Subscription ID from the external payment provider.
     */
    @Column(
            name = "external_subscription_id",
            nullable = false,
            length = 150
    )
    private String externalSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /**
     * Recurring subscription amount.
     */
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal amount;

    /**
     * ISO 4217 currency code.
     * Examples: INR, USD, EUR.
     */
    @Column(
            nullable = false,
            length = 3
    )
    @Builder.Default
    private String currency = "INR";

    /**
     * Expected next billing time.
     */
    @Column(name = "next_billing_at")
    private OffsetDateTime nextBillingAt;

    /**
     * Risk score between 0.00 and 1.00.
     */
    @Column(
            name = "risk_score",
            precision = 3,
            scale = 2
    )
    @Builder.Default
    private BigDecimal riskScore = BigDecimal.ZERO;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {

        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }

        if (this.currency == null) {
            this.currency = "INR";
        }

        if (this.riskScore == null) {
            this.riskScore = BigDecimal.ZERO;
        }

        if (this.status == null) {
            this.status = SubscriptionStatus.ACTIVE;
        }
    }

    public enum SubscriptionStatus {
        ACTIVE,
        PAST_DUE,
        CANCELLED
    }
}