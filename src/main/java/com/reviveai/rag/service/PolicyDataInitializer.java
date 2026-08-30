package com.reviveai.rag;

import com.reviveai.rag.service.PolicyLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyDataInitializer implements CommandLineRunner {

    private final PolicyLoaderService policyLoaderService;

    @Override
    public void run(String... args) {

        try {
            log.info("Starting policy ingestion...");

            policyLoaderService.loadPolicies();

            log.info("Policy ingestion completed successfully.");

        } catch (Exception e) {

            log.error(
                    "Policy ingestion failed",
                    e
            );
        }
    }
}
