package com.ghana.gwire.ai;

/**
 * Result of a co-pilot command: reply text and optional plan to apply.
 *
 * @param reply user-facing message
 * @param plan  optional placements to apply (may be null)
 */
public record AiCopilotResult(String reply, AiDesignPlan plan) {

    public AiCopilotResult {
        if (reply == null || reply.isBlank()) {
            reply = "(no reply)";
        }
    }

    public static AiCopilotResult text(String reply) {
        return new AiCopilotResult(reply, null);
    }

    public static AiCopilotResult withPlan(String reply, AiDesignPlan plan) {
        return new AiCopilotResult(reply, plan);
    }

    public boolean hasPlan() {
        return plan != null && !plan.isEmpty();
    }
}
