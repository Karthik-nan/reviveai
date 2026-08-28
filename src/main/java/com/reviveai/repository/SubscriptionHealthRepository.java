package com.reviveai.repository;

import com.reviveai.entity.SubscriptionHealth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionHealthRepository
        extends JpaRepository<SubscriptionHealth, UUID> {

    Optional<SubscriptionHealth> findBySubscriptionId(
            UUID subscriptionId
    );

    boolean existsBySubscriptionId(
            UUID subscriptionId
    );
}
