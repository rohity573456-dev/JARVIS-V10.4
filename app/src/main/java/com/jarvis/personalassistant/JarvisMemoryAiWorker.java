package com.jarvis.personalassistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Provider-backed extraction/reflection; called only after explicit cloud-memory consent. */
public final class JarvisMemoryAiWorker {
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private JarvisMemoryAiWorker(){}

    public interface ConsentGate { boolean isAllowed(); }
    private static final ConsentGate DENY_BY_DEFAULT = () -> false;

    /** Legacy callers fail closed; production callers must supply a live consent gate. */
    public static void learnAsync(JarvisMemoryStore db,String gKey,String gModel,String rKey,String rModel,String openAiKey,String text){
        learnAsync(db,gKey,gModel,rKey,rModel,openAiKey,text,DENY_BY_DEFAULT);
    }

    public static void learnAsync(JarvisMemoryStore db,String gKey,String gModel,String rKey,String rModel,String openAiKey,String text,ConsentGate consent){
        if(db==null||text==null||text.trim().length()<8||text.length()>2500||sensitive(text)||
                consent==null||!consent.isAllowed())return;
        if(empty(gKey)&&empty(rKey)&&empty(openAiKey))return;
        EXECUTOR.execute(()->{
            if(!consent.isAllowed())return;
            String result=ask(gKey,gModel,rKey,rModel,openAiKey,extractPrompt(text));
            if(!consent.isAllowed())return;
            storeFacts(db,parseArray(result),"ai-conversation",consent);
        });
    }

    /** Legacy callers fail closed; production callers must supply a live consent gate. */
    public static void reflectPendingAsync(JarvisMemoryStore db,String gKey,String gModel,String rKey,String rModel,String openAiKey){
        reflectPendingAsync(db,gKey,gModel,rKey,rModel,openAiKey,DENY_BY_DEFAULT);
    }

    public static void reflectPendingAsync(JarvisMemoryStore db,String gKey,String gModel,String rKey,String rModel,String openAiKey,ConsentGate consent){
        if(db==null||(empty(gKey)&&empty(rKey)&&empty(openAiKey))||consent==null||!consent.isAllowed())return;
        EXECUTOR.execute(()->{
            if(!consent.isAllowed())return;
            List<JarvisMemoryStore.PendingSession> sessions=db.getPendingReflectionSessions(6);
            for(JarvisMemoryStore.PendingSession session:sessions){
                if(!consent.isAllowed())return;
                StringBuilder promptBuilder=new StringBuilder();
                promptBuilder.append("Review this completed conversation session. Return JSON only: ");
                promptBuilder.append("{\"summary\":\"short factual summary\",\"facts\":[{\"category\":\"profile|preference|goal|project|general\",");
                promptBuilder.append("\"type\":\"core|semantic|procedural|episodic\",\"key\":\"stable.key\",\"content\":\"durable fact\",");
                promptBuilder.append("\"subject\":\"entity\",\"relation\":\"relation\",\"object\":\"value\",\"confidence\":0.8,\"importance\":60,\"pinned\":false}]} ");
                promptBuilder.append("Only include user-stated facts; do not trust assistant guesses. Never infer credentials, IDs, health, religion, politics, sexual or criminal information. ");
                promptBuilder.append("Summary under 600 chars. Session:\\n").append(session.transcript);
                String prompt=promptBuilder.toString();
                JSONObject root=parseObject(ask(gKey,gModel,rKey,rModel,openAiKey,prompt));
                if(!consent.isAllowed())return;
                if(root==null)continue;
                String summary=root.optString("summary","").trim();
                if(!summary.isEmpty() && summary.length()<=600 && !sensitive(summary) && consent.isAllowed())
                    db.saveAiSessionReflection(session.sessionId,summary);
                if(!consent.isAllowed())return;
                storeFacts(db,root.optJSONArray("facts"),"ai-reflection",consent);
            }
        });
    }

