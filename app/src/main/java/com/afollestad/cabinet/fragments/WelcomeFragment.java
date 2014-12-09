package com.afollestad.cabinet.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.ui.DrawerActivity;

/**
 * @author Aidan Follestad (afollestad)
 */
public class WelcomeFragment extends Fragment {

    @Override
    public void onDetach() {
        DrawerActivity act = (DrawerActivity) getActivity();
        act.disableFab(false);
        act.mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.START);
        super.onDetach();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_welcome, null);
    }

    private View fileCard;
    private PopupMenu menu;
    private ImageView icon;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        final DrawerActivity act = (DrawerActivity) activity;
        new Thread(new Runnable() {
            @Override
            public void run() {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        act.disableFab(true);
                        act.mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.START);
                    }
                });
            }
        }).start();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewStub fileStub = (ViewStub) view.findViewById(R.id.fileCardStub);
        fileCard = fileStub.inflate();

        icon = (ImageView) fileCard.findViewById(R.id.image);
        icon.setImageResource(R.drawable.android_logo);
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fileCard.setActivated(!view.isActivated());
            }
        });

        fileCard.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                icon.performClick();
                return false;
            }
        });

        TextView title = (TextView) fileCard.findViewById(android.R.id.title);
        title.setText(R.string.file_stub_title);

        ((TextView) fileCard.findViewById(android.R.id.content)).setText(R.string.file_stub_size);

        View menuButton = fileCard.findViewById(R.id.menu);
        ContextThemeWrapper context = new ContextThemeWrapper(getActivity(), R.style.Widget_AppCompat_Light_PopupMenu);
        menu = new PopupMenu(context, menuButton);
        menu.inflate(R.menu.file_options);
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                menu.show();
            }
        });

        View finish = view.findViewById(R.id.finish);
        // work around for selector having wrong state initially
        finish.setPressed(false);
        finish.setPressed(true);
        finish.setPressed(false);
        finish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().putBoolean("shown_welcome", true).commit();
                ((DrawerActivity) getActivity()).switchDirectory(null, true);
            }
        });
    }
}
