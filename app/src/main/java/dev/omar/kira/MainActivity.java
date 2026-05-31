package dev.omar.kira;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yn.shappky.adapter.BackgroundAppsAdapter;
import com.yn.shappky.adapter.FilterAppsAdapter;
import com.yn.shappky.databinding.ActivityMainBinding;
import com.yn.shappky.model.AppModel;
import com.yn.shappky.shell.ShellExecutors;
import com.yn.shappky.shell.ShellResult;
import com.yn.shappky.ui.base.BaseActivity;
import com.yn.shappky.util.BackgroundAppManager;
import com.yn.shappky.util.RamMonitor;
import com.yn.shappky.util.ShellManager;

import rikka.shizuku.Shizuku;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainActivity extends BaseActivity {

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
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    shellManager.bindShizukuService();
                    loadBackgroundApps();
                }
            };

    private void applyToolbarInsets(boolean fullScreen) {
        if (binding == null || binding.toolbar == null) {
            return;
        }
        if (baseToolbarHeight <= 0) {
            baseToolbarHeight = binding.toolbar.getLayoutParams().height;
        }
        ViewCompat.setOnApplyWindowInsetsListener(
                binding.toolbar,
                (view, insets) -> {
                    int topInset =
                            fullScreen
                                    ? 0
                                    : insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
                    lp.height = baseToolbarHeight + topInset;
                    view.setLayoutParams(lp);
                    view.setPadding(
                            view.getPaddingLeft(),
                            topInset,
                            view.getPaddingRight(),
                            view.getPaddingBottom());
                    return insets;
                });
        ViewCompat.requestApplyInsets(binding.toolbar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[] {Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }
        StringBuilder sb = new StringBuilder();
        String command = "ps -A -o rss,name | grep '\\.' | grep -v '[-:@]'";
        ShellResult result = ShellExecutors.newShizukuExecutor(command);
        sb.append("Out : \n");
        sb.append(result.getStdout()).append("\n");
        sb.append("Error : \n");
        sb.append(result.getStderr()).append("\n");
        new MaterialAlertDialogBuilder(this)
                .setTitle("Command output")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();

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
        updatePermissionUi();
        ramMonitor.startMonitoring();
    }

    // Setup event listeners
    private void setupListeners() {
        binding.swiperefreshlayout1.setOnRefreshListener(this::loadBackgroundApps);
        binding.fab.setOnClickListener(view -> killSelectedApps());
        listAdapter.setOnAppActionListener(
                packageName -> appManager.killApp(packageName, this::loadBackgroundApps));
        binding.listview1.setOnItemClickListener(
                (parent, view, position, id) -> {
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
        if (!shellManager.hasAnyShellPermission()) {
            updatePermissionUi();
            return;
        }
        binding.swiperefreshlayout1.setRefreshing(true);
        List<String> selectedPackages =
                appsDataList.stream()
                        .filter(AppModel::isSelected)
                        .map(AppModel::getPackageName)
                        .collect(Collectors.toList());

        appManager.loadBackgroundApps(
                result -> {
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
        List<String> packagesToKill =
                appsDataList.stream()
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
        appManager.killPackages(
                packagesToKill,
                () -> {
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
        /*Drawable overflow = binding.toolbar.getOverflowIcon();
        if (overflow != null) {
            DrawableCompat.setTint(overflow, resolveThemeColor(R.attr.toolbarIconColor));
        }*/
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
            selectBtn.setOnClickListener(
                    v -> {
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
            unselectBtn.setOnClickListener(
                    v -> {
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
        }
        if (itemId == R.id.action_github) {
            openUrl("https://github.com/YasserNull/shappky");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFilterDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_filter, null);
        ListView listView = dialogView.findViewById(R.id.filter_list_view);
        androidx.appcompat.widget.SearchView searchView =
                dialogView.findViewById(R.id.filter_search);
        View loadingContainer = dialogView.findViewById(R.id.filter_loading_container);
        ProgressBar progressBar = dialogView.findViewById(R.id.filter_loading_progress);

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle("Select the apps you want to hide")
                        .setView(dialogView);

        AlertDialog filterDialog = builder.create();
        int dialogBg = resolveThemeColor(com.google.android.material.R.attr.colorSurface);
        String theme = sharedpreferences.getString(KEY_THEME, "dark");
        if ("black".equals(theme)) {
            dialogBg = ContextCompat.getColor(this, R.color.theme_black_dialog_surface);
        }
        filterDialog.getWindow().setBackgroundDrawable(new ColorDrawable(dialogBg));
        if (searchView != null) {
            View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
            if (searchPlate != null) {
                searchPlate.setBackgroundColor(dialogBg);
            }
            View searchEdit = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (searchEdit != null) {
                searchEdit.setBackgroundColor(dialogBg);
            }
            View submitArea = searchView.findViewById(androidx.appcompat.R.id.submit_area);
            if (submitArea != null) {
                submitArea.setBackgroundColor(dialogBg);
            }
            View editFrame = searchView.findViewById(androidx.appcompat.R.id.search_edit_frame);
            if (editFrame != null) {
                editFrame.setBackgroundColor(dialogBg);
            }
            View searchButton = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
            if (searchButton != null) {
                searchButton.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            View closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
            if (closeButton != null) {
                closeButton.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            searchView.setBackgroundColor(dialogBg);
        }

        filterDialog.setButton(
                AlertDialog.BUTTON_NEGATIVE, "Cancel", (dialog, which) -> dialog.dismiss());
        filterDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Save", (dialog, which) -> {});

        if (loadingContainer != null) {
            loadingContainer.setVisibility(View.VISIBLE);
        }
        progressBar.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        filterDialog.show();
        if ("black".equals(theme)) {
            listView.setBackgroundColor(dialogBg);
        }

        int dialogTextColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface);
        filterDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(dialogTextColor);
        filterDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(dialogTextColor);

        appManager.loadAllApps(
                allApps -> {
                    Set<String> hiddenApps = appManager.getHiddenApps();
                    FilterAppsAdapter filterAdapter =
                            new FilterAppsAdapter(this, allApps, hiddenApps);
                    listView.setAdapter(filterAdapter);
                    listView.setOnItemClickListener(null);
                    if (searchView != null) {
                        searchView.setOnQueryTextListener(
                                new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                                    @Override
                                    public boolean onQueryTextSubmit(String query) {
                                        filterAdapter.filter(query);
                                        return true;
                                    }

                                    @Override
                                    public boolean onQueryTextChange(String newText) {
                                        filterAdapter.filter(newText);
                                        return true;
                                    }
                                });
                    }

                    progressBar.setVisibility(View.GONE);
                    if (loadingContainer != null) {
                        loadingContainer.setVisibility(View.GONE);
                    }
                    listView.setVisibility(View.VISIBLE);

                    filterDialog.setButton(
                            AlertDialog.BUTTON_POSITIVE,
                            "Save",
                            (dialog, which) -> {
                                Set<String> packagesToHide = filterAdapter.getSelectedPackages();
                                appManager.saveHiddenApps(packagesToHide);
                                loadBackgroundApps();
                            });
                    filterDialog
                            .getButton(AlertDialog.BUTTON_POSITIVE)
                            .setTextColor(dialogTextColor);
                });
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
        updatePermissionUi();
    }

    @SuppressWarnings("deprecation")
    private void applySystemBars() {
        boolean fullScreen = sharedpreferences.getBoolean(KEY_FULL_SCREEN, false);
        // int systemBarColor = resolveThemeColor(com.google.android.material.R.attr.colorSurface);
        // getWindow().setStatusBarColor(systemBarColor);
        // getWindow().setNavigationBarColor(systemBarColor);

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

    private void applyPendingFullScreenPreference() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (prefs.contains("fullScreenPending")) {
            boolean pending = prefs.getBoolean("fullScreenPending", false);
            prefs.edit().putBoolean(KEY_FULL_SCREEN, pending).remove("fullScreenPending").apply();
        }
    }

    private void updatePermissionUi() {
        boolean hasPermission = shellManager.hasAnyShellPermission();
        if (hasPermission) {
            binding.permissionDeniedContainer.setVisibility(View.GONE);
            binding.swiperefreshlayout1.setVisibility(View.VISIBLE);
            binding.runningApps.setVisibility(View.VISIBLE);
            binding.linear1.setVisibility(View.VISIBLE);
            loadBackgroundApps();
        } else {
            binding.permissionDeniedContainer.setVisibility(View.VISIBLE);
            binding.swiperefreshlayout1.setVisibility(View.GONE);
            binding.runningApps.setVisibility(View.GONE);
            binding.linear1.setVisibility(View.GONE);
            binding.fab.hide();
            binding.swiperefreshlayout1.setRefreshing(false);
        }
    }
}
