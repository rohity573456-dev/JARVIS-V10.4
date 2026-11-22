package com.jarvis.personalassistant;

import java.util.Set;

/**
 * Deterministic offline feature-hash vectors. This is not a neural model;
 * it supplies local vector similarity until a true on-device encoder is installed.
 */
public final class JarvisLocalVectorEngine {
    public static final String MODEL="local-feature-hash-v1";
    public static final int DIMENSIONS=384;
    private JarvisLocalVectorEngine(){}
    public static float[] embed(String text){
        float[] v=new float[DIMENSIONS];Set<String> terms=JarvisSemanticEngine.expand(text);
        for(String t:terms){
            add(v,t,1.0f);
            if(t.length()>=4)for(int i=0;i<=t.length()-3;i++)add(v,"~"+t.substring(i,i+3),0.22f);
        }
        double n=0;for(float f:v)n+=f*f;
        if(n>0){float scale=(float)(1.0/Math.sqrt(n));for(int i=0;i<v.length;i++)v[i]*=scale;}
        return v;
    }
    private static void add(float[] v,String token,float weight){
        int h=token.hashCode(),i=(h&0x7fffffff)%DIMENSIONS;
        v[i]+=((h&0x80000000)==0?1.0f:-1.0f)*weight;
    }
    public static double similarity(float[] a,float[] b){return JarvisEmbeddingClient.cosineSimilarity(a,b);}
}