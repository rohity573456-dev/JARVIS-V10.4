package com.jarvis.personalassistant;

/** Shared defense-in-depth filter for sensitive text before it enters persistent memory. */
public final class JarvisSensitiveDataFilter {
    private JarvisSensitiveDataFilter() {}

    public static boolean isSensitiveText(String value) {
        if(value==null||value.isEmpty())return false;
                String q=value.toLowerCase(java.util.Locale.ROOT);
                String[] bad={"password","passcode","pass phrase","otp","one time password","cvv","cvc",
                    "aadhaar","aadhar","pan number","pan card number","passport number","driving licence number",
                    "social security number","bank account","account number","ifsc","credit card","debit card",
                    "diagnosis","medical condition","medical history","prescription","medication","medicine",
                    "diabetes","cancer","hiv","blood pressure","mental health","depression","anxiety disorder",
                    "religion","religious belief","political party","political belief","sexual orientation",
                    "sex life","sexual activity","criminal record","criminal history","fingerprint","face id",
                    "पासवर्ड","ओटीपी","आधार","आधार कार्ड","पैन कार्ड","बैंक खाता","खाता संख्या",
                    "क्रेडिट कार्ड","डेबिट कार्ड","दवा","बीमारी","निदान","धर्म","राजनीतिक विचार",
                    "यौन अभिरुचि","फिंगरप्रिंट"};
                for(String b:bad)if(q.contains(b))return true;
                if(java.util.regex.Pattern.compile("\\b[0-9]{12}\\b").matcher(q).find())return true;
                if(java.util.regex.Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value).find())return true;
                if(java.util.regex.Pattern.compile("\\b[0-9]{4}\\s?[0-9]{4}\\s?[0-9]{4}\\b").matcher(q).find())return true;
                return false;
    }
}