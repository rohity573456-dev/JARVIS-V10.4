package com.jarvis.personalassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Encrypts provider credentials at rest using an Android Keystore AES-GCM key. */
public final class SecureStore {
    private static final String PREFS = "jarvis_secure";
    private static final String ALIAS = "jarvis_api_keys_v1";
    private final SharedPreferences prefs;
    public SecureStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return gen.generateKey();
    }
    public void put(String name, String value) throws Exception {
        if (value == null || value.isEmpty()) { prefs.edit().remove(name).apply(); return; }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.getBytes("UTF-8"));
        ByteBuffer data = ByteBuffer.allocate(4 + cipher.getIV().length + encrypted.length);
        data.putInt(cipher.getIV().length).put(cipher.getIV()).put(encrypted);
        prefs.edit().putString(name, Base64.encodeToString(data.array(), Base64.NO_WRAP)).apply();
    }
    public String get(String name) {
        try {
            String encoded = prefs.getString(name, null); if (encoded == null) return "";
            ByteBuffer data = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP)); int ivLength = data.getInt();
            if (ivLength < 12 || ivLength > 16 || ivLength > data.remaining()) return "";
            byte[] iv = new byte[ivLength]; data.get(iv); byte[] encrypted = new byte[data.remaining()]; data.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), new javax.crypto.spec.GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), "UTF-8");
        } catch (Exception e) { return ""; }
    }
}
