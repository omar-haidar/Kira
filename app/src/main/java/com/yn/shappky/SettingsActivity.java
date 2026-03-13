package com.yn.shappky;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.TypedValue;
import android.os.Build;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.color.DynamicColors;
import com.topjohnwu.superuser.Shell;
import rikka.shizuku.Shizuku;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private static final String PREFERENCES_NAME = "AppPreferences";
    
    private static final String KEY_SHOW_SYSTEM_APPS = "showSystemApps";
    private static final String KEY_SHOW_PERSISTENT_APPS = "showPersistentApps";
    private static final String KEY_AUTO_REFRESH = "autoRefresh";
    private static final String KEY_FULL_SCREEN = "fullScreen";
    private static final String KEY_THEME = "appTheme";
    private static final String KEY_DYNAMIC_COLORS = "dynamicColors";
    private static final String KEY_PERMISSION_MODE = "permissionMode";
    private static final String KEY_FULLSCREEN_PENDING = "fullScreenPending";
    private Toolbar settingsToolbar;
    private int baseToolbarHeight = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyThemeFromPreferences();
        applyDynamicColorsFromPreferences();
        setContentView(R.layout.activity_settings);

        // Setup toolbar
        settingsToolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(settingsToolbar);
        settingsToolbar.setTitleTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
        if (settingsToolbar.getNavigationIcon() != null) {
            settingsToolbar.getNavigationIcon().setTint(resolveThemeColor(R.attr.toolbarIconColor));
        }
        getWindow().getDecorView().setBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurface));

        sharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);

        setupSwitchItem(KEY_SHOW_SYSTEM_APPS, R.id.settings_show_system, R.id.title_show_system,
            R.id.summary_show_system, R.id.switch_show_system);
        setupSwitchItem(KEY_SHOW_PERSISTENT_APPS, R.id.settings_show_persistent, R.id.title_show_persistent,
            R.id.summary_show_persistent, R.id.switch_show_persistent);
        setupSwitchItem(KEY_AUTO_REFRESH, R.id.settings_auto_refresh, R.id.title_auto_refresh,
            R.id.summary_auto_refresh, R.id.switch_auto_refresh);
        setupFullScreenSwitch();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setupSwitchItem(KEY_DYNAMIC_COLORS, R.id.settings_dynamic_colors, R.id.title_dynamic_colors,
                R.id.summary_dynamic_colors, R.id.switch_dynamic_colors);
        } else {
            View dynamicRow = findViewById(R.id.settings_dynamic_colors);
            View dynamicDivider = findViewById(R.id.divider_dynamic_colors);
            if (dynamicRow != null) dynamicRow.setVisibility(View.GONE);
            if (dynamicDivider != null) dynamicDivider.setVisibility(View.GONE);
        }
        setupPermissionModeDialog();
        setupThemeDialog();

        applySystemBars();
    }

    private void setupSwitchItem(String preferenceKey, int layoutId, int titleId, int summaryId, int switchId) {
        View settingItem = findViewById(layoutId);
        if (settingItem == null) return;

        CompoundButton switchView = findViewById(switchId);
        TextView titleView = findViewById(titleId);
        TextView summaryView = findViewById(summaryId);

        boolean savedState = sharedPreferences.getBoolean(preferenceKey, false);
        if (switchView != null) {
            switchView.setChecked(savedState);
            switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sharedPreferences.edit().putBoolean(preferenceKey, isChecked).apply();
                if (KEY_DYNAMIC_COLORS.equals(preferenceKey)) {
                    recreate();
                }
            });
        }

        settingItem.setOnClickListener(v -> {
            if (switchView != null) {
                switchView.setChecked(!switchView.isChecked());
            }
        });
    }

    private void setupFullScreenSwitch() {
        View settingItem = findViewById(R.id.settings_full_screen);
        CompoundButton switchView = findViewById(R.id.switch_full_screen);
        if (settingItem == null || switchView == null) {
            return;
        }
        boolean savedState = sharedPreferences.getBoolean(KEY_FULL_SCREEN, false);
        switchView.setChecked(savedState);
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit()
                    .putBoolean(KEY_FULLSCREEN_PENDING, isChecked)
                    .apply();
            showRestartDialogForFullScreen();
        });
        settingItem.setOnClickListener(v -> switchView.toggle());
    }

    private void showRestartDialogForFullScreen() {
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.restart_required_title)
                .setMessage(R.string.restart_required_message)
                .setPositiveButton(R.string.restart_now, (d, w) -> restartApp())
                .setNegativeButton(R.string.restart_later, null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            int dialogBg = resolveThemeColor(com.google.android.material.R.attr.colorSurface);
            String theme = sharedPreferences.getString(KEY_THEME, "dark");
            if ("black".equals(theme)) {
                dialogBg = ContextCompat.getColor(this, R.color.theme_black_dialog_surface);
            }
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(dialogBg)
            );
        }
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        finish();
        Runtime.getRuntime().exit(0);
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

    private void setupThemeDialog() {
        View settingItem = findViewById(R.id.settings_theme);
        TextView valueView = findViewById(R.id.theme_value);
        if (settingItem == null || valueView == null) {
            return;
        }

        String[] options = getResources().getStringArray(R.array.theme_options);
        valueView.setText(getThemeLabel(sharedPreferences.getString(KEY_THEME, "dark"), options));

        settingItem.setOnClickListener(v -> {
            String current = sharedPreferences.getString(KEY_THEME, "dark");
            int checked = getThemeIndex(current);
            androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.theme_dialog_title)
                    .setSingleChoiceItems(options, checked, (dlg, which) -> {
                        String newTheme = themeFromIndex(which);
                        if (!newTheme.equals(current)) {
                            sharedPreferences.edit().putString(KEY_THEME, newTheme).apply();
                            valueView.setText(options[which]);
                            recreate();
                        }
                        dlg.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .create();
            dialog.show();
            if (dialog.getWindow() != null) {
                int dialogBg = resolveThemeColor(com.google.android.material.R.attr.colorSurface);
                String theme = sharedPreferences.getString(KEY_THEME, "dark");
                if ("black".equals(theme)) {
                    dialogBg = ContextCompat.getColor(this, R.color.theme_black_dialog_surface);
                }
                dialog.getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(dialogBg)
                );
            }
        });
    }

    private void setupPermissionModeDialog() {
        View settingItem = findViewById(R.id.settings_permission_mode);
        TextView valueView = findViewById(R.id.permission_mode_value);
        if (settingItem == null || valueView == null) {
            return;
        }

        String[] options = new String[] {
                getString(R.string.permission_mode_shizuku),
                getString(R.string.permission_mode_root)
        };
        String current = sharedPreferences.getString(KEY_PERMISSION_MODE, "shizuku");
        valueView.setText("root".equals(current) ? options[1] : options[0]);

        settingItem.setOnClickListener(v -> {
            String currentMode = sharedPreferences.getString(KEY_PERMISSION_MODE, "shizuku");
            int checked = "root".equals(currentMode) ? 1 : 0;
            boolean shizukuAvailable = Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            boolean rootAvailable = Shell.getShell().isRoot();

            boolean[] enabled = new boolean[] { shizukuAvailable, rootAvailable };
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                    this, android.R.layout.simple_list_item_single_choice, options) {
                @Override
                public boolean isEnabled(int position) {
                    return enabled[position];
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = view.findViewById(android.R.id.text1);
                    if (text != null) {
                        int base = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface);
                        int disabledColor = (base & 0x00FFFFFF) | 0x66000000;
                        text.setTextColor(enabled[position] ? base : disabledColor);
                    }
                    return view;
                }
            };

            androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.permission_mode_dialog_title)
                    .setSingleChoiceItems(adapter, checked, (dlg, which) -> {
                        if (!enabled[which]) {
                            return;
                        }
                        String newMode = (which == 1) ? "root" : "shizuku";
                        if (!newMode.equals(currentMode)) {
                            sharedPreferences.edit().putString(KEY_PERMISSION_MODE, newMode).apply();
                            valueView.setText(options[which]);
                        }
                        dlg.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .create();
            dialog.show();
            if (dialog.getWindow() != null) {
                int dialogBg = resolveThemeColor(com.google.android.material.R.attr.colorSurface);
                String theme = sharedPreferences.getString(KEY_THEME, "dark");
                if ("black".equals(theme)) {
                    dialogBg = ContextCompat.getColor(this, R.color.theme_black_dialog_surface);
                }
                dialog.getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(dialogBg)
                );
            }
        });
    }

    private int getThemeIndex(String theme) {
        if ("white".equals(theme)) {
            return 1;
        }
        if ("black".equals(theme)) {
            return 2;
        }
        return 0;
    }

    private String themeFromIndex(int index) {
        if (index == 1) {
            return "white";
        }
        if (index == 2) {
            return "black";
        }
        return "dark";
    }

    private String getThemeLabel(String theme, String[] options) {
        int index = getThemeIndex(theme);
        if (index >= 0 && index < options.length) {
            return options[index];
        }
        return options[0];
    }

    private void applyThemeFromPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, "dark");
        boolean dynamic = prefs.getBoolean(KEY_DYNAMIC_COLORS, false);
        if (dynamic) {
            if ("white".equals(theme)) {
                setTheme(R.style.AppTheme_Dynamic_Light);
            } else if ("black".equals(theme)) {
                setTheme(R.style.AppTheme_Dynamic_Black);
            } else {
                setTheme(R.style.AppTheme_Dynamic_Dark);
            }
            return;
        }
        if ("white".equals(theme)) {
            setTheme(R.style.AppTheme_Light);
        } else if ("black".equals(theme)) {
            setTheme(R.style.AppTheme_Black);
        } else {
            setTheme(R.style.AppTheme_Dark);
        }
    }

    private void applyDynamicColorsFromPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_DYNAMIC_COLORS, false);
        if (enabled) {
            DynamicColors.applyToActivityIfAvailable(this);
            if ("black".equals(prefs.getString(KEY_THEME, "dark"))) {
                getTheme().applyStyle(R.style.AppTheme_Dynamic_Black_Override, true);
            }
        }
    }

    private int resolveThemeColor(int attr) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    private void applySystemBars() {
        boolean fullScreen = sharedPreferences.getBoolean(KEY_FULL_SCREEN, false);
        int systemBarColor = resolveThemeColor(com.google.android.material.R.attr.colorSurface);
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
