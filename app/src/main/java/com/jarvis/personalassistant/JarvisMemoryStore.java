package com.jarvis.personalassistant;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline-first private memory, conversation, task and knowledge-graph store. */
public final class JarvisMemoryStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "jarvis_long_term_memory.db";
    private static final int DB_VERSION = 3;
    private static final String MEMORIES = "memories";
    private static final String CONVERSATIONS = "conversations";
    private static final String TASKS = "tasks";
    private static final String NODES = "knowledge_nodes";
    private static final String EDGES = "knowledge_edges";

    public JarvisMemoryStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        createMemories(db); createConversations(db); createTasks(db); createKnowledgeGraph(db);
    }
    private void createMemories(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS memories (id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_updated ON memories(updated_at DESC)");
    }
    private void createConversations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id INTEGER PRIMARY KEY AUTOINCREMENT, speaker TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_created ON conversations(created_at DESC)");
    }
    private void createTasks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due_at INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_created ON tasks(done, created_at DESC)");
    }
    private void createKnowledgeGraph(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_nodes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL COLLATE NOCASE UNIQUE, node_type TEXT NOT NULL DEFAULT 'concept')");
        db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_edges (id INTEGER PRIMARY KEY AUTOINCREMENT, source_id INTEGER NOT NULL, target_id INTEGER NOT NULL, relation TEXT NOT NULL, memory_id INTEGER NOT NULL, created_at INTEGER NOT NULL, UNIQUE(source_id,target_id,relation,memory_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_source ON knowledge_edges(source_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_target ON knowledge_edges(target_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_memory ON knowledge_edges(memory_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive-only migration. Existing memories, conversations and tasks are preserved.
        if (oldVersion < 2) createTasks(db);
        if (oldVersion < 3) {
            createKnowledgeGraph(db);
            Cursor c = db.rawQuery("SELECT id,content FROM memories", null);
            try { while (c.moveToNext()) indexMemoryGraph(db, c.getLong(0), c.getString(1)); }
            finally { c.close(); }
        }
    }

    public synchronized long saveMemory(String category, String content) {
        if (content == null || content.trim().isEmpty()) return -1;
        String clean = content.trim();
        long now = System.currentTimeMillis();
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM memories WHERE lower(content)=lower(?) LIMIT 1", new String[]{clean});
        try {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                ContentValues update = new ContentValues();
                update.put("category", category == null ? "general" : category);
                update.put("content", clean); update.put("updated_at", now);
                db.update(MEMORIES, update, "id=?", new String[]{String.valueOf(id)});
                db.delete(EDGES, "memory_id=?", new String[]{String.valueOf(id)});
                indexMemoryGraph(db, id, clean);
                return id;
            }
        } finally { cursor.close(); }
        ContentValues values = new ContentValues();
        values.put("category", category == null ? "general" : category);
        values.put("content", clean); values.put("created_at", now); values.put("updated_at", now);
        long id = db.insert(MEMORIES, null, values);
        if (id > 0) indexMemoryGraph(db, id, clean);
        return id;
    }

    public synchronized boolean updateMemory(long id, String category, String content) {
        if (id <= 0 || content == null || content.trim().isEmpty()) return false;
        ContentValues v = new ContentValues();
        v.put("category", category == null ? "general" : category);
        v.put("content", content.trim()); v.put("updated_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        boolean changed = db.update(MEMORIES, v, "id=?", new String[]{String.valueOf(id)}) > 0;
        if (changed) { db.delete(EDGES, "memory_id=?", new String[]{String.valueOf(id)}); indexMemoryGraph(db,id,content.trim()); }
        return changed;
    }
    public synchronized boolean deleteMemory(long id) {
        if (id <= 0) return false;
        SQLiteDatabase db=getWritableDatabase();
        db.delete(EDGES,"memory_id=?",new String[]{String.valueOf(id)});
        return db.delete(MEMORIES, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    private long node(SQLiteDatabase db, String name, String type) {
        String clean=name==null?"":name.trim();
        if(clean.isEmpty()) return -1;
        Cursor c=db.rawQuery("SELECT id FROM knowledge_nodes WHERE name=? COLLATE NOCASE LIMIT 1",new String[]{clean});
        try { if(c.moveToFirst()) return c.getLong(0); } finally { c.close(); }
        ContentValues v=new ContentValues();v.put("name",clean);v.put("node_type",type==null?"concept":type);
        return db.insertWithOnConflict(NODES,null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    private void addEdge(SQLiteDatabase db,long memoryId,String relation,String object) {
        if(object==null||object.trim().isEmpty())return;
        String value=object.trim().replaceAll("[.!?]+$","").trim();
        if(value.length()<2||value.length()>180)return;
        long user=node(db,"Commander","person"), target=node(db,value,"fact");
        if(user<=0||target<=0)return;
        ContentValues edge=new ContentValues();edge.put("source_id",user);edge.put("target_id",target);
        edge.put("relation",relation);edge.put("memory_id",memoryId);edge.put("created_at",System.currentTimeMillis());
        db.insertWithOnConflict(EDGES,null,edge,SQLiteDatabase.CONFLICT_IGNORE);
    }
    private void indexMemoryGraph(SQLiteDatabase db,long memoryId,String text) {
        if(text==null)return;
        String s=text.trim();
        String[][] patterns={
            {"(?i)^(?:my name is|mera naam)\\s+(.+)$","name"},
            {"(?i)^(?:i live in|i am from|main rehta hoon|main rehti hoon|mera ghar)\\s+(.+)$","lives_in"},
            {"(?i)^(?:i work as|my job is|main kaam karta hoon|main kaam karti hoon)\\s+(.+)$","works_as"},
            {"(?i)^(?:i like|i love|i prefer|mujhe pasand hai)\\s+(.+)$","prefers"},
            {"(?i)^(?:my favourite|my favorite|mera favourite|meri favourite)\\s+(.+)$","favourite"},
            {"(?i)^(?:my goal is|mera goal|my dream is)\\s+(.+)$","goal"}
        };
        for(String[] p:patterns) {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile(p[0]).matcher(s);
            if(m.matches()){addEdge(db,memoryId,p[1],m.group(1));return;}
        }
        // Preserve general facts as a searchable concept node even without a safely
        // identifiable subject-predicate-object structure.
        long user=node(db,"Commander","person"), fact=node(db,s,"fact");
        if(user>0&&fact>0) {
            ContentValues edge=new ContentValues();edge.put("source_id",user);edge.put("target_id",fact);
            edge.put("relation","remembers");edge.put("memory_id",memoryId);edge.put("created_at",System.currentTimeMillis());
            db.insertWithOnConflict(EDGES,null,edge,SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    public synchronized void addConversation(String speaker, String content) {
        if (content == null || content.trim().isEmpty()) return;
        ContentValues v = new ContentValues(); v.put("speaker", speaker == null ? "unknown" : speaker);
        v.put("content", content.trim()); v.put("created_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase(); db.insert("conversations", null, v);
        db.execSQL("DELETE FROM conversations WHERE id NOT IN (SELECT id FROM conversations ORDER BY created_at DESC LIMIT 2000)");
    }

    /** Hybrid lexical + concept-expanded semantic ranking, fully offline. */
    public synchronized String searchMemories(String query, int limit) {
        StringBuilder out=new StringBuilder();String q=query==null?"":query.trim();
        SQLiteDatabase db=getReadableDatabase();
        Cursor c=db.rawQuery("SELECT id,category,content,updated_at FROM memories ORDER BY updated_at DESC LIMIT 1000",null);
        List<MemoryHit> hits=new ArrayList<>();
        try {
            while(c.moveToNext()) {
                String category=c.getString(1),content=c.getString(2);
                int score=q.isEmpty()?1:JarvisSemanticEngine.score(q,content,category);
                if(score>0) hits.add(new MemoryHit(c.getLong(0),category,content,c.getLong(3),score));
            }
        } finally { c.close(); }
        java.util.Collections.sort(hits,(a,b)->{
            if(b.score!=a.score)return b.score-a.score;
            return Long.compare(b.updatedAt,a.updatedAt);
        });
        int count=0,max=Math.max(1,Math.min(limit,100));
        for(MemoryHit h:hits) {
            if(count++>=max)break;
            if(out.length()>0)out.append("\n");
            out.append("#").append(h.id).append(" [").append(h.category).append("] ").append(h.content);
        }
        return out.length()==0?"No matching saved memories.":out.toString();
    }
    private static final class MemoryHit {
        final long id,updatedAt;final String category,content;final int score;
        MemoryHit(long i,String c,String t,long u,int s){id=i;category=c;content=t;updatedAt=u;score=s;}
    }

    public synchronized String getKnowledgeGraphText(String query) {
        StringBuilder out=new StringBuilder();
        String q=query==null?"":query.trim();
        Cursor c;
        if(q.isEmpty()) c=getReadableDatabase().rawQuery(
            "SELECT e.relation,n.name FROM knowledge_edges e JOIN knowledge_nodes n ON n.id=e.target_id ORDER BY e.created_at DESC LIMIT 80",null);
        else c=getReadableDatabase().rawQuery(
            "SELECT e.relation,n.name FROM knowledge_edges e JOIN knowledge_nodes n ON n.id=e.target_id WHERE n.name LIKE ? OR e.relation LIKE ? ORDER BY e.created_at DESC LIMIT 80",
            new String[]{"%"+q+"%","%"+q+"%"});
        try { while(c.moveToNext()){if(out.length()>0)out.append("\n");out.append("Commander — ").append(c.getString(0)).append(" → ").append(c.getString(1));} }
        finally { c.close(); }
        return out.length()==0?"No linked knowledge found.":out.toString();
    }
    public synchronized String getRecentConversation(int limit) {
        StringBuilder out = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery("SELECT speaker,content FROM conversations ORDER BY created_at DESC LIMIT ?", new String[]{String.valueOf(Math.max(1, limit))});
        try { while (c.moveToNext()) { if (out.length() > 0) out.insert(0, "\n"); out.insert(0, c.getString(0) + ": " + c.getString(1)); } }
        finally { c.close(); }
        return out.toString();
    }
    public synchronized String getMemoryContext(String query, int limit) {
        String memories=searchMemories(query,limit);
        if(memories.startsWith("No matching"))memories=searchMemories("",Math.min(8,limit));
        String graph=getKnowledgeGraphText("");
        String recent=getRecentConversation(8);
        StringBuilder result=new StringBuilder("RELEVANT LONG-TERM MEMORIES (offline hybrid recall):\n").append(memories);
        if(!graph.startsWith("No linked"))result.append("\n\nKNOWLEDGE RELATIONSHIPS:\n").append(graph);
        if(!recent.isEmpty())result.append("\n\nRECENT CONVERSATION:\n").append(recent);
        return result.toString();
    }
    public synchronized String getAllMemoriesText(){return searchMemories("",200);}
    public synchronized void clearMemories(){SQLiteDatabase db=getWritableDatabase();db.delete("memories",null,null);db.delete(EDGES,null,null);db.delete(NODES,null,null);}
    public synchronized void clearConversationHistory(){getWritableDatabase().delete("conversations",null,null);}
    public synchronized int getConversationCount(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM conversations",null);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    public synchronized int getMemoryCount(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM memories",null);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}

    public synchronized long addTask(String title,long dueAt){
        if(title==null||title.trim().isEmpty())return -1;
        ContentValues v=new ContentValues();v.put("title",title.trim());v.put("due_at",Math.max(0,dueAt));v.put("done",0);v.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insert("tasks",null,v);
    }
    public synchronized String getTasksText(boolean includeDone){
        StringBuilder out=new StringBuilder();
        Cursor c=getReadableDatabase().rawQuery("SELECT id,title,due_at,done FROM tasks "+(includeDone?"":"WHERE done=0 ")+"ORDER BY done ASC, CASE WHEN due_at=0 THEN created_at ELSE due_at END ASC LIMIT 100",null);
        try{while(c.moveToNext()){if(out.length()>0)out.append("\n");out.append("#").append(c.getLong(0)).append(" ").append(c.getInt(3)==1?"[DONE] ":"[OPEN] ").append(c.getString(1));if(c.getLong(2)>0)out.append(" — ").append(new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.getDefault()).format(new java.util.Date(c.getLong(2))));}}
        finally{c.close();}
        return out.length()==0?"Abhi koi task/reminder saved nahi hai.":out.toString();
    }
    public synchronized boolean completeTask(long id){ContentValues v=new ContentValues();v.put("done",1);return getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)})>0;}
    public synchronized boolean deleteTask(long id){return id>0&&getWritableDatabase().delete("tasks","id=?",new String[]{String.valueOf(id)})>0;}
}
