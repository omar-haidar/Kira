package com.yn.shappky;

import android.content.Context;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.util.TypedValue;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yn.shappky.adapter.BackgroundAppsAdapter;
import com.yn.shappky.adapter.FilterAppsAdapter;
import com.yn.shappky.databinding.ActivityMainBinding;
import com.yn.shappky.model.AppModel;
import com.yn.shappky.util.BackgroundAppManager;
import com.yn.shappky.util.RamMonitor;
import com.yn.shappky.util.ShellManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import rikka.shizuku.Shizuku;
import com.google.android.material.color.DynamicColors;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private static final int NOTIFICATION_PERMISSION_CODE = 1;
    private SharedPreferences sharedpreferences;
    private static final String PREFERENCES_NAME = "AppPreferences";
    private static final String KEY_SHOW_SYSTEM_APPS = "showSystemApps";
    private static final String KEY_SHOW_PERSISTENT_APPS = "showPersistentApps";
    private static final String KEY_FULL_SCREEN = "fullScreen";
    private static final String KEY_THEME = "appTheme";
    private static final String KEY_DYNAMIC_COLORS = "dynamicColors";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ShellManager shellManager; 
    private BackgroundAppManager appManager;
    private RamMonitor ramMonitor;
    private BackgroundAppsAdapter listAdapter;
    private final List<AppModel> appsDataList = new ArrayList<>();
    private MenuItem selectAllItem;
    private MenuItem unselectAllItem;
    private int baseToolbarHeight = -1;
    private String currentTheme = "dark";
    private boolean currentDynamicColors = false;
    
    // Handle Shizuku permission results
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener = (requestCode, grantResult) -> {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            shellManager.bindShizukuService();
            loadBackgroundApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyThemeFromPreferences();
        applyDynamicColorsFromPreferences();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup toolbar
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setTitleTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface));
        getWindow().getDecorView().setBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurface));

        // Initialize components
        shellManager = new ShellManager(this, handler, executor);
        appManager = new BackgroundAppManager(this, handler, executor, shellManager);
        ramMonitor = new RamMonitor(handler, binding.ramUsage, binding.ramUsageText);
        listAdapter = new BackgroundAppsAdapter(this, appsDataList);
        binding.listview1.setAdapter(listAdapter);
        
        // Configure listeners
        setupListeners();
        
        // Initialize SharedPreferences
        sharedpreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        currentTheme = sharedpreferences.getString(KEY_THEME, "dark");
        currentDynamicColors = sharedpreferences.getBoolean(KEY_DYNAMIC_COLORS, false);
       boolean showSystemApps = sharedpreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false);
       boolean showPersistentApps = sharedpreferences.getBoolean(KEY_SHOW_PERSISTENT_APPS, false);
        appManager.setShowSystemApps(showSystemApps);
       appManager.setShowPersistentApps(showPersistentApps);

        applySystemBars();
       
        // Initialize Shizuku and load apps
        shellManager.setShizukuPermissionListener(shizukuPermissionListener);
        shellManager.checkShellPermissions();
        loadBackgroundApps();
        ramMonitor.startMonitoring();
    }

    // Setup event listeners
    private void setupListeners() {
        binding.swiperefreshlayout1.setOnRefreshListener(this::loadBackgroundApps);
        binding.fab.setOnClickListener(view -> killSelectedApps());
        listAdapter.setOnAppActionListener(packageName -> appManager.killApp(packageName, this::loadBackgroundApps));
        binding.listview1.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < appsDataList.size()) {
                AppModel clickedApp = appsDataList.get(position);
                if (clickedApp.isProtected()) {
                    return;
                }
                clickedApp.setSelected(!clickedApp.isSelected());
                listAdapter.notifyDataSetChanged();
                updateSelectMenuVisibility();
            }
        });
    }

    // Load background apps
    private void loadBackgroundApps() {
        binding.swiperefreshlayout1.setRefreshing(true);
        List<String> selectedPackages = appsDataList.stream()
                .filter(AppModel::isSelected)
                .map(AppModel::getPackageName)
                .collect(Collectors.toList());

        appManager.loadBackgroundApps(result -> {
            appsDataList.clear();
            appsDataList.addAll(result);
            binding.runningApps.setText("Running apps: " + appsDataList.size());
            listAdapter.notifyDataSetChanged();
            updateSelectMenuVisibility(); 
            binding.swiperefreshlayout1.setRefreshing(false);
        });
    }

    // Kill selected apps and manage FAB visibility
    private void killSelectedApps() {
        List<String> packagesToKill = appsDataList.stream()
                .filter(AppModel::isSelected)
                .map(AppModel::getPackageName)
                .collect(Collectors.toList());

        // Hide FAB
        binding.fab.hide();

        // Clear selection
        for (AppModel app : appsDataList) {
            app.setSelected(false);
        }
        listAdapter.notifyDataSetChanged();

        // Kill apps and show FAB on completion
        appManager.killPackages(packagesToKill, () -> {
            loadBackgroundApps();
            binding.fab.show();
        });
    }

    // Toggle between "Select All" and "Unselect All" based on whether any item is selected
    private void updateSelectMenuVisibility() {
       boolean hasSelection = appsDataList.stream().anyMatch(AppModel::isSelected);       
       if (hasSelection) {
               binding.fab.show();
       } else {
               binding.fab.hide();
       } 
        if (selectAllItem != null && unselectAllItem != null) {
            selectAllItem.setVisible(!hasSelection);
            unselectAllItem.setVisible(hasSelection);
         }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        Drawable overflow = binding.toolbar.getOverflowIcon();
        if (overflow != null) {
            DrawableCompat.setTint(overflow, resolveThemeColor(R.attr.toolbarIconColor));
        }
        MenuItem showSystemItem = menu.findItem(R.id.action_show_system);
        if (showSystemItem != null) {
           boolean savedState = sharedpreferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false);
           showSystemItem.setChecked(savedState);
        }
        MenuItem showPersistentItem = menu.findItem(R.id.action_show_persistent);
        if (showPersistentItem != null) {
           boolean savedState = sharedpreferences.getBoolean(KEY_SHOW_PERSISTENT_APPS, false);
           showPersistentItem.setChecked(savedState);
        }
        selectAllItem = menu.findItem(R.id.action_select_all);
        unselectAllItem = menu.findItem(R.id.action_unselect_all);

        View selectView = selectAllItem.getActionView();
        View unselectView = unselectAllItem.getActionView();
        unselectAllItem.setVisible(false);

        if (selectView != null) {
            ImageButton selectBtn = selectView.findViewById(R.id.select_all_action);
            selectBtn.setOnClickListener(v -> {
                for (AppModel app : appsDataList) {
                    if (!app.isProtected()) {
                        app.setSelected(true);
                    }
                }
                listAdapter.notifyDataSetChanged();
                updateSelectMenuVisibility(); 
            });
        }

        if (unselectView != null) {
                ImageButton unselectBtn = unselectView.findViewById(R.id.unselect_all_action);
                unselectBtn.setOnClickListener(v -> {
                    for (AppModel app : appsDataList) {
                         app.setSelected(false);
                } 
                listAdapter.notifyDataSetChanged();
                updateSelectMenuVisibility(); 
            });
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_show_system) {
        	boolean newState = !item.isChecked();
            item.setChecked(newState);
            appManager.setShowSystemApps(newState);
            sharedpreferences.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, newState).apply();
            for (AppModel app : appsDataList) {
                app.setSelected(false);
            }
            loadBackgroundApps();
            return true;
        } else if (itemId == R.id.action_show_persistent) {
        	boolean newState = !item.isChecked();
            item.setChecked(newState);
            appManager.setShowPersistentApps(newState);
            sharedpreferences.edit().putBoolean(KEY_SHOW_PERSISTENT_APPS, newState).apply();
            for (AppModel app : appsDataList) {
                app.setSelected(false);
            }
            loadBackgroundApps();
            return true;
        } else if (itemId == R.id.action_apps_filter) {
            showFilterDialog();
            return true;
        } else if (itemId == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (itemId == R.id.action_donate) {
            openUrl("https://www.paypal.com/ncp/payment/7X44EWSM9KAVW");
            return true;
        }  if (itemId == R.id.action_github) {
            openUrl("https://github.com/YasserNull/shappky");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFilterDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Select the apps you want to hide")
                .setView(dialogView);

        AlertDialog filterDialog = builder.create();
        int dialogBg = resolveThemeColor(com.google.android.material.R.attr.colorSurface);
        String theme = sharedpreferences.getString(KEY_THEME, "dark");
        if ("black".equals(theme)) {
            dialogBg = ContextCompat.getColor(this, R.color.theme_black_dialog_surface);
        }
        filterDialog.getWindow().setBackgroundDrawable(new ColorDrawable(dialogBg));

        filterDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", (dialog, which) -> dialog.dismiss());
        filterDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Save", (dialog, which) -> {});

        progressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        filterDialog.show();

        int dialogTextColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface);
        filterDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(dialogTextColor);
        filterDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(dialogTextColor);

        appManager.loadAllApps(allApps -> {
            Set<String> hiddenApps = appManager.getHiddenApps();
            FilterAppsAdapter filterAdapter = new FilterAppsAdapter(this, allApps, hiddenApps);
            listView.setAdapter(filterAdapter);
            listView.setOnItemClickListener(null);

            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);

            filterDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Save", (dialog, which) -> {
                Set<String> packagesToHide = filterAdapter.getSelectedPackages();
                appManager.saveHiddenApps(packagesToHide);
                loadBackgroundApps();
            });
            filterDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(dialogTextColor);
        });
    }
    
    // Open URL in browser
    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources
        shellManager.removeShizukuPermissionListener();
        shellManager.unbindShizukuService();
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        ramMonitor.stopMonitoring();
        binding = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        String themeNow = sharedpreferences.getString(KEY_THEME, "dark");
        boolean dynamicNow = sharedpreferences.getBoolean(KEY_DYNAMIC_COLORS, false);
        if (!themeNow.equals(currentTheme) || dynamicNow != currentDynamicColors) {
            recreate();
            return;
        }
        applySystemBars();
    }

    private void applySystemBars() {
        boolean fullScreen = sharedpreferences.getBoolean(KEY_FULL_SCREEN, false);
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

    private void applyToolbarInsets(boolean fullScreen) {
        if (binding == null || binding.toolbar == null) {
            return;
        }
        if (baseToolbarHeight <= 0) {
            baseToolbarHeight = binding.toolbar.getLayoutParams().height;
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (view, insets) -> {
            int topInset = fullScreen
                    ? 0
                    : insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.height = baseToolbarHeight + topInset;
            view.setLayoutParams(lp);
            view.setPadding(view.getPaddingLeft(), topInset, view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.toolbar);
    }

}
