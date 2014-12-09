package com.afollestad.cabinet.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.utils.ThemeUtils;
import com.afollestad.materialdialogs.MaterialDialog;

import java.util.Calendar;

public class AboutDialog extends DialogFragment {

    private static final String VERSION_UNAVAILABLE = "N/A";
    private DismissListener mListener;

    public interface DismissListener {
        public void onDismiss();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof DismissListener)) {
            throw new RuntimeException("The activity showing the about dialog must implement AboutDialog.DismissListener");
        }
        mListener = (DismissListener) getActivity();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Get app version
        PackageManager pm = getActivity().getPackageManager();
        String packageName = getActivity().getPackageName();
        String versionName;
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = VERSION_UNAVAILABLE;
        }
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View rootView = layoutInflater.inflate(R.layout.dialog_custom, null);
        rootView.findViewById(R.id.permissionsGroup).setVisibility(View.GONE);
        TextView aboutBodyView = (TextView) rootView.findViewById(R.id.body);
        aboutBodyView.setText(Html.fromHtml(getString(R.string.about_body, Calendar.getInstance().get(Calendar.YEAR))));
        aboutBodyView.setMovementMethod(new LinkMovementMethod());

        return new MaterialDialog.Builder(getActivity())
                .positiveText(android.R.string.ok)
                .theme(ThemeUtils.getDialogTheme(getActivity()))
                .positiveColorRes(R.color.cabinet_accent_color)
                .title(Html.fromHtml(getString(R.string.app_name_and_version, versionName)))
                .customView(rootView)
                .callback(new MaterialDialog.SimpleCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                    }
                }).build();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        mListener.onDismiss();
    }
}