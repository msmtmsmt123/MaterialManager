package com.afollestad.cabinet.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.adapters.NavigationDrawerAdapter;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.ui.DrawerActivity;
import com.afollestad.cabinet.utils.Pins;
import com.afollestad.cabinet.utils.StorageHelper;
import com.afollestad.cabinet.utils.Utils;

public class NavigationDrawerFragment extends Fragment {

    private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";
    private static final String STATE_TITLE = "title";
    private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learn";
    private ActionBarDrawerToggle mDrawerToggle;

    private DrawerLayout mDrawerLayout;
    private RecyclerView mRecyclerView;
    private NavigationDrawerAdapter mAdapter;
    private View mFragmentContainerView;

    private int mCurrentSelectedPosition = 1;
    private boolean mFromSavedInstanceState;
    private boolean mUserLearnedDrawer;
    private CharSequence mTitle;

    private StorageHelper mStorageHelper;

    public NavigationDrawerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mUserLearnedDrawer = sp.getBoolean(PREF_USER_LEARNED_DRAWER, false);
        if (savedInstanceState != null) {
            mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
            mTitle = savedInstanceState.getCharSequence(STATE_TITLE);
            mFromSavedInstanceState = true;
        }
        mStorageHelper = new StorageHelper(getActivity(), new StorageHelper.StateListener() {
            @Override
            public void onStateChanged(boolean available, boolean writeable) {

            }
        });
        mStorageHelper.startWatchingExternalStorage();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mStorageHelper.stopWatchingExternalStorage();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRecyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_drawer, container, false);
        mRecyclerView.setClipToPadding(false);
        mAdapter = new NavigationDrawerAdapter(getActivity(), new NavigationDrawerAdapter.ClickListener() {
            @Override
            public void onClick(int index) {
                selectItem(index);
            }

            @Override
            public boolean onLongClick(final int index) {
                Pins.Item item = mAdapter.getItem(index);
                Utils.showConfirmDialog(getActivity(), R.string.remove_shortcut,
                        R.string.confirm_remove_shortcut, item.getDisplay(getActivity()), new Utils.ClickListener() {
                            @Override
                            public void onPositive(int which, View view) {
                                Pins.remove(getActivity(), index);
                                mAdapter.reload(getActivity());
                            }
                        }
                );
                return false;
            }
        });
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setCheckedPos(mCurrentSelectedPosition);
        return mRecyclerView;
    }

    public void setUp(int fragmentId, DrawerLayout drawerLayout) {
        mFragmentContainerView = getActivity().findViewById(fragmentId);
        mDrawerLayout = drawerLayout;
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        mDrawerToggle = new ActionBarDrawerToggle(getActivity(), mDrawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (!isAdded()) {
                    return;
                }
                getActivity().setTitle(mTitle);
                getActivity().invalidateOptionsMenu();
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (!isAdded()) {
                    return;
                }

                mTitle = getActivity().getTitle();
                getActivity().setTitle(R.string.app_name);

                if (!mUserLearnedDrawer) {
                    // The user manually opened the drawer; store this flag to prevent auto-showing
                    // the navigation drawer automatically in the future.
                    Toast.makeText(getActivity(), R.string.drawer_longpress_hint, Toast.LENGTH_LONG).show();
                    mUserLearnedDrawer = true;
                    SharedPreferences sp = PreferenceManager
                            .getDefaultSharedPreferences(getActivity());
                    sp.edit().putBoolean(PREF_USER_LEARNED_DRAWER, true).apply();
                }

                getActivity().invalidateOptionsMenu();
            }
        };

        // If the user hasn't 'learned' about the drawer, open it to introduce them to the drawer,
        // per the navigation drawer design guidelines.
        if (!mUserLearnedDrawer && !mFromSavedInstanceState) {
            mDrawerLayout.openDrawer(mFragmentContainerView);
        }

        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

    private void selectItem(int position) {
        if (position < 0) position = 1;
        mCurrentSelectedPosition = position;
        if (mRecyclerView != null) {
            mAdapter.setCheckedPos(position);
        }
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mFragmentContainerView);
        }
        DrawerActivity act = (DrawerActivity) getActivity();
        Pins.Item item = mAdapter.getItem(position);
        act.switchDirectory(item);
        mTitle = item.getDisplay(getActivity());
        mDrawerLayout.closeDrawers();
    }

    public void selectFile(File file) {
        mCurrentSelectedPosition = mAdapter.setCheckedFile(file);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
        outState.putCharSequence(STATE_TITLE, mTitle);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerLayout.getDrawerLockMode(Gravity.START) == DrawerLayout.LOCK_MODE_LOCKED_CLOSED ||
                mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    private ActionBar getActionBar() {
        return ((ActionBarActivity) getActivity()).getSupportActionBar();
    }

    public void reload(boolean open) {
        Activity act = getActivity();
        if (act != null) {
            mAdapter.reload(act);
            if (open) mDrawerLayout.openDrawer(Gravity.START);
        }
    }
}