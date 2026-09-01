package com.reviveai.controller;

import com.reviveai.dto.CustomerDetailsResponse;
import com.reviveai.dto.CustomerResponse;
import com.reviveai.dto.CustomerSubscriptionResponse;
import com.reviveai.entity.Customer;
import com.reviveai.entity.RecoveryCase;
import com.reviveai.entity.Subscription;
import com.reviveai.repository.CustomerRepository;
import com.reviveai.repository.RecoveryCaseRepository;
import com.reviveai.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final RecoveryCaseRepository recoveryCaseRepository;


    /*
     * =========================================================
     * GET ALL CUSTOMERS
     * =========================================================
     */

    @GetMapping
    public List<CustomerResponse> getAllCustomers() {

        return customerRepository.findAll()
                .stream()
                .map(this::toCustomerResponse)
                .toList();
    }


    /*
     * =========================================================
     * GET CUSTOMER BY ID
     * =========================================================
     */

    @GetMapping("/{id}")
    public CustomerDetailsResponse getCustomerById(
            @PathVariable UUID id
    ) {

        Customer customer =
                customerRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Customer not found: " + id
                                )
                        );

        List<Subscription> subscriptions =
                subscriptionRepository.findByCustomerId(
                        customer.getId()
                );

        int subscriptionCount =
                subscriptions.size();

        int activeSubscriptions =
                (int) subscriptions.stream()
                        .filter(subscription ->
                                subscription.getStatus()
                                        == Subscription.SubscriptionStatus.ACTIVE
                        )
                        .count();

        int pastDueSubscriptions =
                (int) subscriptions.stream()
                        .filter(subscription ->
                                subscription.getStatus()
                                        == Subscription.SubscriptionStatus.PAST_DUE
                        )
                        .count();

        int cancelledSubscriptions =
                (int) subscriptions.stream()
                        .filter(subscription ->
                                subscription.getStatus()
                                        == Subscription.SubscriptionStatus.CANCELLED
                        )
                        .count();


        int recoveryCaseCount = 0;

        BigDecimal revenueAtRisk =
                BigDecimal.ZERO;

        BigDecimal revenueRecovered =
                BigDecimal.ZERO;


        for (Subscription subscription : subscriptions) {

            List<RecoveryCase> recoveryCases =
                    recoveryCaseRepository
                            .findBySubscriptionId(
                                    subscription.getId()
                            );

            recoveryCaseCount +=
                    recoveryCases.size();


            for (RecoveryCase recoveryCase :
                    recoveryCases) {

                if (recoveryCase.getAmountAtRisk() != null) {

                    revenueAtRisk =
                            revenueAtRisk.add(
                                    recoveryCase.getAmountAtRisk()
                            );
                }


                if (recoveryCase.getAmountRecovered() != null) {

                    revenueRecovered =
                            revenueRecovered.add(
                                    recoveryCase.getAmountRecovered()
                            );
                }
            }
        }


        return CustomerDetailsResponse.builder()

                .id(customer.getId())

                .externalCustomerId(
                        customer.getExternalCustomerId()
                )

                .email(
                        customer.getEmail()
                )

                .createdAt(
                        customer.getCreatedAt()
                )

                .subscriptionCount(
                        subscriptionCount
                )

                .activeSubscriptions(
                        activeSubscriptions
                )

                .pastDueSubscriptions(
                        pastDueSubscriptions
                )

                .cancelledSubscriptions(
                        cancelledSubscriptions
                )

                .recoveryCaseCount(
                        recoveryCaseCount
                )

                .revenueAtRisk(
                        revenueAtRisk
                )

                .revenueRecovered(
                        revenueRecovered
                )

                .subscriptions(
                        subscriptions.stream()
                                .map(
                                        this::toSubscriptionResponse
                                )
                                .toList()
                )

                .build();
    }


    /*
     * =========================================================
     * CUSTOMER LIST MAPPING
     * =========================================================
     */

    private CustomerResponse toCustomerResponse(
            Customer customer
    ) {

        List<Subscription> subscriptions =
                subscriptionRepository.findByCustomerId(
                        customer.getId()
                );


        int subscriptionCount =
                subscriptions.size();


        int activeSubscriptions =
                (int) subscriptions.stream()
                        .filter(subscription ->
                                subscription.getStatus()
                                        == Subscription.SubscriptionStatus.ACTIVE
                        )
                        .count();


        int pastDueSubscriptions =
                (int) subscriptions.stream()
                        .filter(subscription ->
                                subscription.getStatus()
                                        == Subscription.SubscriptionStatus.PAST_DUE
                        )
                        .count();


        int cancelledSubscriptions =
                (int) subscriptions.stream()
                        .filter(subscription ->
                                subscription.getStatus()
                                        == Subscription.SubscriptionStatus.CANCELLED
                        )
                        .count();


        int recoveryCaseCount = 0;

        BigDecimal revenueAtRisk =
                BigDecimal.ZERO;

        BigDecimal revenueRecovered =
                BigDecimal.ZERO;


        for (Subscription subscription :
                subscriptions) {

            List<RecoveryCase> recoveryCases =
                    recoveryCaseRepository
                            .findBySubscriptionId(
                                    subscription.getId()
                            );


            recoveryCaseCount +=
                    recoveryCases.size();


            for (RecoveryCase recoveryCase :
                    recoveryCases) {

                if (recoveryCase.getAmountAtRisk() != null) {

                    revenueAtRisk =
                            revenueAtRisk.add(
                                    recoveryCase.getAmountAtRisk()
                            );
                }


                if (recoveryCase.getAmountRecovered() != null) {

                    revenueRecovered =
                            revenueRecovered.add(
                                    recoveryCase.getAmountRecovered()
                            );
                }
            }
        }


        return CustomerResponse.builder()

                .id(customer.getId())

                .externalCustomerId(
                        customer.getExternalCustomerId()
                )

                .email(
                        customer.getEmail()
                )

                .createdAt(
                        customer.getCreatedAt()
                )

                .subscriptionCount(
                        subscriptionCount
                )

                .activeSubscriptions(
                        activeSubscriptions
                )

                .pastDueSubscriptions(
                        pastDueSubscriptions
                )

                .cancelledSubscriptions(
                        cancelledSubscriptions
                )

                .recoveryCaseCount(
                        recoveryCaseCount
                )

                .revenueAtRisk(
                        revenueAtRisk
                )

                .revenueRecovered(
                        revenueRecovered
                )

                .build();
    }


    /*
     * =========================================================
     * SUBSCRIPTION MAPPING
     * =========================================================
     */

    private CustomerSubscriptionResponse
    toSubscriptionResponse(
            Subscription subscription
    ) {

        return CustomerSubscriptionResponse.builder()

                .id(
                        subscription.getId()
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
