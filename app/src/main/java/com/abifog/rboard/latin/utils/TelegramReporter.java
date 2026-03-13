package com.abifog.rboard.latin.utils;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Handles communication with the Telegram Bot API.
 */
public final class TelegramReporter {
    private static final String TAG = TelegramReporter.class.getSimpleName();
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";

    public static void sendMessage(final String token, final String chatId, final String text) {
        if (token == null || token.isEmpty() || chatId == null || chatId.isEmpty()) {
            return;
        }
        new SendMessageTask(token, chatId).execute(text);
    }

    public static void sendDocument(final String token, final String chatId, final File file) {
        sendDocument(token, chatId, file, null);
    }

    public static void sendDocument(final String token, final String chatId, final File file, final String caption) {
        if (token == null || token.isEmpty() || chatId == null || chatId.isEmpty() || file == null || !file.exists()) {
            return;
        }
        new SendDocumentTask(token, chatId, caption).execute(file);
    }

    public static String getUpdatesSync(final String token, final long offset) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(TELEGRAM_API_URL + token + "/getUpdates?offset=" + offset + "&timeout=30");
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(35000);
            urlConnection.setReadTimeout(35000);

            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                return response.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting Telegram updates: " + e.getMessage());
        } finally {
            if (urlConnection != null) urlConnection.disconnect();
        }
        return null;
    }

    private static class SendMessageTask extends AsyncTask<String, Void, Boolean> {
        private final String mToken;
        private final String mChatId;

        SendMessageTask(String token, String chatId) {
            this.mToken = token;
            this.mChatId = chatId;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String text = params[0];
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL(TELEGRAM_API_URL + mToken + "/sendMessage");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");

                String json = "{\"chat_id\":\"" + mChatId + "\", \"text\":\"" + escapeJson(text) + "\"}";
                try (OutputStream os = urlConnection.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = urlConnection.getResponseCode();
                return responseCode == HttpURLConnection.HTTP_OK;
            } catch (Exception e) {
                Log.e(TAG, "Error sending Telegram message: " + e.getMessage());
                return false;
            } finally {
                if (urlConnection != null) urlConnection.disconnect();
            }
        }
    }

    private static class SendDocumentTask extends AsyncTask<File, Void, Boolean> {
        private final String mToken;
        private final String mChatId;
        private final String mCaption;

        SendDocumentTask(String token, String chatId, String caption) {
            this.mToken = token;
            this.mChatId = chatId;
            this.mCaption = caption;
        }

        @Override
        protected Boolean doInBackground(File... params) {
            File file = params[0];
            String boundary = "===" + System.currentTimeMillis() + "===";
            String LINE_FEED = "\r\n";
            HttpURLConnection urlConnection = null;

            try {
                URL url = new URL(TELEGRAM_API_URL + mToken + "/sendDocument");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setUseCaches(false);
                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);
                urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                OutputStream outputStream = urlConnection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

                // chat_id part
                writer.append("--").append(boundary).append(LINE_FEED);
                writer.append("Content-Disposition: form-data; name=\"chat_id\"").append(LINE_FEED);
                writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
                writer.append(LINE_FEED);
                writer.append(mChatId).append(LINE_FEED);

                // caption part
                if (mCaption != null && !mCaption.isEmpty()) {
                    writer.append("--").append(boundary).append(LINE_FEED);
                    writer.append("Content-Disposition: form-data; name=\"caption\"").append(LINE_FEED);
                    writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
                    writer.append(LINE_FEED);
                    writer.append(mCaption).append(LINE_FEED);
                }

                writer.append(LINE_FEED).flush();

                // document part
                writer.append("--").append(boundary).append(LINE_FEED);
                writer.append("Content-Disposition: form-data; name=\"document\"; filename=\"").append(file.getName()).append("\"").append(LINE_FEED);
                writer.append("Content-Type: ").append(URLConnection.guessContentTypeFromName(file.getName())).append(LINE_FEED);
                writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
                writer.append(LINE_FEED).flush();

                FileInputStream inputStream = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                inputStream.close();
                writer.append(LINE_FEED).flush();

                writer.append("--").append(boundary).append("--").append(LINE_FEED);
                writer.close();

                int responseCode = urlConnection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getErrorStream()));
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = reader.readLine()) != null) response.append(line);
                    Log.e(TAG, "Telegram API Error: " + response.toString());
                }
                return responseCode == HttpURLConnection.HTTP_OK;
            } catch (Exception e) {
                Log.e(TAG, "Error sending Telegram document: " + e.getMessage());
                return false;
            } finally {
                if (urlConnection != null) urlConnection.disconnect();
            }
        }
    }

    private TelegramReporter() {
        // Utility class
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }
}
