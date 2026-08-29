package com.reviveai.agent;

import com.reviveai.recovery.RecoveryPriority;
import com.reviveai.recovery.RecoveryStrategy;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryAgentResponse {

    /**
     * Strategy recommended by the AI recovery agent.
     */
    private RecoveryStrategy recommendedStrategy;

    /**
     * Priority of the recommended action.
     */
    private RecoveryPriority priority;

    /**
     * Explanation for the recommendation.
     */
    private String reason;

    /**
     * Agent/model version used.
     */
    private String modelVersion;

    /**
     * Indicates whether the agent had to use
     * a deterministic fallback.
     */
    private boolean fallbackUsed;
}