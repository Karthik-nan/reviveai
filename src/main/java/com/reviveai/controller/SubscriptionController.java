package com.reviveai.controller;

import com.reviveai.dto.SubscriptionResponse;
import com.reviveai.entity.Subscription;
import com.reviveai.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;

    /*
     * =========================================================
     * GET ALL SUBSCRIPTIONS
     * =========================================================
     */

    @GetMapping
    public List<SubscriptionResponse> getAllSubscriptions() {

        return subscriptionRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * =========================================================
     * GET SUBSCRIPTION BY ID
     * =========================================================
     */

    @GetMapping("/{id}")
    public SubscriptionResponse getSubscriptionById(
            @PathVariable UUID id
    ) {

        Subscription subscription =
                subscriptionRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Subscription not found: " + id
                                )
                        );

        return toResponse(subscription);
    }

    /*
     * =========================================================
     * ENTITY → RESPONSE DTO
     * =========================================================
     */

    private SubscriptionResponse toResponse(
            Subscription subscription
    ) {

        return SubscriptionResponse.builder()
                .id(subscription.getId())

                .customerId(
                        subscription.getCustomer() != null
                                ? subscription.getCustomer().getId()
                                : null
                )

                .externalSubscriptionId(
                        subscription.getExternalSubscriptionId()
                )

                .status(
                        subscription.getStatus()
                )

                .amount(
                        subscription.getAmount()
                )

                .currency(
                        subscription.getCurrency()
                )

                .nextBillingAt(
                        subscription.getNextBillingAt()
                )

                .riskScore(
                        subscription.getRiskScore()
                )

                .createdAt(
                        subscription.getCreatedAt()
                )

                .build();
    }
}