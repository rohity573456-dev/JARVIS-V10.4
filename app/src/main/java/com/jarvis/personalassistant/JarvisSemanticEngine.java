package com.jarvis.personalassistant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline semantic-style recall helpers. This is a lightweight lexical concept
 * matcher, not a neural embedding model; it expands common English/Hinglish
 * concepts and scores memory relevance without sending private data anywhere.
 */
public final class JarvisSemanticEngine {
    private JarvisSemanticEngine() {}

    private static final String[][] CONCEPTS = {
        {"home","house","ghar","residence","live","rehta","rehna"},
        {"work","job","career","profession","office","kaam","naukri"},
        {"food","eat"," खाना","khana","meal","restaurant","dish"},
        {"like","love","favourite","favorite","pasand","choice","prefer"},
        {"goal","target","dream","ambition","aim","lakshya"},
        {"family","father","mother","brother","sister","parents","parivaar"},
        {"money","finance","income","salary","budget","paisa","rupees"},
        {"travel","trip","journey","visit","holiday","safar","ghumna"},
        {"phone","mobile","device","smartphone"},
        {"business","company","factory","shop","dukaan","vyapar"},
        {"learn","study","education","course","padhna","seekhna"},
        {"health","fitness","exercise","workout","diet"}
    };

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "the","a","an","is","am","are","was","were","be","been","being","i","me","my","mine",
        "you","your","we","our","they","their","he","she","it","this","that","these","those",
        "to","of","for","in","on","at","by","with","and","or","but","as","from","about",
        "what","when","where","which","who","how","do","does","did","have","has","had",
        "hai","ho","tha","thi","the","ka","ki","ke","ko","se","par","aur","mera","meri","mere",
        "kya","kab","kahan","kaise","batao","please","jarvis","commander"
    ));

    public static Set<String> terms(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        String normalized = text.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (normalized.isEmpty()) return out;
        for (String raw : normalized.split("\\s+")) {
            String t = raw.trim();
            if (t.length() < 2 || STOP.contains(t)) continue;
            out.add(t);
            if (t.length() > 5 && t.endsWith("ing")) out.add(t.substring(0,t.length()-3));
            else if (t.length() > 4 && t.endsWith("ed")) out.add(t.substring(0,t.length()-2));
            else if (t.length() > 4 && t.endsWith("s")) out.add(t.substring(0,t.length()-1));
        }
        return out;
    }

    public static Set<String> expand(String text) {
        Set<String> result = terms(text);
        Set<String> original = new HashSet<>(result);
        for (String[] group : CONCEPTS) {
            boolean hit = false;
            for (String word : group) if (original.contains(word)) { hit = true; break; }
            if (hit) result.addAll(Arrays.asList(group));
        }
        return result;
    }

    public static int score(String query, String content, String category) {
        Set<String> q = expand(query);
        Set<String> c = terms(content);
        if (q.isEmpty() || c.isEmpty()) return 0;
        int hits = 0;
        for (String term : q) if (c.contains(term)) hits++;
        int exact = 0;
        for (String term : terms(query)) if (c.contains(term)) exact++;
        int score = hits * 10 + exact * 7;
        if (category != null) {
            Set<String> cat = terms(category);
            for (String term : q) if (cat.contains(term)) score += 3;
        }
        String qLower = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String cLower = content.toLowerCase(Locale.ROOT);
        if (!qLower.isEmpty() && cLower.contains(qLower)) score += 30;
        return score;
    }
}
