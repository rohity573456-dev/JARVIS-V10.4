package com.jarvis.personalassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Typed, allow-listed tool boundary for the JARVIS agent.
 * Model output can request a registered tool, but can never provide executable code.
 */
public final class JarvisToolRegistry {
    public enum Risk { READ_ONLY, REVERSIBLE, SENSITIVE, EXTERNAL }

    public interface Handler {
        ToolResult execute(Map<String, String> arguments) throws Exception;
    }

    public static final class ToolResult {
        public final boolean success;
        public final String output;
        public final boolean verified;

        private ToolResult(boolean success, String output, boolean verified) {
            this.success = success;
            this.output = output == null ? "" : output;
            this.verified = verified;
        }
        public static ToolResult success(String output) { return new ToolResult(true, output, true); }
        public static ToolResult unverified(String output) { return new ToolResult(true, output, false); }
        public static ToolResult failure(String output) { return new ToolResult(false, output, false); }
    }

    public static final class ToolSpec {
        public final String name;
        public final String description;
        public final Risk risk;
        public final Set<String> requiredArguments;
        public final Set<String> optionalArguments;
        public final String permission;
        private final Handler handler;

        public ToolSpec(String name, String description, Risk risk,
                Set<String> requiredArguments, Set<String> optionalArguments,
                String permission, Handler handler) {
            if (name == null || !name.matches("[a-z][a-z0-9_]{1,63}"))
                throw new IllegalArgumentException("Invalid tool name");
            if (description == null || description.trim().isEmpty() || risk == null || handler == null)
                throw new IllegalArgumentException("Tool metadata and handler are required");
            this.name = name;
            this.description = description;
            this.risk = risk;
            this.requiredArguments = immutableCopy(requiredArguments);
            this.optionalArguments = immutableCopy(optionalArguments);
            this.permission = permission == null ? "" : permission;
            this.handler = handler;
            for (String key : this.requiredArguments)
                if (this.optionalArguments.contains(key)) throw new IllegalArgumentException("Argument cannot be both required and optional");
        }
        private static Set<String> immutableCopy(Set<String> input) {
            return Collections.unmodifiableSet(new HashSet<>(input == null ? Collections.<String>emptySet() : input));
        }
        public List<String> argumentErrors(Map<String, String> args) {
            List<String> errors = new ArrayList<>();
            Map<String, String> safe = args == null ? Collections.<String, String>emptyMap() : args;
            if (safe.size() > 32) errors.add("Too many tool arguments");
            for (String key : requiredArguments) if (blank(safe.get(key))) errors.add("Missing required argument: " + key);
            for (Map.Entry<String, String> entry : safe.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || key.length() > 64) {
                    errors.add("Invalid argument name");
                    continue;
                }
                if (!requiredArguments.contains(key) && !optionalArguments.contains(key))
                    errors.add("Unexpected argument: " + key);
                if (value != null && value.length() > maxArgumentLength(key))
                    errors.add("Argument exceeds the allowed length: " + key);
            }
            return errors;
        }
        private static int maxArgumentLength(String key) {
            if ("query".equals(key)) return 300;
            if ("app_name".equals(key)) return 80;
            if ("title".equals(key)) return 160;
            if ("url".equals(key)) return 2048;
            if ("delay_minutes".equals(key)) return 5;
            if ("id".equals(key)) return 19;
            if ("package".equals(key)) return 255;
            return 1024;
        }
        private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    }

    private final Map<String, ToolSpec> tools = new LinkedHashMap<>();

    public synchronized void register(ToolSpec spec) {
        if (spec == null) throw new IllegalArgumentException("Tool is required");
        if (tools.containsKey(spec.name)) throw new IllegalArgumentException("Duplicate tool: " + spec.name);
        tools.put(spec.name, spec);
    }

    public synchronized ToolSpec get(String name) { return tools.get(name); }

    public synchronized List<ToolSpec> describeAllowedTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    public ToolResult invoke(String name, Map<String, String> arguments) {
        ToolSpec spec = get(name);
        if (spec == null) return ToolResult.failure("Tool is not registered: " + name);
        List<String> errors = spec.argumentErrors(arguments);
        if (!errors.isEmpty()) return ToolResult.failure(errors.toString());
        try {
            Map<String, String> safe = Collections.unmodifiableMap(new LinkedHashMap<>(
                arguments == null ? Collections.<String, String>emptyMap() : arguments));
            ToolResult result = spec.handler.execute(safe);
            return result == null ? ToolResult.failure("Tool returned no result") : result;
        } catch (SecurityException e) {
            return ToolResult.failure("Permission denied: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.failure("Tool failed: " + e.getClass().getSimpleName());
        }
    }

    public static boolean requiresApproval(Risk risk) {
        return risk == Risk.SENSITIVE || risk == Risk.EXTERNAL;
    }
}
