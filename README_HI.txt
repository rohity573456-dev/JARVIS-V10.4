JARVIS ULTIMATE V10.3-DEV — SINGLE WAKE OWNER PREPARATION SOURCE

Is ZIP mein Android Studio/Gradle ka complete source project hai; ready-made APK nahi hai.

V10.1 source changes:
- Background WakeWordService ab har listening cycle par SpeechRecognizer ko cancel/destroy karke naya instance nahi banata. Ek recognizer instance ko successful/error callbacks ke baad reuse karta hai.
- Sirf ERROR_SERVER_DISCONNECTED ya unrecoverable start exception par recognizer recreate hota hai.
- ERROR_NO_MATCH / SPEECH_TIMEOUT par controlled retry; repeated misses ke baad Hindi (hi-IN) aur English (en-IN) recognition alternates.
- Wake phrase detection multiple recognition hypotheses mein hotword dhoondhta hai; "Jarvis", "Hey Jarvis", "जार्विस" aur "हे जार्विस" handle karta hai.
- Foreground service ka apna TextToSpeech engine hai. "Yes, Commander" acknowledgement ab Activity ke dynamic receiver par dependent nahi.
- Agar Activity receiver alive hai to command MainActivity ke existing command handler ko bheji jaati hai; Activity background mein ho aur process alive ho to voice response app TTS se aata hai.
- Agar Activity destroy ho chuki ho, service wake acknowledgement de sakti hai aur command sunne ke baad batati hai ki execution ke liye app kholna hoga. MainActivity ke existing full command handler ko alag background service mein migrate nahi kiya gaya.
- Wake acknowledgement ke dauran self-triggering kam karne ke liye next recognition session delay kiya gaya.
- Service TTS init se pehle aayi acknowledgement queue hoti hai.
- Service running state dashboard ko update karti hai.
- Version 10.1 / versionCode 19.

IMPORTANT ANDROID LIMITATIONS:
- Android SpeechRecognizer continuous always-on hotword API nahi hai. Is implementation mein completed speech sessions ke beech same recognizer object reuse hota hai; Android speech provider phir bhi sessions end kar sakta hai.
- Android 14/15 microphone foreground service ko aam taur par visible Activity se start karna padta hai. Isliye JARVIS ko pehli baar app kholkar mic permission allow karni hogi.
- Home button se app background karne par foreground service aur Activity receiver alive reh sakte hain; command processing device/OS par depend karegi.
- Recents se app swipe away, force-stop, OS process kill, microphone privacy toggle, Google Speech Services failure, ya aggressive HyperOS battery management par wake listening/command execution guaranteed nahi hai.
- Agar Activity destroy ho jaati hai, service wake phrase ka voice acknowledgement aur "open app to execute" fallback de sakti hai; app ke saare existing custom/AI commands ko Activity band hone par execute karna abhi supported nahi.
- True app-closed full command execution ke liye command engine, AI routing, app launching aur response TTS ko service-safe architecture mein migrate karna hoga. Isko is patch mein complete hone ka claim nahi kiya gaya.
- Repeated cloud speech recognition battery aur internet data use karega.

DEVICE SETUP FOR POCO F5 / ANDROID 15:
1. App Info > Permissions > Microphone: Allow.
2. App Info > Notifications: Allow (ongoing service notification visibility).
3. Battery usage: Unrestricted / Don't optimize, agar HyperOS option de.
4. Autostart: Allow, agar device settings mein option available ho.
5. Google Speech Services installed/enabled aur updated hon.
6. App ko foreground mein kholkar wake notification active hone ka wait karein; phir Home dabakar "Hey Jarvis" test karein.

BUILD:
- JDK 17, Android SDK Platform 35, configured Gradle workflow.
- GitHub Actions workflow: .github/workflows/build-apk.yml
- Command: gradle assembleDebug
- Output: app/build/outputs/apk/debug/app-debug.apk

VERIFICATION STATUS:
- AndroidManifest XML parsing and Java delimiter/static source checks performed after patch.
- Is environment mein Android SDK/android.jar aur Gradle executable available nahi the; APK compilation, dependency resolution, and POCO F5 Android 15 microphone tests are NOT verified.
- Isliye V10.1 ko tested/bug-free APK nahi kaha ja sakta. Actual device testing is still required.


V10.2 MICROPHONE INDICATOR TUNING:
- Idle speech silence window 8 seconds / possible silence 6 seconds; no-match retry gap 1.2 seconds.
- Isse mic privacy indicator ka frequent blinking kam ho sakta hai, lekin SpeechRecognizer session-based hone ke karan blinking poori tarah khatam hone ki guarantee nahi.
- True continuous mic ke liye AudioRecord + local wake-word model architecture chahiye.
- Android build aur POCO F5 test abhi pending hai.


V10.3-DEV NOTE:
- MainActivity ka second wake-only SpeechRecognizer path disable kiya gaya hai; WakeWordService single owner hai.
- Yeh abhi AudioRecord + Sherpa-ONNX offline KWS final integration nahi hai. Mic indicator blink ka permanent fix is source mein abhi complete nahi.
- Details: V10_3_DEVELOPMENT_STATUS_HI.txt


V10.4: Continuous AudioRecord standby + offline Sherpa-ONNX keyword spotting integration is prepared in source. GitHub Actions downloads pinned native runtime and English KWS model before building. See V10_4_CONTINUOUS_KWS_NOTES_HI.txt.
