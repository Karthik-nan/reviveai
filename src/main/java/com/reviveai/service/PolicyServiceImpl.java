package com.reviveai.service;

import com.reviveai.dto.PolicyResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PolicyServiceImpl implements PolicyService {

    @Override
    public List<PolicyResponse> getPolicies() {

        return List.of(

                PolicyResponse.builder()
                        .errorCode("INSUFFICIENT_FUNDS")
                        .strategy("RETRY_PAYMENT")
                        .description(
                                "Retry the failed payment when the failure is caused by insufficient funds."
                        )
                        .priorityRule(
                                "HIGH ≥ 0.80, MEDIUM_HIGH ≥ 0.60, MEDIUM ≥ 0.40, LOW < 0.40"
                        )
                        .build(),

                PolicyResponse.builder()
                        .errorCode("CARD_EXPIRED")
                        .strategy("UPDATE_PAYMENT_METHOD")
                        .description(
                                "Request the customer to update their expired payment method."
                        )
                        .priorityRule(
                                "HIGH ≥ 0.80, MEDIUM_HIGH ≥ 0.60, MEDIUM ≥ 0.40, LOW < 0.40"
                        )
                        .build(),

                PolicyResponse.builder()
                        .errorCode("CARD_DECLINED")
                        .strategy("RETRY_PAYMENT")
                        .description(
                                "Retry the failed payment when the card was declined."
                        )
                        .priorityRule(
                                "HIGH ≥ 0.80, MEDIUM_HIGH ≥ 0.60, MEDIUM ≥ 0.40, LOW < 0.40"
                        )
                        .build(),

                PolicyResponse.builder()
                        .errorCode("AUTHENTICATION_FAILED")
                        .strategy("CUSTOMER_ACTION_REQUIRED")
                        .description(
                                "Require customer action when payment authentication fails."
                        )
                        .priorityRule(
                                "HIGH ≥ 0.80, MEDIUM_HIGH ≥ 0.60, MEDIUM ≥ 0.40, LOW < 0.40"
                        )
                        .build(),

                PolicyResponse.builder()
                        .errorCode("UNKNOWN / MISSING")
                        .strategy("MANUAL_REVIEW")
                        .description(
                                "Unknown or missing payment errors are not automatically recovered."
                        )
                        .priorityRule(
                                "Manual review is required when no deterministic strategy is available."
                        )
                        .build()
        );
    }
}