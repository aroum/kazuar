package io.github.sds100.keymapper.inputmethod.latin.settings;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.sds100.keymapper.inputmethod.latin.R;

public final class IncognitoModeRulesFragment extends SubScreenFragment implements SearchView.OnQueryTextListener {

    private PreferenceCategory mAppsCategory;
    private List<AppItem> mInstalledApps = new ArrayList<>();
    private AppLoaderTask mAppLoaderTask;

    private static class AppItem {
        CharSequence name;
        String packageName;
        Drawable icon;

        AppItem(CharSequence name, String packageName, Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_incognito_mode_rules);
        setHasOptionsMenu(true);

        mAppsCategory = (PreferenceCategory) findPreference("incognito_apps_category");

        if (mAppLoaderTask != null) {
            mAppLoaderTask.cancel(true);
        }
        mAppLoaderTask = new AppLoaderTask(getActivity());
        mAppLoaderTask.execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAppLoaderTask != null) {
            mAppLoaderTask.cancel(true);
            mAppLoaderTask = null;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        
        MenuItem searchItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Search");
        searchItem.setIcon(android.R.drawable.ic_menu_search);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        
        SearchView searchView = new SearchView(getActivity());
        searchView.setOnQueryTextListener(this);
        searchItem.setActionView(searchView);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        filterApps(newText);
        return true;
    }

    private void filterApps(String query) {
        mAppsCategory.removeAll();
        for (AppItem app : mInstalledApps) {
            if (TextUtils.isEmpty(query) || app.name.toString().toLowerCase().contains(query.toLowerCase()) || app.packageName.toLowerCase().contains(query.toLowerCase())) {
                CheckBoxPreference pref = new CheckBoxPreference(getActivity());
                pref.setKey("incognito_ignore_" + app.packageName.toLowerCase(java.util.Locale.US));
                pref.setTitle(app.name);
                pref.setSummary(app.packageName);
                pref.setIcon(app.icon);
                pref.setDefaultValue(false);
                mAppsCategory.addPreference(pref);
            }
        }
    }

    private class AppLoaderTask extends AsyncTask<Void, Void, List<AppItem>> {
        private final Context mContext;
        private final PackageManager mPm;

        AppLoaderTask(Context context) {
            mContext = context;
            mPm = context.getPackageManager();
        }

        @Override
        protected List<AppItem> doInBackground(Void... voids) {
            List<ApplicationInfo> packages = mPm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppItem> appItems = new ArrayList<>();
            for (ApplicationInfo info : packages) {
                if (isCancelled()) break;
                // Exclude ourselves maybe? We can include everything.
                CharSequence name = mPm.getApplicationLabel(info);
                Drawable icon = mPm.getApplicationIcon(info);
                appItems.add(new AppItem(name, info.packageName, icon));
            }
            Collections.sort(appItems, new Comparator<AppItem>() {
                @Override
                public int compare(AppItem o1, AppItem o2) {
                    return o1.name.toString().compareToIgnoreCase(o2.name.toString());
                }
            });
            return appItems;
        }

        @Override
        protected void onPostExecute(List<AppItem> appItems) {
            if (!isCancelled() && getActivity() != null) {
                mInstalledApps = appItems;
                filterApps("");
            }
        }
    }
}
