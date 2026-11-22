package com.jarvis.personalassistant;

/** Conservative character-based token guard for mixed-language prompts. */
public final class JarvisPromptBudget {
    private JarvisPromptBudget() {}
    public static final int STANDARD_MAX_CHARS = 9000;
    public static final int ASTRA_MAX_CHARS = 28000;

    public static final class Prompt {
        public final String system;
        public final String input;
        public final int estimatedTokens;
        Prompt(String system, String input) {
            this.system = system; this.input = input;
            this.estimatedTokens = estimateTokens(system) + estimateTokens(input);
        }
    }

    public static Prompt fit(String system, String input, int maxTotalChars) {
        int max = Math.max(1200, maxTotalChars);
        String safeSystem = system == null ? "" : system.trim();
        String safeInput = input == null ? "" : input.trim();
        int systemLimit = Math.min(2600, Math.max(500, max / 5));
        if (safeSystem.length() > systemLimit)
            safeSystem = safeSystem.substring(0, systemLimit - 18) + " [system trimmed]";
        int inputLimit = Math.max(500, max - safeSystem.length() - 40);
        if (safeInput.length() > inputLimit) {
            int markerLength = 40;
            int available = Math.max(200, inputLimit - markerLength);
            int head = (int) (available * 0.72);
            int tail = available - head;
            safeInput = safeInput.substring(0, head)
                    + "\n[Earlier context safely truncated]\n"
                    + safeInput.substring(safeInput.length() - tail);
        }
        return new Prompt(safeSystem, safeInput);
    }

    /** Approximation only; intentionally errs toward a conservative budget. */
    public static int estimateTokens(String value) {
        if (value == null || value.isEmpty()) return 0;
        return (value.length() + 1) / 2;
    }
}
