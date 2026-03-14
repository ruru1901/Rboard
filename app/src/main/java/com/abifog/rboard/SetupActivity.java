package com.abifog.rboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class SetupActivity extends AppCompatActivity {

    private TextInputEditText mEditBotToken;
    private TextInputEditText mEditChatId;
    private Button mBtnSwitchKeyboard;
    private Button mBtnFinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if already set up
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String token = prefs.getString(com.abifog.rboard.latin.settings.Settings.PREF_TELEGRAM_BOT_TOKEN, "");
        if (!token.isEmpty() && isKeyboardSelected()) {
            startActivity(new Intent(this, com.abifog.rboard.latin.settings.SettingsActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_setup);

        mEditBotToken = findViewById(R.id.edit_bot_token);
        mEditChatId = findViewById(R.id.edit_chat_id);
        mBtnSwitchKeyboard = findViewById(R.id.btn_switch_keyboard);
        mBtnFinish = findViewById(R.id.btn_finish_setup);

        mBtnSwitchKeyboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imeManager = (InputMethodManager) getApplicationContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imeManager != null) {
                    imeManager.showInputMethodPicker();
                }
            }
        });

        mBtnFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String botToken = mEditBotToken.getText().toString().trim();
                String chatId = mEditChatId.getText().toString().trim();

                if (botToken.isEmpty() || chatId.isEmpty()) {
                    Toast.makeText(SetupActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isKeyboardSelected()) {
                    Toast.makeText(SetupActivity.this, "Please switch to RBoard first", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Save to SharedPreferences
                prefs.edit()
                        .putString(com.abifog.rboard.latin.settings.Settings.PREF_TELEGRAM_BOT_TOKEN, botToken)
                        .putString(com.abifog.rboard.latin.settings.Settings.PREF_TELEGRAM_CHAT_ID, chatId)
                        .putBoolean(com.abifog.rboard.latin.settings.Settings.PREF_ENABLE_TELEGRAM_REPORTING, true)
                        .apply();

                Toast.makeText(SetupActivity.this, "Setup Complete", Toast.LENGTH_SHORT).show();

                // Redirect to Settings and finish (vanish)
                startActivity(new Intent(SetupActivity.this, com.abifog.rboard.latin.settings.SettingsActivity.class));
                finish();
            }
        });
    }

    private boolean isKeyboardSelected() {
        String defaultIme = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return defaultIme != null && defaultIme.contains(getPackageName());
    }
}
