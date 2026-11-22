package com.jarvis.personalassistant;

import java.util.Locale;

/**
 * Conservative, deterministic intent gates for local Android actions.
 *
 * A device action is recognized only when the utterance is an explicit command
 * or a narrowly-scoped status request. Explanatory questions mentioning the
 * same nouns should continue to the conversational AI path.
 */
final class JarvisIntentRouter {
    private JarvisIntentRouter() {}

    static String normalize(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .trim().replaceAll("\\s+", " ");
    }

    static boolean isTimeQuery(String raw) {
        String q = normalize(raw);
        return oneOf(q, "time", "current time", "time now", "what time is it",
                "tell me the time", "time batao", "abhi time kya hai",
                "abhi kitne baje hain", "kitne baje", "samay", "samay batao",
                "समय", "समय बताओ", "अभी कितने बजे हैं");
    }

    static boolean isDateQuery(String raw) {
        String q = normalize(raw);
        return oneOf(q, "date", "today date", "today s date", "what is the date",
                "what is today date", "aaj ki date", "aaj ki tareekh",
                "tareekh batao", "आज की तारीख", "आज की डेट");
    }

    static boolean isBatteryStatusQuery(String raw) {
        String q = normalize(raw);
        if (oneOf(q, "battery", "battery status", "battery level", "battery percentage",
                "battery percent", "बैटरी", "बैटरी स्टेटस", "बैटरी कितनी है",
                "फोन की बैटरी कितनी है")) return true;
        return q.matches("(show|check|tell me|what is|whats|how much|kitni|kitna|batao|dikhao) (my )?battery( status| level| percentage| percent)?")
                || q.matches("battery (kitni|kitna) (hai|bacha|bachi hai)")
                || q.matches("meri battery (kitni|kaisi) hai");
    }

    static boolean isSystemReportQuery(String raw) {
        String q = normalize(raw);
        if (oneOf(q, "system status", "system report", "show system report",
                "show system status", "phone status", "device status",
                "phone system status", "ram", "storage", "phone ram", "phone storage",\n                "ram kitni hai", "ram kitna free hai", "storage kitna bacha", "storage kitni bachi hai",\n                "ram status", "storage status",
                "available storage", "free storage", "ram usage",
                "system report dikhao", "phone ka status batao",
                "system status batao", "system report batao", "phone ka status batao")) return true;
        return q.matches("(show|check|tell me|give me) (the )?(phone|device|system) (status|report)")
                || q.matches("(kitni|kitna) (ram|storage) (free|available|bacha|bachi)( hai)?");
    }

    static boolean isFlashlightCommand(String raw) {
        String q = normalize(raw);
        return oneOf(q, "turn on flashlight", "turn off flashlight", "flashlight on",
                "flashlight off", "torch on", "torch off", "torch chalu karo",
                "torch band karo", "flashlight chalu karo", "flashlight band karo",
                "enable flashlight", "disable flashlight");
    }

    static boolean isVolumeUpCommand(String raw) {
        return oneOf(normalize(raw), "volume up", "increase volume", "volume badhao",
                "awaz badhao", "sound badhao");
    }

    static boolean isVolumeDownCommand(String raw) {
        return oneOf(normalize(raw), "volume down", "decrease volume", "volume kam",
                "awaz kam karo", "sound kam karo");
    }

    static boolean isMuteCommand(String raw) {
        return oneOf(normalize(raw), "mute", "mute volume", "mute sound");
    }

    static boolean isBrightnessSettingsCommand(String raw) {
        return oneOf(normalize(raw), "open brightness settings", "open display settings",
                "brightness settings", "screen brightness settings");
    }

    static boolean isWifiSettingsCommand(String raw) {
        return oneOf(normalize(raw), "open wifi settings", "open wi fi settings",
                "wifi settings", "wi fi settings", "open wi fi");
    }

    static boolean isBluetoothSettingsCommand(String raw) {
        return oneOf(normalize(raw), "open bluetooth settings", "bluetooth settings",
                "open bluetooth");
    }

    static boolean isGeneralSettingsCommand(String raw) {
        return oneOf(normalize(raw), "open settings", "open phone settings",
                "phone settings kholo", "settings kholo");
    }

    static boolean isOpenYoutubeCommand(String raw) {
        return oneOf(normalize(raw), "open youtube", "launch youtube", "youtube kholo");
    }

    static boolean isWebSearchCommand(String raw) {
        String q = normalize(raw);
        return q.startsWith("search ") || q.startsWith("web search ")
                || q.startsWith("google search ");
    }

    static boolean isOpenCameraCommand(String raw) {
        return oneOf(normalize(raw), "open camera", "launch camera", "camera kholo");
    }

    static boolean isMapsCommand(String raw) {
        String q = normalize(raw);
        return q.equals("open maps") || q.equals("maps kholo")
                || q.startsWith("navigate to ") || q.startsWith("directions to ")
                || q.startsWith("raasta batao ");
    }

    static boolean isOpenTranslateCommand(String raw) {
        return oneOf(normalize(raw), "open google translate", "open translate",
                "launch google translate");
    }

    static boolean isAlarmCommand(String raw) {
        String q = normalize(raw);
        return q.equals("open alarm") || q.equals("open clock")
                || q.startsWith("set alarm ") || q.equals("set alarm")
                || q.startsWith("alarm lagao ");
    }

    private static boolean oneOf(String value, String... choices) {
        for (String choice : choices) if (choice.equals(value)) return true;
        return false;
    }
}
