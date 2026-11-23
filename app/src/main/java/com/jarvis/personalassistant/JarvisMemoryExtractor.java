package com.jarvis.personalassistant;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative, Hinglish-aware structured fact extractor. Unknown text is not auto-saved. */
public final class JarvisMemoryExtractor {
    private JarvisMemoryExtractor() {}

    private static String clean(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("[.!?]+$", "").trim();
    }

    private static String slug(String value) {
        String s = value == null ? "" : value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.length() > 48 ? s.substring(0, 48) : s;
    }

    static boolean sensitive(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        // Apply the same comprehensive identifier/credential/financial and sensitive-topic
        // filter used by AI memory extraction before any local automatic learning.
        if (JarvisSensitiveDataFilter.isSensitiveText(s)) return true;
        String q = s.toLowerCase(Locale.ROOT);
        String[] blocked = {"password","passcode","otp","cvv","aadhaar","aadhar","pan number",
            "bank account","credit card","debit card","diagnosis","medicine","medical condition",
            "disease","sexual","religion","political party"};
        for (String word : blocked) if (q.contains(word)) return true;
        return false;
    }

    private static boolean store(JarvisMemoryStore db, String category, String type, String key,
            String content, double confidence, int importance, boolean pinned) {
        return db.upsertFact(category, type, key, content, confidence, importance, pinned, "conversation") > 0;
    }

    private static Matcher match(String regex, String value) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(value);
        return m.matches() ? m : null;
    }

    /** Returns true only when a durable, non-sensitive fact was actually learned. */
    public static boolean learn(JarvisMemoryStore db, String raw) {
        if (db == null || raw == null) return false;
        String s = raw.trim();
        if (s.length() < 8 || s.length() > 800 || sensitive(s)) return false;
        String q = s.toLowerCase(Locale.ROOT);
        if (q.startsWith("memory ") || q.startsWith("task ") || q.startsWith("reminder ") || q.startsWith("search ")) return false;

        String value;
        Matcher m;

        m = match("^(?:actually[, ]+|correction:?\\s*|no[, ]+|nahi[, ]+)?(?:my name is|mera naam(?: hai)?)\\s+(.+)$", s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"profile","core","profile.name","My name is "+value,0.99,100,true);

        m = match("^(?:actually[, ]+|correction:?\\s*)?(?:i live in|main rehta hoon|main rehti hoon|mera ghar hai)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"profile","core","profile.location","I live in "+value,0.92,90,true);

        m = match("^(?:actually[, ]+|correction:?\\s*)?(?:i am from|i come from|main .+ se hoon)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"profile","core","profile.origin","I am from "+value,0.90,80,false);

        m = match("^(?:actually[, ]+|correction:?\\s*)?(?:i work as|my job is|my occupation is|main kaam karta hoon|main kaam karti hoon)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"profile","core","profile.occupation","My work/occupation: "+value,0.90,85,true);

        m = match("^(?:actually[, ]+|correction:?\\s*)?(?:my goal is|my dream is|mera goal hai|mera lakshya hai)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"goals","semantic","profile.goal","My current goal is "+value,0.90,90,true);

        m = match("^(?:i am working on|i'm working on|my current project is|mera project hai)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"projects","semantic","project.current","Current project: "+value,0.85,80,false);

        m = match("^(?:my favourite|my favorite|mera favourite|meri favourite)\\s+(.+?)\\s+(?:is|hai)\\s+(.+)$",s);
        if (m != null) {
            String field=slug(clean(m.group(1))), answer=clean(m.group(2));
            if (!field.isEmpty() && !answer.isEmpty())
                return store(db,"preference","core","preference.favorite."+field,
                    "My favourite "+clean(m.group(1))+" is "+answer,0.95,82,false);
        }

        m = match("^(?:my preferred language is|i prefer to speak|meri preferred language)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"preference","core","preference.language","Preferred language: "+value,0.90,75,false);

        m = match("^(?:i prefer|my preference is|mujhe pasand hai)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty()) {
            String key="preference."+slug(value);
            return store(db,"preference","procedural",key,"Preference: "+value,0.82,65,false);
        }

        m = match("^(?:from now on[, ]+|going forward[, ]+|hamesha[, ]+|aage se[, ]+)(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty() && value.length() <= 220) {
            String key="rule."+slug(value);
            return store(db,"instructions","procedural",key,"Standing instruction: "+value,0.88,88,true);
        }

        m = match("^(?:i study|i am studying|my education is|main padh raha hoon|main padh rahi hoon)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"education","semantic","profile.education","Education: "+value,0.84,70,false);
        m = match("^(?:my company is|i own|mera business hai|meri company hai)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"business","semantic","profile.business","Business: "+value,0.84,75,false);
        m = match("^(?:i am building|i am developing|i am creating|i am working on|my current project is|mera project hai)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"projects","semantic","project.current","Current project: "+value,0.86,82,false);
        m = match("^(?:my preferred response style is|i want replies|reply to me|mujhe jawab)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"instructions","procedural","instruction.response_style","Response style: "+value,0.90,90,true);
        m = match("^(?:i no longer|i don't like|i do not like|mujhe ab pasand nahi)\\s+(.+)$",s);
        if (m != null && !(value=clean(m.group(1))).isEmpty())
            return store(db,"preference","procedural","preference.avoid."+slug(value),"Avoid preference: "+value,0.84,70,false);
        return false;
    }
}