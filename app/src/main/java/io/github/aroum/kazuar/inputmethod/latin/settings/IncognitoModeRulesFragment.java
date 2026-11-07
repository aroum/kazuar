package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;
import androidx.preference.PreferenceCategory;
import io.github.aroum.kazuar.inputmethod.latin.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class IncognitoModeRulesFragment extends SubScreenFragment
        implements SearchView.OnQueryTextListener {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppOverridePreference> preferences = new ArrayList<>();
    private PreferenceCategory appsCategory;
    private String query = "";
    private boolean destroyed;
    private final Runnable filter = () -> {
        for (AppOverridePreference pref : preferences) {
            String text = pref.getTitle() + " " + pref.getSummary();
            pref.setVisible(text.toLowerCase(Locale.ROOT).contains(query));
        }
    };

    private static final class AppItem {
        final String label;
        final String packageName;
        final Drawable icon;
        AppItem(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(R.xml.prefs_screen_incognito_mode_rules);
        setHasOptionsMenu(true);
        appsCategory = findPreference("incognito_apps_category");
        PackageManager pm = requireContext().getApplicationContext().getPackageManager();
        loader.execute(() -> {
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<AppItem> apps = new ArrayList<>();
            HashSet<String> seen = new HashSet<>();
            for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
                if (Thread.currentThread().isInterrupted()) return;
                if (info.activityInfo == null || !seen.add(info.activityInfo.packageName)) continue;
                apps.add(new AppItem(info.loadLabel(pm).toString(),
                        info.activityInfo.packageName, info.loadIcon(pm)));
            }
            apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
            handler.post(() -> {
                if (destroyed || !isAdded()) return;
                for (AppItem app : apps) {
                    AppOverridePreference pref = new AppOverridePreference(requireContext(), () -> {
                        Bundle arguments = new Bundle();
                        arguments.putString(AppOverrideSettingsFragment.ARG_PACKAGE, app.packageName);
                        ((PreferenceHostActivity) requireActivity()).startPreferencePanel(
                                AppOverrideSettingsFragment.class.getName(), arguments, 0, app.label, this, 0);
                    });
                    pref.setKey(AppOverrides.key(app.packageName, AppOverrides.ENABLED));
                    pref.setTitle(app.label);
                    pref.setSummary(app.packageName);
                    pref.setIcon(app.icon);
                    pref.setDefaultValue(false);
                    preferences.add(pref);
                    appsCategory.addPreference(pref);
                }
                filter.run();
            });
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem item = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.search_title);
        item.setIcon(android.R.drawable.ic_menu_search);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        SearchView view = new SearchView(requireContext());
        view.setOnQueryTextListener(this);
        item.setActionView(view);
    }

    @Override public boolean onQueryTextSubmit(String text) { return true; }
    @Override public boolean onQueryTextChange(String text) {
        query = text.toLowerCase(Locale.ROOT);
        handler.removeCallbacks(filter);
        handler.postDelayed(filter, 150);
        return true;
    }

    @Override public void onResume() {
        super.onResume();
        for (AppOverridePreference pref : preferences) {
            pref.setChecked(getSharedPreferences().getBoolean(pref.getKey(), false));
        }
    }

    @Override public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        loader.shutdownNow();
        super.onDestroy();
    }
}
