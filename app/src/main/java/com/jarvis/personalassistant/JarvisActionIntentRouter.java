package com.jarvis.personalassistant;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative multilingual routing for explicit Android actions, not general questions. */
public final class JarvisActionIntentRouter {
    private static final Pattern APP_ACTION = Pattern.compile(
        "^(open|launch|start|run|please open|please launch|can you open|could you open|khol|kholo|chalao|chala do)\\b"
    );
    private static final Pattern HINDI_SUFFIX = Pattern.compile(
        ".*\\b(kholo|khol do|khol ke do|chalao|chala do|start karo)\\s*[.!?]*$"
    );
    private JarvisActionIntentRouter() {}

    public static boolean isExplicitAction(String input) {
        if (input == null) return false;
        String q = input.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return false;
        if (q.startsWith("web search ") || q.startsWith("search web ") ||
            q.startsWith("search the web ") || q.startsWith("internet par search ") ||
            q.startsWith("internet pe search ") || q.startsWith("google par search ")) return true;
        if (q.startsWith("how to ") || q.startsWith("how do i ") ||
            q.startsWith("kaise ") || q.startsWith("why ") || q.startsWith("what ")
            || q.startsWith("explain ") || q.startsWith("tell me ")
            || q.startsWith("compare ") || q.startsWith("define ")
            || q.startsWith("is ") || q.startsWith("are ") || q.startsWith("does ")
            || q.startsWith("can i ")) return false;
        if (q.startsWith("open source ") || q.startsWith("open-source ")
                || q.startsWith("open ai ")) return false;
        if (APP_ACTION.matcher(q).find()) return true;
        return HINDI_SUFFIX.matcher(q).matches();
    }
}