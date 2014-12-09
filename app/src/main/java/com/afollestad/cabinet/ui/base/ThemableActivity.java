package com.afollestad.cabinet.ui.base;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.afollestad.cabinet.utils.ThemeUtils;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ThemableActivity extends ActionBarActivity {

    private ThemeUtils mThemeUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mThemeUtils = new ThemeUtils(this);
        setTheme(mThemeUtils.getCurrent());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mThemeUtils.isChanged()) {
            setTheme(mThemeUtils.getCurrent());
            recreate();
        }
    }
}
