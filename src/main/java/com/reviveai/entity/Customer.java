package com.reviveai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_customer_merchant_external",
                        columnNames = {"merchant_id", "external_customer_id"}
                )
        },
        indexes = {
                @Index(name = "idx_customers_merchant_id", columnList = "merchant_id"),
                @Index(name = "idx_customers_email", columnList = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Every customer belongs to exactly one merchant.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "merchant_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_customer_merchant")
    )
    private Merchant merchant;

    /**
     * Customer ID from the merchant's external payment system.
     */
    @Column(
            name = "external_customer_id",
            nullable = false,
            length = 150
    )
    private String externalCustomerId;

    @Column(
            nullable = false,
            length = 320
    )
    private String email;

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
    }
}