package com.afollestad.cabinet.file;

import android.app.Activity;
import android.app.ProgressDialog;
import android.util.Log;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.file.base.FileFilter;
import com.afollestad.cabinet.file.root.LsParser;
import com.afollestad.cabinet.services.NetworkService;
import com.afollestad.cabinet.sftp.SftpClient;
import com.afollestad.cabinet.utils.Utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class LocalFile extends File {

    public LocalFile(Activity context) {
        super(context, "/");
    }

    public LocalFile(Activity context, String path) {
        super(context, path);
    }

    public LocalFile(Activity context, java.io.File local) {
        super(context, local.getAbsolutePath());
    }

    public LocalFile(Activity context, File parent, String name) {
        super(context, parent.getPath() + (parent.getPath().equals("/") ? "" : "/") + name);
    }

    public boolean isSearchResult;

    @Override
    public boolean isHidden() {
        java.io.File mFile = new java.io.File(getPath());
        return mFile.isHidden() || mFile.getName().startsWith(".");
    }

    @Override
    public void createFile(final SftpClient.CompletionCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (requiresRoot()) {
                        runAsRoot("touch \"" + getPath() + "\"");
                    } else if (!toJavaFile().createNewFile())
                        throw new Exception("An unknown error occurred while creating your file.");
                    callback.onComplete();
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onError(e);
                }
            }
        }).start();
    }

    @Override
    public void mkdir(final SftpClient.CompletionCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mkdirSync();
                    callback.onComplete();
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onError(e);
                }
            }
        }).start();
    }

    public void mkdirSync() throws Exception {
        if (requiresRoot()) {
            runAsRoot("mkdir -P \"" + getPath() + "\"");
        } else {
            new java.io.File(getPath()).mkdirs();
        }
        if (!new java.io.File(getPath()).exists())
            throw new Exception("Unknown error");
    }

    @Override
    public void rename(final File newFile, final SftpClient.FileCallback callback) {
        getContext().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (newFile.isRemote()) {
                    final ProgressDialog connectProgress = Utils.showProgressDialog(getContext(), R.string.connecting);
                    getContext().getNetworkService().getSftpClient(new NetworkService.SftpGetCallback() {
                        @Override
                        public void onSftpClient(final SftpClient client) {
                            connectProgress.dismiss();
                            final ProgressDialog uploadProgress = Utils.showProgressDialog(getContext(), R.string.uploading);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        uploadRecursive(client, LocalFile.this, (CloudFile) newFile, true, true);
                                        getContext().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                setPath(newFile.getPath());
                                                uploadProgress.dismiss();
                                                callback.onComplete(newFile);
                                            }
                                        });
                                    } catch (final Exception e) {
                                        e.printStackTrace();
                                        getContext().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                uploadProgress.dismiss();
                                                callback.onError(null);
                                                Utils.showErrorDialog(getContext(), R.string.failed_upload_file, e);
                                            }
                                        });
                                    }
                                }
                            }).start();
                        }

                        @Override
                        public void onError(final Exception e) {
                            getContext().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    connectProgress.dismiss();
                                    callback.onError(null);
                                    Utils.showErrorDialog(getContext(), R.string.failed_connect_server, e);
                                }
                            });
                        }
                    }, (CloudFile) newFile);
                } else {
                    Utils.checkDuplicates(getContext(), newFile, new Utils.DuplicateCheckResult() {
                        @Override
                        public void onResult(final File newFile) {
                            if (requiresRoot()) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            runAsRoot("mv \"" + getPath() + "\" \"" + newFile.getPath() + "\"");
                                            getContext().runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    updateMediaDatabase(LocalFile.this, MediaUpdateType.REMOVE);
                                                    setPath(newFile.getPath());
                                                    callback.onComplete(newFile);
                                                    updateMediaDatabase(newFile, MediaUpdateType.ADD);
                                                }
                                            });
                                        } catch (final Exception e) {
                                            e.printStackTrace();
                                            getContext().runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Utils.showErrorDialog(getContext(), R.string.failed_rename_file, e);
                                                    callback.onError(null);
                                                }
                                            });
                                        }
                                    }
                                }).start();
                            } else if (new java.io.File(getPath()).renameTo(newFile.toJavaFile())) {
                                getContext().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateMediaDatabase(LocalFile.this, MediaUpdateType.REMOVE);
                                        setPath(newFile.getPath());
                                        callback.onComplete(newFile);
                                        updateMediaDatabase(newFile, MediaUpdateType.ADD);
                                    }
                                });
                            } else {
                                getContext().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Utils.showErrorDialog(getContext(), R.string.failed_rename_file, new Exception("Unknown error"));
                                        callback.onError(null);
                                    }
                                });
                            }
                        }
                    });
                }
            }
        });
    }

    private void performCopy(final File dest, final SftpClient.FileCallback callback) {
        if (dest.isRemote()) {
            final ProgressDialog connectProgress = Utils.showProgressDialog(getContext(), R.string.connecting);
            getContext().getNetworkService().getSftpClient(new NetworkService.SftpGetCallback() {
                @Override
                public void onSftpClient(final SftpClient client) {
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            connectProgress.dismiss();
                            final ProgressDialog uploadProgress = Utils.showProgressDialog(getContext(), R.string.uploading);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        uploadRecursive(client, LocalFile.this, (CloudFile) dest, false, false);
                                        getContext().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                uploadProgress.dismiss();
                                                callback.onComplete(dest);
                                            }
                                        });
                                    } catch (final Exception e) {
                                        e.printStackTrace();
                                        getContext().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                uploadProgress.dismiss();
                                                callback.onError(null);
                                                Utils.showErrorDialog(getContext(), R.string.failed_upload_file, e);
                                            }
                                        });
                                    }
                                }
                            }).start();
                        }
                    });
                }

                @Override
                public void onError(final Exception e) {
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            connectProgress.dismiss();
                            callback.onError(null);
                            Utils.showErrorDialog(getContext(), R.string.failed_connect_server, e);
                        }
                    });
                }
            }, (CloudFile) dest);
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (isDirectory()) {
                        try {
                            copyRecursive(toJavaFile(), dest.toJavaFile(), false);
                            getContext().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onComplete(dest);
                                }
                            });
                        } catch (Exception e) {
                            getContext().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Utils.showErrorDialog(getContext(), R.string.failed_copy_file, new Exception("Unable to create the destination directory."));
                                    callback.onError(null);
                                }
                            });
                        }

                    } else {
                        try {
                            final LocalFile result = copySync(toJavaFile(), dest.toJavaFile());
                            getContext().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onComplete(result);
                                }
                            });
                        } catch (final Exception e) {
                            e.printStackTrace();
                            getContext().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Utils.showErrorDialog(getContext(), R.string.failed_copy_file, e);
                                    callback.onError(null);
                                }
                            });
                        }
                    }
                }
            }).start();
        }
    }

    public void copy(final File newFile, final SftpClient.FileCallback callback, boolean checkDuplicates) {
        if (checkDuplicates) {
            Utils.checkDuplicates(getContext(), newFile, new Utils.DuplicateCheckResult() {
                @Override
                public void onResult(final File dest) {
                    performCopy(newFile, callback);
                }
            });
        } else {
            performCopy(newFile, callback);
        }
    }

    @Override
    public void copy(File newFile, final SftpClient.FileCallback callback) {
        copy(newFile, callback, true);
    }

    private LocalFile copySync(java.io.File file, java.io.File newFile) throws Exception {
        LocalFile dest = (LocalFile) Utils.checkDuplicatesSync(getContext(), new LocalFile(getContext(), newFile));
        if (requiresRoot()) {
            runAsRoot("cp -R \"" + file.getAbsolutePath() + "\" \"" + dest.getPath() + "\"");
            return dest;
        }
        InputStream in = new FileInputStream(file);
        OutputStream out = new FileOutputStream(dest.toJavaFile());
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
        File scanFile = new LocalFile(getContext(), newFile);
        updateMediaDatabase(scanFile, MediaUpdateType.ADD);
        return dest;
    }

    private void copyRecursive(java.io.File dir, java.io.File to, boolean deleteAfter) throws Exception {
        if (!to.mkdir()) throw new Exception("Unable to create the destination directory.");
        java.io.File[] subFiles = dir.listFiles();
        for (java.io.File f : subFiles) {
            final File old = new LocalFile(getContext(), f);
            java.io.File dest = new java.io.File(to, f.getName());
            if (f.isDirectory()) copyRecursive(f, dest, deleteAfter);
            else {
                if (deleteAfter) {
                    if (!f.renameTo(dest))
                        throw new Exception("Failed to move a file to the new directory.");
                    else updateMediaDatabase(old, MediaUpdateType.REMOVE);
                } else {
                    try {
                        copySync(f, dest);
                    } catch (Exception e) {
                        throw new Exception("Failed to copy a file to the new directory (" + e.getMessage() + ").");
                    }
                }
            }
        }
        if (deleteAfter) dir.delete();
    }

    public static int getFileCount(java.io.File parent) {
        int count = parent.isDirectory() ? 0 : 1;
        java.io.File[] children = parent.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                count += getFileCount(child);
            }
        }
        return count;
    }

    public static int getFileCount(List<File> files) {
        int count = 0;
        for (File fi : files) {
            count += getFileCount(fi.toJavaFile());
        }
        return count;
    }

    private void log(String message) {
        Log.v("LocalFile", message);
    }

    private File uploadRecursive(SftpClient client, LocalFile local, CloudFile dest, boolean deleteAfter, boolean checkDuplicates) throws Exception {
        if (checkDuplicates)
            dest = (CloudFile) Utils.checkDuplicatesSync(getContext(), dest);
        if (local.isDirectory()) {
            log("Uploading local directory " + local.getPath() + " to " + dest.getPath());
            try {
                client.mkdirSync(dest.getPath());
            } catch (Exception e) {
                throw new Exception("Failed to create the destination directory " + dest.getPath() + " (" + e.getMessage() + ")");
            }
            log("Getting file listing for " + local.getPath());
            List<File> contents = local.listFilesSync(true);
            for (File lf : contents) {
                CloudFile newFile = new CloudFile(getContext(), dest, lf.getName(), lf.isDirectory());
                if (lf.isDirectory()) {
                    uploadRecursive(client, (LocalFile) lf, newFile, deleteAfter, checkDuplicates);
                } else {
                    log(" >> Uploading sub-file: " + lf.getPath() + " to " + newFile.getPath());
                    client.putSync(lf.getPath(), newFile.getPath());
                    if (deleteAfter) {
                        if (!lf.toJavaFile().delete()) {
                            throw new Exception("Failed to delete old local file " + lf.getPath());
                        } else {
                            updateMediaDatabase(lf, MediaUpdateType.REMOVE);
                        }
                    }
                }
            }
            if (deleteAfter) {
                wipeDirectory(local, null);
            }
        } else {
            log("Uploading file: " + local.getPath());
            try {
                client.putSync(local.getPath(), dest.getPath());
            } catch (Exception e) {
                throw new Exception("Failed to upload " + local.getPath() + " (" + e.getMessage() + ")");
            }
            if (deleteAfter) {
                if (!local.toJavaFile().delete()) {
                    throw new Exception("Failed to delete old local file " + local.getPath());
                } else {
                    updateMediaDatabase(local, MediaUpdateType.REMOVE);
                }
            }
        }
        return dest;
    }

    private void wipeDirectory(File dir, final SftpClient.CompletionCallback callback) throws Exception {
        List<File> contents = dir.listFilesSync(true);
        if (contents != null) {
            for (File fi : contents) {
                if (fi.isDirectory()) {
                    wipeDirectory(fi, null);
                } else if (!fi.deleteSync()) {
                    if (callback != null) callback.onError(new Exception("Unknown error"));
                    else throw new Exception("Failed to delete " + fi.getPath());
                    break;
                } else {
                    updateMediaDatabase(fi, MediaUpdateType.REMOVE);
                }
            }
        }
        if (!dir.deleteSync()) {
            if (callback != null) callback.onError(new Exception("Unknown error"));
            else throw new Exception("Failed to delete " + dir.getPath());
            return;
        }
        if (callback != null) callback.onComplete();
    }

    @Override
    public void delete(final SftpClient.CompletionCallback callback) {
        if (requiresRoot()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        runAsRoot("rm -rf \"" + getPath() + "\"");
                        getContext().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) callback.onComplete();
                            }
                        });
                    } catch (final Exception e) {
                        e.printStackTrace();
                        getContext().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Utils.showErrorDialog(getContext(), R.string.failed_delete_file, e);
                                if (callback != null) callback.onError(null);
                            }
                        });
                    }
                }
            }).start();
        } else {
            java.io.File mFile = new java.io.File(getPath());
            if (mFile.isDirectory()) {
                try {
                    wipeDirectory(this, callback);
                } catch (Exception e) {
                    // This will not happen since a callback is passed
                }
            } else if (deleteSync()) {
                if (callback != null) callback.onComplete();
            } else {
                Utils.showErrorDialog(getContext(), R.string.failed_delete_file, new Exception("Unknown error"));
                if (callback != null) callback.onError(null);
            }
        }
    }

    @Override
    public boolean deleteSync() {
        boolean val = new java.io.File(getPath()).delete();
        updateMediaDatabase(this, MediaUpdateType.REMOVE);
        return val;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        java.io.File mFile = new java.io.File(getPath());
        try {
            return mFile.getCanonicalFile().isDirectory();
        } catch (IOException e) {
            e.printStackTrace();
            return mFile.isDirectory();
        }
    }

    @Override
    public void exists(final BooleanCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean exists = existsSync();
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onComplete(exists);
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.showErrorDialog(getContext(), R.string.error, e);
                            if (callback != null) callback.onError(null);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public boolean existsSync() throws Exception {
        if (requiresRoot()) {
            if (Shell.SU.available()) {
                String cmd;
                if (isDirectory()) {
                    cmd = "[ -d \"" + getPath() + "\" ] && echo \"1\" || echo \"0\"";
                } else {
                    cmd = "[ -f \"" + getPath() + "\" ] && echo \"1\" || echo \"0\"";
                }
                return Integer.parseInt(runAsRoot(cmd).get(0)) == 1;
            }
        }
        java.io.File mFile = new java.io.File(getPath());
        return mFile.exists() && isDirectory() == mFile.isDirectory();
    }

    @Override
    public long length() {
        return new java.io.File(getPath()).length();
    }

    @Override
    public void listFiles(final boolean includeHidden, final ArrayCallback callback) {
        listFiles(includeHidden, null, callback);
    }

    @Override
    public void listFiles(final boolean includeHidden, final FileFilter filter, final ArrayCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<File> results = listFilesSync(includeHidden, filter);
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onComplete(results != null ? results.toArray(new File[results.size()]) : null);
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public long lastModified() {
        return new java.io.File(getPath()).lastModified();
    }

    @Override
    public List<File> listFilesSync(boolean includeHidden, FileFilter filter) throws Exception {
        List<File> results = new ArrayList<File>();
        if (requiresRoot()) {
            if (Shell.SU.available()) {
                List<String> response = runAsRoot("ls -l \"" + getPath() + "\"");
                return LsParser.parse(getContext(), getPath(), response, filter, includeHidden).getFiles();
            }
        }
        java.io.File[] list;
        if (filter != null) list = new java.io.File(getPath()).listFiles();
        else list = new java.io.File(getPath()).listFiles();
        if (list == null || list.length == 0) return new ArrayList<File>();
        for (java.io.File local : list) {
            if (!includeHidden && (local.isHidden() || local.getName().startsWith(".")))
                continue;
            LocalFile file = new LocalFile(getContext(), local);
            if (filter != null) {
                if (filter.accept(file)) {
                    file.isSearchResult = true;
                    results.add(file);
                }
            } else results.add(file);
        }
        return results;
    }

    @Override
    public File getParent() {
        if (getPath().contains("/")) {
            if (getPath().equals("/")) return null;
            String str = getPath().substring(0, getPath().lastIndexOf('/'));
            if (str.trim().isEmpty()) str = "/";
            return new LocalFile(getContext(), str);
        } else return null;
    }

    private List<String> runAsRoot(String command) throws Exception {
        Log.v("Cabinet-SU", command);
        boolean suAvailable = Shell.SU.available();
        if (!suAvailable)
            throw new Exception(getContext().getString(R.string.superuser_not_available));
        return Shell.SU.run(new String[]{
                "mount -o remount,rw /",
                command
        });
    }
}