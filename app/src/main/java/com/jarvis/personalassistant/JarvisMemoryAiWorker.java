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

    public static void learnAsync(JarvisMemoryStore db,String gKey,String gModel,String rKey,String rModel,String text){
        if(db==null||text==null||text.trim().length()<8||text.length()>2500||sensitive(text))return;
        if(empty(gKey)&&empty(rKey))return;
        EXECUTOR.execute(()->storeFacts(db,parseArray(ask(gKey,gModel,rKey,rModel,extractPrompt(text))),"ai-conversation"));
    }

    public static void reflectPendingAsync(JarvisMemoryStore db,String gKey,String gModel,String rKey,String rModel){
        if(db==null||(empty(gKey)&&empty(rKey)))return;
        EXECUTOR.execute(()->{
            List<JarvisMemoryStore.PendingSession> sessions=db.getPendingReflectionSessions(6);
            for(JarvisMemoryStore.PendingSession session:sessions){
                String prompt="Review this completed conversation session. Return JSON only: "+
                    "{\"summary\":\"short factual summary\",\"facts\":[{\"category\":\"profile|preference|goal|project|general\","+
                    "\"type\":\"core|semantic|procedural|episodic\",\"key\":\"stable.key\",\"content\":\"durable fact\","+
                    "\"subject\":\"entity\",\"relation\":\"relation\",\"object\":\"value\",\"confidence\":0.8,\"importance\":60,\"pinned\":false}]} "+
                    "Only include user-stated facts; do not trust assistant guesses. Never infer credentials, IDs, health, religion, politics, sexual or criminal information. "+
                    "Summary under 600 chars. Session:\n"+session.transcript;
                JSONObject root=parseObject(ask(gKey,gModel,rKey,rModel,prompt));
                if(root==null)continue;
                String summary=root.optString("summary","").trim();
                if(!summary.isEmpty())db.saveAiSessionReflection(session.sessionId,summary);
                storeFacts(db,root.optJSONArray("facts"),"ai-reflection");
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
    private static String ask(String gKey,String gModel,String rKey,String rModel,String prompt){
        String result="ERROR: no provider";
        if(!empty(gKey))result=OnlineAiClient.ask("https://generativelanguage.googleapis.com/v1beta/openai",gKey,
            empty(gModel)?"gemini-2.5-flash":gModel,prompt);
        if(result.startsWith("ERROR")&&!empty(rKey))result=OnlineAiClient.ask("https://api.groq.com/openai/v1",rKey,
            empty(rModel)?"openai/gpt-oss-120b":rModel,prompt);
        return result;
    }
    private static void storeFacts(JarvisMemoryStore db,JSONArray facts,String source){
        if(db==null||facts==null)return;
        for(int i=0;i<Math.min(20,facts.length());i++){
            JSONObject f=facts.optJSONObject(i);if(f==null)continue;
            String content=f.optString("content","").trim(),key=f.optString("key","").trim();
            if(content.length()<4||content.length()>500||key.isEmpty()||sensitive(content))continue;
            String type=f.optString("type","semantic");
            if(!("core".equals(type)||"semantic".equals(type)||"procedural".equals(type)||"episodic".equals(type)))type="semantic";
            long validFrom=f.optLong("valid_from",0),validUntil=f.optLong("valid_until",0);
            long id=db.upsertFact(f.optString("category","general"),type,key,content,
                f.optDouble("confidence",0.70),f.optInt("importance",55),f.optBoolean("pinned",false),source);
            if(id>0&&(validFrom>0||validUntil>0))db.setMemoryValidity(id,validFrom,validUntil);
            String object=f.optString("object","").trim();
            if(id>0&&!object.isEmpty())db.addGraphRelation(id,f.optString("subject","Commander"),
                f.optString("relation","related_to"),object,f.optDouble("confidence",0.70),
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
    private static boolean sensitive(String value){
        String q=value.toLowerCase(java.util.Locale.ROOT);
        String[] bad={"password","passcode","otp","cvv","aadhaar","aadhar","pan number","bank account","credit card",
            "debit card","diagnosis","medicine","medical condition","disease","religion","political party","sexual"};
        for(String b:bad)if(q.contains(b))return true;return false;
    }
}