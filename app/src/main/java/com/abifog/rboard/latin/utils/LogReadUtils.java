package com.abifog.rboard.latin.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for reading the captured log files.
 */
public final class LogReadUtils {
    private static final String TAG = LogReadUtils.class.getSimpleName();

    /**
     * Reads the current daily log file and returns its contents.
     * 
     * @param context App context.
     * @return String containing all logged keystrokes for today.
     */
    public static String readCurrentLogFile(final Context context) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd_MM_yyyy", Locale.getDefault());
        String fileName = "rboard_files_" + sdf.format(new Date()) + ".txt";
        File logFile = new File(context.getExternalFilesDir(null), fileName);

        if (!logFile.exists()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading log file: " + e.getMessage());
        }

        return text.toString();
    }

    /**
     * Clears the current daily log file.
     * 
     * @param context App context.
     */
    public static void truncateLogFile(final Context context) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd_MM_yyyy", Locale.getDefault());
        String fileName = "rboard_files_" + sdf.format(new Date()) + ".txt";
        File logFile = new File(context.getExternalFilesDir(null), fileName);

        if (logFile.exists()) {
            try (FileOutputStream fos = new FileOutputStream(logFile, false)) {
                fos.write("".getBytes());
            } catch (IOException e) {
                Log.e(TAG, "Error truncating log file: " + e.getMessage());
            }
        }
    }

    private LogReadUtils() {
        // Utility class
    }
}
