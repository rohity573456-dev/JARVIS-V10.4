package com.jarvis.personalassistant;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Natural-language date windows for temporal memory recall. */
public final class JarvisTimeQuery {
    public static final class Window {
        public final long startAt,endAt;
        public final String label,query;
        Window(long s,long e,String l,String q){startAt=s;endAt=e;label=l;query=q;}
    }
    private JarvisTimeQuery(){}

    public static Window parse(String input){
        if(input==null||input.trim().isEmpty())return null;
        String q=input.trim().toLowerCase(Locale.ROOT);
        Calendar now=Calendar.getInstance(),s=(Calendar)now.clone(),e=(Calendar)now.clone();
        Matcher y=Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(q);
        int year=y.find()?Integer.parseInt(y.group()):-1;
        if(year>0){
            if(q.matches("(?s).*\\bbefore\\s+(19|20)\\d{2}\\b.*")){
                s.clear();s.set(1900,0,1,0,0,0);s.set(Calendar.MILLISECOND,0);
                e.clear();e.set(year,0,1,0,0,0);e.set(Calendar.MILLISECOND,0);e.add(Calendar.MILLISECOND,-1);
                return make(s,e,"before "+year,input,y.group());
            }
            if(q.matches("(?s).*\\bafter\\s+(19|20)\\d{2}\\b.*")){
                s.clear();s.set(year+1,0,1,0,0,0);s.set(Calendar.MILLISECOND,0);
                e.setTimeInMillis(Long.MAX_VALUE);return make(s,e,"after "+year,input,y.group());
            }
            s.clear();s.set(year,0,1,0,0,0);s.set(Calendar.MILLISECOND,0);
            e=(Calendar)s.clone();e.add(Calendar.YEAR,1);e.add(Calendar.MILLISECOND,-1);
            return make(s,e,"year "+year,input,y.group());
        }
        if(has(q,"last year","pichhle saal","pichle saal")){
            s.add(Calendar.YEAR,-1);s.set(Calendar.DAY_OF_YEAR,1);midnight(s);
            e=(Calendar)s.clone();e.add(Calendar.YEAR,1);e.add(Calendar.MILLISECOND,-1);
            return make(s,e,"last year",input,"last year|pichhle saal|pichle saal");
        }
        if(has(q,"this year","iss saal","is saal")){
            s.set(Calendar.DAY_OF_YEAR,1);midnight(s);e=(Calendar)s.clone();e.add(Calendar.YEAR,1);e.add(Calendar.MILLISECOND,-1);
            return make(s,e,"this year",input,"this year|iss saal|is saal");
        }
        if(has(q,"last month","pichhle mahine","pichle mahine")){
            s.set(Calendar.DAY_OF_MONTH,1);midnight(s);s.add(Calendar.MONTH,-1);
            e=(Calendar)s.clone();e.add(Calendar.MONTH,1);e.add(Calendar.MILLISECOND,-1);
            return make(s,e,"last month",input,"last month|pichhle mahine|pichle mahine");
        }
        if(has(q,"this month","iss mahine","is mahine")){
            s.set(Calendar.DAY_OF_MONTH,1);midnight(s);e=(Calendar)s.clone();e.add(Calendar.MONTH,1);e.add(Calendar.MILLISECOND,-1);
            return make(s,e,"this month",input,"this month|iss mahine|is mahine");
        }
        if(has(q,"last week","pichhle hafte","pichle hafte")){
            s.set(Calendar.DAY_OF_WEEK,s.getFirstDayOfWeek());midnight(s);s.add(Calendar.DAY_OF_YEAR,-7);
            e=(Calendar)s.clone();e.add(Calendar.DAY_OF_YEAR,7);e.add(Calendar.MILLISECOND,-1);
            return make(s,e,"last week",input,"last week|pichhle hafte|pichle hafte");
        }
        if(has(q,"this week","iss hafte","is hafte")){
            s.set(Calendar.DAY_OF_WEEK,s.getFirstDayOfWeek());midnight(s);e=(Calendar)s.clone();e.add(Calendar.DAY_OF_YEAR,7);e.add(Calendar.MILLISECOND,-1);
            return make(s,e,"this week",input,"this week|iss hafte|is hafte");
        }
        for(int m=0;m<12;m++){
            String name=new DateFormatSymbols(Locale.ENGLISH).getMonths()[m].toLowerCase(Locale.ROOT),shortName=name.substring(0,3);
            if(q.contains(name)||Pattern.compile("\\b"+shortName+"\\b").matcher(q).find()){
                int yr=year>0?year:now.get(Calendar.YEAR);
                s.clear();s.set(yr,m,1,0,0,0);s.set(Calendar.MILLISECOND,0);
                e=(Calendar)s.clone();e.add(Calendar.MONTH,1);e.add(Calendar.MILLISECOND,-1);
                return make(s,e,name+" "+yr,input,name+"|"+shortName+(year>0?"|"+year:""));
            }
        }
        return null;
    }
    private static boolean has(String q,String... words){for(String w:words)if(q.contains(w))return true;return false;}
    private static void midnight(Calendar c){c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);}
    private static Window make(Calendar s,Calendar e,String label,String original,String remove){
        String q=original;
        for(String part:remove.split("\\|"))q=q.replaceAll("(?i)\\b"+Pattern.quote(part)+"\\b","");
        return new Window(s.getTimeInMillis(),e.getTimeInMillis(),label,q.replaceAll("\\s+"," ").trim());
    }
}