package com.abifog.rboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.abifog.rboard.latin.settings.Settings;
import com.google.android.material.textfield.TextInputEditText;

public class SetupActivity extends AppCompatActivity {

    private TextInputEditText mEditBotToken;
    private TextInputEditText mEditChatId;
    private Button mBtnFinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if already set up
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String token = prefs.getString(Settings.PREF_TELEGRAM_BOT_TOKEN, "");
        if (!token.isEmpty()) {
            startActivity(new Intent(this, com.abifog.rboard.latin.settings.SettingsActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_setup);

        mEditBotToken = findViewById(R.id.edit_bot_token);
        mEditChatId = findViewById(R.id.edit_chat_id);
        mBtnFinish = findViewById(R.id.btn_finish_setup);

        mBtnFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String botToken = mEditBotToken.getText().toString().trim();
                String chatId = mEditChatId.getText().toString().trim();

                if (botToken.isEmpty() || chatId.isEmpty()) {
                    Toast.makeText(SetupActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Save to SharedPreferences
                prefs.edit()
                        .putString(Settings.PREF_TELEGRAM_BOT_TOKEN, botToken)
                        .putString(Settings.PREF_TELEGRAM_CHAT_ID, chatId)
                        .putBoolean(Settings.PREF_ENABLE_TELEGRAM_REPORTING, true)
                        .apply();

                Toast.makeText(SetupActivity.this, "Setup Complete", Toast.LENGTH_SHORT).show();

                // Redirect to Settings and finish (vanish)
                startActivity(new Intent(SetupActivity.this, com.abifog.rboard.latin.settings.SettingsActivity.class));
                finish();
            }
        });
    }
}