    private static String extractPrompt(String text){
        return "Extract only durable memories explicitly stated by the user in one message. Return JSON array only. "+
            "Each object: category,type,key,content,subject,relation,object,confidence,importance,pinned. "+
            "Use stable keys like profile.name, profile.location, preference.food, project.current, goal.primary, instruction.response_style. "+
            "Use Commander as subject for the user's own fact. Never invent, infer from questions, or save assistant statements. "+
            "Never extract credentials, OTPs, government IDs, financial identifiers, health, religion, political beliefs, sexual or criminal information. "+
            "Confidence 0-1, importance 0-100, pinned only for durable identity and explicit standing instructions. If no fact qualifies return []. User: "+text;
    }
    private static String ask(String gKey,String gModel,String rKey,String rModel,String openAiKey,String prompt){
        String result="ERROR: no provider";
        if(!empty(openAiKey))result=OpenAiResponsesClient.ask(openAiKey,OpenAiResponsesClient.DEFAULT_MODEL,
            "You are JARVIS memory curator. Extract only explicit, durable user facts. Return valid JSON only. Never infer sensitive information.",prompt,"medium");
        if(result.startsWith("ERROR")&&!empty(gKey))result=OnlineAiClient.ask("https://generativelanguage.googleapis.com/v1beta/openai",gKey,
            empty(gModel)?"gemini-2.5-flash":gModel,prompt);
        if(result.startsWith("ERROR")&&!empty(rKey))result=OnlineAiClient.ask("https://api.groq.com/openai/v1",rKey,
            empty(rModel)?"openai/gpt-oss-120b":rModel,prompt);
        return result;
    }
    private static void storeFacts(JarvisMemoryStore db,JSONArray facts,String source,ConsentGate consent){
        if(db==null||facts==null||consent==null||!consent.isAllowed())return;
        for(int i=0;i<Math.min(20,facts.length());i++){
            if(!consent.isAllowed())return;
            JSONObject f=facts.optJSONObject(i);if(f==null)continue;
            String content=f.optString("content","").trim(),key=f.optString("key","").trim();
            String category=f.optString("category","general").trim().toLowerCase(java.util.Locale.ROOT);
            if(content.length()<4||content.length()>500||key.isEmpty()||key.length()>96||
                    category.length()>40||isSensitiveCategory(category)||sensitive(content)||sensitive(key))continue;
            String type=f.optString("type","semantic");
            if(!("core".equals(type)||"semantic".equals(type)||"procedural".equals(type)||"episodic".equals(type)))type="semantic";
            long validFrom=f.optLong("valid_from",0),validUntil=f.optLong("valid_until",0);
            double confidence=Math.max(0.0,Math.min(1.0,f.optDouble("confidence",0.70)));
            int importance=Math.max(0,Math.min(100,f.optInt("importance",55)));
            boolean pinned=f.optBoolean("pinned",false)&&isSafePinnedKey(key,category);
            if(!consent.isAllowed())return;
            long id=db.upsertFact(category,type,key,content,confidence,importance,pinned,source);
            if(id>0&&(validFrom>0||validUntil>0))db.setMemoryValidity(id,validFrom,validUntil);
            String object=f.optString("object","").trim();
            String subject=f.optString("subject","Commander").trim();
            String relation=f.optString("relation","related_to").trim();
            if(consent.isAllowed()&&id>0&&!object.isEmpty()&&object.length()<=240&&subject.length()<=120&&
                    relation.length()<=80&&!sensitive(object)&&!sensitive(subject)&&!sensitive(relation))
                db.addGraphRelation(id,subject,relation,object,confidence,
                    f.optLong("valid_from",0),f.optLong("valid_until",0));
        }
    }
    private static JSONArray parseArray(String raw){
        String s=strip(raw);if(s.isEmpty()||s.startsWith("ERROR"))return null;
        int a=s.indexOf('['),b=s.lastIndexOf(']');if(a<0||b<a)return null;
        try{return new JSONArray(s.substring(a,b+1));}catch(Exception e){return null;}
    }
    private static JSONObject parseObject(String raw){
        String s=strip(raw);if(s.isEmpty()||s.startsWith("ERROR"))return null;
        int a=s.indexOf('{'),b=s.lastIndexOf('}');if(a<0||b<a)return null;
        try{return new JSONObject(s.substring(a,b+1));}catch(Exception e){return null;}
    }
    private static String strip(String raw){
        if(raw==null)return "";String s=raw.trim();
        if(s.startsWith("```")){int n=s.indexOf('\n');if(n>=0)s=s.substring(n+1);int end=s.lastIndexOf("```");if(end>=0)s=s.substring(0,end);}
        return s.trim();
    }
    private static boolean empty(String s){return s==null||s.trim().isEmpty();}
    static boolean isSensitiveCategory(String category){
        if(category==null)return true;
        String q=category.trim().toLowerCase(java.util.Locale.ROOT);
        return q.matches(".*(health|medical|diagnos|relig|politic|sexual|sex_life|criminal|credential|password|identity_document|government_id|bank|financial_identifier|biometric).*");
    }
    static boolean isSafePinnedKey(String key,String category){
        if(key==null||category==null||isSensitiveCategory(category))return false;
        String k=key.trim().toLowerCase(java.util.Locale.ROOT);
        return k.equals("profile.name")||k.startsWith("instruction.")||
            k.startsWith("preference.")||k.equals("project.current")||
            k.equals("goal.primary");
    }
    static boolean isSensitiveText(String value){return sensitive(value);}
    private static boolean sensitive(String value) {
        return JarvisSensitiveDataFilter.isSensitiveText(value);
    }}