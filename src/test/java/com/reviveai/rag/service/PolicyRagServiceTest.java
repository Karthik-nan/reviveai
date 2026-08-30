package com.reviveai.rag.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PolicyRagServiceTest {

    @Autowired
    private PolicyRagService policyRagService;

    @Test
    void shouldRetrieveHighValuePaymentPolicy() {

        List<Document> results =
                policyRagService.searchPolicies(
                        "Payments above INR 20,000",
                        3
                );

        assertFalse(
                results.isEmpty(),
                "RAG should retrieve at least one policy"
        );

        System.out.println("===== HIGH VALUE POLICY SEARCH =====");

        results.forEach(document -> {
            System.out.println("Content:");
            System.out.println(document.getText());

            System.out.println("Metadata:");
            System.out.println(document.getMetadata());

            System.out.println("--------------------------------");
        });

        boolean foundHighValuePolicy =
                results.stream()
                        .anyMatch(document ->
                                document.getText()
                                        .contains("HIGH VALUE PAYMENT POLICY")
                        );

        assertTrue(
                foundHighValuePolicy,
                "High-value payment policy should be retrieved"
        );
    }


    @Test
    void shouldRetrievePaymentRetryPolicy() {

        List<Document> results =
                policyRagService.searchPolicies(
                        "How many times can a payment be retried?",
                        3
                );

        assertFalse(
                results.isEmpty(),
                "RAG should retrieve at least one policy"
        );

        System.out.println("===== PAYMENT RETRY POLICY SEARCH =====");

        results.forEach(document ->
                System.out.println(document.getText())
        );

        boolean foundRetryPolicy =
                results.stream()
                        .anyMatch(document ->
                                document.getText()
                                        .contains("PAYMENT RETRY POLICY")
                        );

        assertTrue(
                foundRetryPolicy,
                "Payment retry policy should be retrieved"
        );
    }


    @Test
    void shouldRetrieveCustomerNotificationPolicy() {

        List<Document> results =
                policyRagService.searchPolicies(
                        "When should the customer be notified about a failed payment?",
                        3
                );

        assertFalse(
                results.isEmpty(),
                "RAG should retrieve at least one policy"
        );

        System.out.println(
                "===== CUSTOMER NOTIFICATION POLICY SEARCH ====="
        );

        results.forEach(document ->
                System.out.println(document.getText())
        );

        boolean foundNotificationPolicy =
                results.stream()
                        .anyMatch(document ->
                                document.getText()
                                        .contains(
                                                "CUSTOMER NOTIFICATION POLICY"
                                        )
                        );

        assertTrue(
                foundNotificationPolicy,
                "Customer notification policy should be retrieved"
        );
    }
}