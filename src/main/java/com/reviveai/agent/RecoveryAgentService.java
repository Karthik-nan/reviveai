package com.reviveai.agent;

public interface RecoveryAgentService {

    /**
     * Analyzes a recovery case and recommends
     * the safest recovery strategy.
     *
     * The agent only recommends an action.
     * It does NOT execute payments or call Razorpay.
     *
     * @param request recovery context
     * @return agent recommendation
     */
    RecoveryAgentResponse recommend(
            RecoveryAgentRequest request
    );
}