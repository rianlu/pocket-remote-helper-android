package com.pockettv.tv;

import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class TransferStore {
    public File root() {
        File dir = new File(Environment.getExternalStorageDirectory(), Constants.ROOT_DIR_NAME);
        new File(dir, Constants.DIR_INBOX).mkdirs();
        new File(dir, Constants.DIR_APK).mkdirs();
        return dir;
    }

    public File dir(String name) {
        if (Constants.DIR_INBOX.equals(name) || Constants.DIR_APK.equals(name)) {
            File d = new File(root(), name);
            d.mkdirs();
            return d;
        }
        return null;
    }

    public File resolve(String dirName, String fileName) {
        File d = dir(dirName);
        if (d == null || fileName == null || fileName.length() == 0) {
            return null;
        }
        if (fileName.contains("..") || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
            return null;
        }
        try {
            File f = new File(d, fileName);
            String canon = f.getCanonicalPath();
            String rootCanon = d.getCanonicalPath();
            if (!canon.startsWith(rootCanon + File.separator) && !canon.equals(rootCanon)) {
                return null;
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    public File findByName(String fileName) {
        File apk = resolve(Constants.DIR_APK, fileName);
        if (apk != null && apk.isFile()) {
            return apk;
        }
        return resolve(Constants.DIR_INBOX, fileName);
    }

    public JSONArray list() throws Exception {
        JSONArray arr = new JSONArray();
        addDir(arr, Constants.DIR_INBOX);
        addDir(arr, Constants.DIR_APK);
        return arr;
    }

    private void addDir(JSONArray arr, String dirName) throws Exception {
        File d = dir(dirName);
        File[] files = d.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (!f.isFile()) {
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("name", f.getName());
            o.put("dir", dirName);
            o.put("size", f.length());
            o.put("mtime", f.lastModified());
            arr.put(o);
        }
    }

    public void save(File dest, InputStream in, long length) throws Exception {
        File tmp = new File(dest.getAbsolutePath() + ".part");
        OutputStream out = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[8192];
            long left = length < 0 ? Long.MAX_VALUE : length;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min((long) buf.length, left));
                if (n < 0) {
                    break;
                }
                out.write(buf, 0, n);
                left -= n;
            }
        } finally {
            out.close();
        }
        if (dest.exists()) {
            dest.delete();
        }
        if (!tmp.renameTo(dest)) {
            copyFile(tmp, dest);
            tmp.delete();
        }
    }

    public void copyTo(File src, OutputStream out) throws Exception {
        InputStream in = new FileInputStream(src);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
    }

    private void copyFile(File from, File to) throws Exception {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream out = new FileOutputStream(to);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
