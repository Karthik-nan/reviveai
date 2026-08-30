package com.reviveai.recovery;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;

public interface RecoveryDecisionOrchestrator {

    RecoveryAgentResponse decide(
            RecoveryAgentRequest request
    );
}
