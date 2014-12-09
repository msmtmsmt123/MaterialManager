package com.afollestad.cabinet.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.file.root.RootFile;
import com.afollestad.cabinet.utils.Perm;
import com.afollestad.cabinet.utils.ThemeUtils;
import com.afollestad.cabinet.utils.TimeUtils;
import com.afollestad.cabinet.utils.Utils;
import com.afollestad.materialdialogs.MaterialDialog;

import java.util.GregorianCalendar;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class DetailsDialog extends DialogFragment implements CompoundButton.OnCheckedChangeListener {

    public DetailsDialog() {
    }

    public static DetailsDialog create(File file) {
        DetailsDialog dialog = new DetailsDialog();
        Bundle args = new Bundle();
        args.putSerializable("file", file);
        dialog.setArguments(args);
        return dialog;
    }

    private TextView body;
    private File file;
    public CheckBox ownerR;
    public CheckBox ownerW;
    public CheckBox ownerX;
    public CheckBox groupR;
    public CheckBox groupW;
    public CheckBox groupX;
    public CheckBox otherR;
    public CheckBox otherW;
    public CheckBox otherX;
    public String permissionsString;
    public String initialPermission;

    private Spanned getBody(boolean loadDirContents, final View view) {
        if (getActivity() == null) return null;
        String content;
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(file.lastModified());
        if (file.isDirectory()) {
            if (view != null)
                view.findViewById(R.id.permissionsGroup).setVisibility(View.GONE);
            String size = getString(R.string.unavailable);
            if (!file.isRemote()) {
                if (loadDirContents) {
                    size = file.getSizeString();
                } else {
                    size = getString(R.string.loading);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final Spanned newBody = getBody(true, null);
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    body.setText(newBody);
                                }
                            });
                        }
                    }).start();
                }
            }
            if (getActivity() == null) return null;
            content = getString(R.string.details_body_dir,
                    file.getName(), file.getPath(), size, TimeUtils.toStringLong(cal));
        } else {
            if (permissionsString == null) {
                ownerR.setEnabled(false);
                ownerW.setEnabled(false);
                ownerX.setEnabled(false);
                groupR.setEnabled(false);
                groupW.setEnabled(false);
                groupX.setEnabled(false);
                otherR.setEnabled(false);
                otherW.setEnabled(false);
                otherX.setEnabled(false);
                if (!Shell.SU.available()) {
                    permissionsString = getString(R.string.superuser_not_available);
                    if (view != null)
                        view.findViewById(R.id.permissionsGroup).setVisibility(View.GONE);
                } else {
                    permissionsString = getString(R.string.loading);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            invalidatePermissions(true);
                            final Spanned newBody = getBody(false, view);
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ownerR.setEnabled(true);
                                    ownerW.setEnabled(true);
                                    ownerX.setEnabled(true);
                                    groupR.setEnabled(true);
                                    groupW.setEnabled(true);
                                    groupX.setEnabled(true);
                                    otherR.setEnabled(true);
                                    otherW.setEnabled(true);
                                    otherX.setEnabled(true);
                                    body.setText(newBody);
                                    invalidatePermissions(false);
                                }
                            });
                        }
                    }).start();
                }
            }
            content = getString(R.string.details_body_file,
                    file.getName(), file.getPath(), file.getSizeString(), TimeUtils.toStringLong(cal), permissionsString);
        }
        return Html.fromHtml(content);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        file = (File) getArguments().getSerializable("file");
        file.setContext(getActivity());
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View rootView = layoutInflater.inflate(R.layout.dialog_custom, null);

        ownerR = (CheckBox) rootView.findViewById(R.id.ownerR);
        ownerW = (CheckBox) rootView.findViewById(R.id.ownerW);
        ownerX = (CheckBox) rootView.findViewById(R.id.ownerX);
        groupR = (CheckBox) rootView.findViewById(R.id.groupR);
        groupW = (CheckBox) rootView.findViewById(R.id.groupW);
        groupX = (CheckBox) rootView.findViewById(R.id.groupX);
        otherR = (CheckBox) rootView.findViewById(R.id.otherR);
        otherW = (CheckBox) rootView.findViewById(R.id.otherW);
        otherX = (CheckBox) rootView.findViewById(R.id.otherX);

        body = (TextView) rootView.findViewById(R.id.body);
        body.setText(getBody(false, rootView));

        return new MaterialDialog.Builder(getActivity())
                .positiveText(android.R.string.ok)
                .positiveColorRes(R.color.cabinet_accent_color)
                .theme(ThemeUtils.getDialogTheme(getActivity()))
                .title(R.string.details)
                .customView(rootView)
                .callback(new MaterialDialog.SimpleCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        applyPermissionsIfNecessary();
                    }
                }).build();
    }

    private void invalidatePermissions(boolean reload) {
        if (reload) {
            try {
                List<String> results = RootFile.runAsRoot(getActivity(), "ls -l \"" + file.getPath() + "\"", file.getParent());

                if (results.isEmpty()) {
                    String path = file.getPath().replace("/0/", "/legacy/");
                    results = RootFile.runAsRoot(getActivity(), "ls -l \"" + path + "\"", file.getParent());
                }

                if (results.size() > 0 && getActivity() != null) {
                    final List<String> finalResults = results;
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            permissionsString = Perm.parse(finalResults.get(0), DetailsDialog.this);
                            initialPermission = permissionsString;
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            int owner = 0;
            if (ownerR.isChecked()) owner += Perm.READ;
            if (ownerW.isChecked()) owner += Perm.WRITE;
            if (ownerX.isChecked()) owner += Perm.EXECUTE;
            int group = 0;
            if (groupR.isChecked()) group += Perm.READ;
            if (groupW.isChecked()) group += Perm.WRITE;
            if (groupX.isChecked()) group += Perm.EXECUTE;
            int other = 0;
            if (otherR.isChecked()) other += Perm.READ;
            if (otherW.isChecked()) other += Perm.WRITE;
            if (otherX.isChecked()) other += Perm.EXECUTE;
            permissionsString = owner + "" + group + "" + other;
            body.setText(getBody(false, getView()));

            ownerR.setOnCheckedChangeListener(this);
            ownerW.setOnCheckedChangeListener(this);
            ownerX.setOnCheckedChangeListener(this);
            groupR.setOnCheckedChangeListener(this);
            groupW.setOnCheckedChangeListener(this);
            groupX.setOnCheckedChangeListener(this);
            otherR.setOnCheckedChangeListener(this);
            otherW.setOnCheckedChangeListener(this);
            otherX.setOnCheckedChangeListener(this);
        }
    }

    private void applyPermissionsIfNecessary() {
        if ((permissionsString == null || permissionsString.length() != 3) ||
                (initialPermission == null || initialPermission.length() != 3) ||
                (permissionsString != null && permissionsString.equals(initialPermission)) || !Shell.SU.available()) {
            return;
        }
        final ProgressDialog mDialog = new ProgressDialog(getActivity());
        mDialog.setCancelable(false);
        mDialog.setMessage(getString(R.string.applying_permissions));
        mDialog.setIndeterminate(true);
        mDialog.show();
        Perm.chmod(file, permissionsString, new Perm.Callback() {
            @Override
            public void onComplete(boolean result, String error) {
                mDialog.dismiss();
                if (!result)
                    Utils.showErrorDialog(getActivity(), error);
            }
        });
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        invalidatePermissions(false);
    }
}
