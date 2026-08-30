package com.reviveai.policy;

import com.reviveai.agent.RecoveryAgentRequest;
import com.reviveai.agent.RecoveryAgentResponse;

public interface PolicyDecisionService {

    RecoveryAgentResponse validate(
            RecoveryAgentRequest request,
            RecoveryAgentResponse agentResponse
    );
}