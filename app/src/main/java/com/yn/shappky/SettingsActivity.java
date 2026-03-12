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
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private static final String PREFERENCES_NAME = "AppPreferences";
    
    private static final String KEY_SHOW_SYSTEM_APPS = "showSystemApps";
    private static final String KEY_SHOW_PERSISTENT_APPS = "showPersistentApps";
    private static final String KEY_AUTO_REFRESH = "autoRefresh";
    private static final String KEY_FULL_SCREEN = "fullScreen";
    private Toolbar settingsToolbar;
    private int baseToolbarHeight = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Setup toolbar
        settingsToolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(settingsToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
        getWindow().getDecorView().setBackgroundColor(
                ContextCompat.getColor(this, R.color.colorPrimary)
        );

        sharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        setupSwitchItem(KEY_SHOW_SYSTEM_APPS, R.id.settings_show_system, R.id.title_show_system,
            R.id.summary_show_system, R.id.switch_show_system);
        setupSwitchItem(KEY_SHOW_PERSISTENT_APPS, R.id.settings_show_persistent, R.id.title_show_persistent,
            R.id.summary_show_persistent, R.id.switch_show_persistent);
        setupSwitchItem(KEY_AUTO_REFRESH, R.id.settings_auto_refresh, R.id.title_auto_refresh,
            R.id.summary_auto_refresh, R.id.switch_auto_refresh);
        setupSwitchItem(KEY_FULL_SCREEN, R.id.settings_full_screen, R.id.title_full_screen,
            R.id.summary_full_screen, R.id.switch_full_screen);

        applySystemBars();
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
                if (KEY_FULL_SCREEN.equals(preferenceKey)) {
                    applySystemBars();
                }
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

    @Override
    protected void onResume() {
        super.onResume();
        applySystemBars();
    }

    private void applySystemBars() {
        boolean fullScreen = sharedPreferences.getBoolean(KEY_FULL_SCREEN, false);
        int systemBarColor = ContextCompat.getColor(this, R.color.colorPrimary);
        getWindow().setStatusBarColor(systemBarColor);
        getWindow().setNavigationBarColor(systemBarColor);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), !fullScreen);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        if (fullScreen) {
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars());
        }

        applyToolbarInsets(fullScreen);
    }

    private void applyToolbarInsets(boolean fullScreen) {
        if (settingsToolbar == null) {
            return;
        }
        if (baseToolbarHeight <= 0) {
            baseToolbarHeight = settingsToolbar.getLayoutParams().height;
        }
        ViewCompat.setOnApplyWindowInsetsListener(settingsToolbar, (view, insets) -> {
            int topInset = fullScreen
                    ? 0
                    : insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.height = baseToolbarHeight + topInset;
            view.setLayoutParams(lp);
            view.setPadding(view.getPaddingLeft(), topInset, view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(settingsToolbar);
    }
}
