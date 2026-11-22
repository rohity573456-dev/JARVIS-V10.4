package com.jarvis.personalassistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Converts a user objective into typed OpenAI-compatible function/tool calls.
 * Tool names and argument schemas are sourced only from the local allow-list.
 */
public final class JarvisStructuredPlanner implements JarvisAgentEngine.Planner {
    private static final String GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1";

    private final JarvisToolRegistry registry;
    private final String geminiKey, geminiModel, groqKey, groqModel;

    public JarvisStructuredPlanner(JarvisToolRegistry registry, String geminiKey, String geminiModel,
            String groqKey, String groqModel) {
        if (registry == null) throw new IllegalArgumentException("Tool registry required");
        this.registry = registry;
        this.geminiKey = clean(geminiKey);
        this.geminiModel = clean(geminiModel).isEmpty() ? "gemini-2.5-flash" : geminiModel;
        this.groqKey = clean(groqKey);
        this.groqModel = clean(groqModel).isEmpty() ? "openai/gpt-oss-120b" : groqModel;
    }

    @Override public JarvisAgentEngine.AgentPlan createPlan(String objective) throws Exception {
        if (clean(objective).isEmpty()) throw new IllegalArgumentException("Objective is empty");
        JSONArray schemas = buildToolSchemas();
        if (schemas.length() == 0) throw new IllegalStateException("No Android tools are registered");
        String system = "You are JARVIS action planner. Use native function calls, never write executable code. " +
            "Call only a tool that directly helps fulfil the user's explicit request. " +
            "If the request is a question or cannot be completed by the available tools, do not call any tool. " +
            "Never guess package names. For open_app, use a package name only when the user clearly identified an installed app and you know its exact Android package; otherwise do not call it. " +
            "For web_search, search only when the user explicitly asks to search the web. " +
            "Never chain unrelated actions. Maximum one action per response. Tool calls are requests, not proof of success.";
        String prompt = "User request: " + objective + "\nReturn a native tool call only if an allow-listed action is clearly requested.";
        String result = "ERROR: No AI provider configured.";
        if (!geminiKey.isEmpty())
            result = OnlineAiClient.askForToolCalls(GEMINI_ENDPOINT, geminiKey, geminiModel, prompt, system, schemas);
        if (isError(result) && !groqKey.isEmpty())
            result = OnlineAiClient.askForToolCalls(GROQ_ENDPOINT, groqKey, groqModel, prompt, system, schemas);
        if (isError(result)) throw new IllegalStateException(result);
        JSONArray calls = new JSONArray(result);
        List<JarvisAgentEngine.AgentStep> steps = new ArrayList<>();
        for (int i = 0; i < calls.length(); i++) {
            JSONObject call = calls.getJSONObject(i);
            String name = call.optString("name", "");
            JarvisToolRegistry.ToolSpec spec = registry.get(name);
            if (spec == null) throw new SecurityException("Model requested an unregistered tool: " + name);
            JSONObject jsonArgs = call.optJSONObject("arguments");
            if (jsonArgs == null) throw new IllegalArgumentException("Tool arguments must be a JSON object");
            Map<String, String> args = new HashMap<>();
            Iterator<String> keys = jsonArgs.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsonArgs.opt(key);
                if (!(value instanceof String)) throw new IllegalArgumentException("Only string arguments are accepted: " + key);
                args.put(key, (String) value);
            }
            List<String> errors = spec.argumentErrors(args);
            if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid " + name + " arguments: " + errors);
            steps.add(new JarvisAgentEngine.AgentStep("step-" + (i + 1), name,
                args, "Requested by user: " + objective));
            if (steps.size() >= JarvisAgentEngine.MAX_STEPS) break;
        }
        return new JarvisAgentEngine.AgentPlan(objective, steps);
    }

    private JSONArray buildToolSchemas() throws Exception {
        JSONArray tools = new JSONArray();
        for (JarvisToolRegistry.ToolSpec spec : registry.describeAllowedTools()) {
            JSONObject properties = new JSONObject();
            for (String key : spec.requiredArguments) properties.put(key, argumentSchema(key));
            for (String key : spec.optionalArguments) properties.put(key, argumentSchema(key));
            JSONArray required = new JSONArray();
            for (String key : spec.requiredArguments) required.put(key);
            JSONObject parameters = new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", required)
                .put("additionalProperties", false);
            JSONObject function = new JSONObject()
                .put("name", spec.name)
                .put("description", spec.description)
                .put("parameters", parameters);
            tools.put(new JSONObject().put("type", "function").put("function", function));
        }
        return tools;
    }

    private JSONObject argumentSchema(String key) throws Exception {
        JSONObject schema = new JSONObject().put("type", "string");
        if ("panel".equals(key)) schema.put("enum", new JSONArray().put("wifi").put("bluetooth").put("display").put("sound").put("general"));
        else if ("direction".equals(key)) schema.put("enum", new JSONArray().put("up").put("down").put("mute"));
        else if ("query".equals(key)) schema.put("minLength", 1).put("maxLength", 300);
        else if ("package".equals(key)) schema.put("pattern", "^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$");
        return schema;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static boolean isError(String value) { return value == null || value.trim().isEmpty() || value.startsWith("ERROR"); }
}
