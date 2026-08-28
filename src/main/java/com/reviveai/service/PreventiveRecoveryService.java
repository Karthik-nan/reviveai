package com.reviveai.service;

import com.reviveai.entity.RecoveryAction;
import com.reviveai.entity.SubscriptionHealth;

public interface PreventiveRecoveryService {

    boolean shouldTriggerPreventiveRecovery(
            SubscriptionHealth health
    );

    RecoveryAction createPreventiveRecoveryAction(
            SubscriptionHealth health
    );
}
