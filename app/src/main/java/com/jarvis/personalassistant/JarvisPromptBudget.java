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
        String safeSystem = system == null ? "" : system.trim();
        String safeInput = input == null ? "" : input.trim();
        // Devanagari and other non-ASCII scripts can consume close to one token
        // per UTF-16 code unit. Keep mixed-script prompts below the free-tier
        // provider TPM ceiling instead of applying the English 2-chars/token rule.
        int combinedLength = safeSystem.length() + safeInput.length();
        int nonAscii = countNonAscii(safeSystem) + countNonAscii(safeInput);
        int max = Math.max(1200, maxTotalChars);
        if (combinedLength > 0 && nonAscii * 12 > combinedLength)
            max = Math.min(max, 6000);
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

    /** Approximation only; non-ASCII text is budgeted at one token per code unit. */
    public static int estimateTokens(String value) {
        if (value == null || value.isEmpty()) return 0;
        int ascii = 0, nonAscii = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) <= 0x7f) ascii++;
            else nonAscii++;
        }
        return (ascii + 1) / 2 + nonAscii;
    }

    private static int countNonAscii(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) > 0x7f) count++;
        return count;
    }
}
