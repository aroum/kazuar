// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import io.github.aroum.kazuar.inputmethod.latin.utils.FragmentUtils;

/** Hosts AndroidX settings and keeps existing entry intents compatible. */
public abstract class PreferenceHostActivity extends FragmentActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    public static final String EXTRA_SHOW_FRAGMENT = ":android:show_fragment";
    public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":android:show_fragment_args";
    public static final String EXTRA_NO_HEADERS = ":android:no_headers";
    private static final int CONTENT_ID = 0x00f10001;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout content = new FrameLayout(this);
        content.setId(CONTENT_ID);
        setContentView(content);
        if (state == null) {
            String name = getIntent().getStringExtra(EXTRA_SHOW_FRAGMENT);
            if (isValidFragment(name)) {
                Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                        .instantiate(getClassLoader(), name);
                fragment.setArguments(getIntent().getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS));
                startPreferenceFragment(fragment, false);
            } else {
                finish();
            }
        }
    }

    public boolean isValidFragment(String name) { return FragmentUtils.isValidFragment(name); }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        if (!isValidFragment(pref.getFragment())) return false;
        Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), pref.getFragment());
        fragment.setArguments(pref.getExtras());
        startPreferenceFragment(fragment, true);
        return true;
    }

    public void startPreferenceFragment(Fragment fragment, boolean push) {
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction().replace(CONTENT_ID, fragment);
        if (push) transaction.addToBackStack(null);
        transaction.commit();
    }

    public void startPreferencePanel(String name, Bundle args, int titleRes,
            CharSequence title, Fragment caller, int requestCode) {
        if (!isValidFragment(name)) return;
        Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), name);
        fragment.setArguments(args);
        if (caller != null) fragment.setTargetFragment(caller, requestCode);
        startPreferenceFragment(fragment, true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
