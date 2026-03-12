package com.yn.shappky;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private static final String PREFERENCES_NAME = "AppPreferences";
    
    private static final String KEY_SHOW_SYSTEM_APPS = "showSystemApps";
    private static final String KEY_SHOW_PERSISTENT_APPS = "showPersistentApps";
    private static final String KEY_AUTO_REFRESH = "autoRefresh";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
        getWindow().getDecorView().setBackgroundColor(Color.parseColor("#17181C"));

        sharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        setupSwitchItem(KEY_SHOW_SYSTEM_APPS, R.id.settings_show_system, R.id.title_show_system,
            R.id.summary_show_system, R.id.switch_show_system);
        setupSwitchItem(KEY_SHOW_PERSISTENT_APPS, R.id.settings_show_persistent, R.id.title_show_persistent,
            R.id.summary_show_persistent, R.id.switch_show_persistent);
        setupSwitchItem(KEY_AUTO_REFRESH, R.id.settings_auto_refresh, R.id.title_auto_refresh,
            R.id.summary_auto_refresh, R.id.switch_auto_refresh);
    }

    private void setupSwitchItem(String preferenceKey, int layoutId, int titleId, int summaryId, int switchId) {
        View settingItem = findViewById(layoutId);
        if (settingItem == null) return;

        Switch switchView = findViewById(switchId);
        TextView titleView = findViewById(titleId);
        TextView summaryView = findViewById(summaryId);

        boolean savedState = sharedPreferences.getBoolean(preferenceKey, false);
        if (switchView != null) {
            switchView.setChecked(savedState);
            switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sharedPreferences.edit().putBoolean(preferenceKey, isChecked).apply();
            });
        }

        settingItem.setOnClickListener(v -> {
            if (switchView != null) {
                switchView.setChecked(!switchView.isChecked());
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
