package com.reviveai.service;

import com.reviveai.entity.Subscription;
import com.reviveai.entity.SubscriptionHealth;

public interface SubscriptionHealthEvaluator {

    SubscriptionHealth evaluateHealth(
            Subscription subscription
    );
}