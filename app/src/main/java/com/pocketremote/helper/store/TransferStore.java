package com.pocketremote.helper.store;

import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import com.pocketremote.helper.protocol.Constants;

/** 只允许 /sdcard/PocketRemote/inbox 与 apk，拒绝路径穿越。 */
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

    /** dir 只能是 inbox/apk；文件名不得含路径分隔或 .. */
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
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        addDir(arr, Constants.DIR_INBOX, seen);
        addDir(arr, Constants.DIR_APK, seen);
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null) {
            walkApk(primary, primary, 0, arr, seen);
        }
        File storage = new File("/storage");
        File[] vols = storage.listFiles();
        if (vols != null) {
            for (int i = 0; i < vols.length; i++) {
                File vol = vols[i];
                String n = vol.getName();
                if (!vol.isDirectory() || "emulated".equals(n) || "self".equals(n)) {
                    continue;
                }
                walkApk(vol, vol, 0, arr, seen);
            }
        }
        return arr;
    }

    /** 仅允许公共存储上的 .apk，拒绝路径穿越。path 为相对主存储，或以 / 开头的绝对路径。 */
    public File resolveInstallable(String path) {
        if (path == null || path.length() == 0 || path.contains("..")) {
            return null;
        }
        if (!path.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
            return null;
        }
        File f;
        if (path.charAt(0) == '/') {
            f = new File(path);
        } else {
            File primary = Environment.getExternalStorageDirectory();
            if (primary == null) {
                return null;
            }
            f = new File(primary, path);
        }
        try {
            if (!f.isFile()) {
                return null;
            }
            String canon = f.getCanonicalPath();
            if (!underPublicStorage(canon)) {
                return null;
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private void addDir(JSONArray arr, String dirName, java.util.Set<String> seen) throws Exception {
        File d = dir(dirName);
        File[] files = d.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (!f.isFile() || !f.getName().toLowerCase(java.util.Locale.US).endsWith(".apk")) {
                continue;
            }
            addApk(arr, f, dirName, seen);
        }
    }

    private void walkApk(File dir, File volumeRoot, int depth, JSONArray arr, java.util.Set<String> seen)
            throws Exception {
        if (depth > 6 || arr.length() >= 400) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            if (arr.length() >= 400) {
                return;
            }
            File f = files[i];
            String name = f.getName();
            if (name.startsWith(".")) {
                continue;
            }
            if (f.isDirectory()) {
                if ("data".equals(name) || "obb".equals(name)) {
                    File parent = f.getParentFile();
                    if (parent != null && "Android".equals(parent.getName())) {
                        continue;
                    }
                }
                if ("lost+found".equals(name) || "thumbnails".equalsIgnoreCase(name)) {
                    continue;
                }
                walkApk(f, volumeRoot, depth + 1, arr, seen);
                continue;
            }
            if (!name.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
                continue;
            }
            String label = folderLabel(f, volumeRoot);
            addApk(arr, f, label, seen);
        }
    }

    private void addApk(JSONArray arr, File f, String dirLabel, java.util.Set<String> seen) throws Exception {
        try {
            String canon = f.getCanonicalPath();
            if (!seen.add(canon)) {
                return;
            }
            JSONObject o = new JSONObject();
            o.put("name", f.getName());
            o.put("dir", dirLabel);
            o.put("size", f.length());
            o.put("mtime", f.lastModified());
            o.put("path", publicPath(f));
            arr.put(o);
        } catch (Exception ignored) {
        }
    }

    private String folderLabel(File f, File volumeRoot) {
        File parent = f.getParentFile();
        if (parent == null) {
            return "";
        }
        try {
            String p = parent.getCanonicalPath();
            String vol = volumeRoot.getCanonicalPath();
            if (p.equals(vol)) {
                return volumeRoot.getName();
            }
            if (p.startsWith(vol + File.separator)) {
                return p.substring(vol.length() + 1).replace(File.separatorChar, '/');
            }
        } catch (Exception ignored) {
        }
        return parent.getName();
    }

    private String publicPath(File f) throws Exception {
        String canon = f.getCanonicalPath();
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null) {
            String root = primary.getCanonicalPath();
            if (canon.equals(root)) {
                return "";
            }
            if (canon.startsWith(root + File.separator)) {
                return canon.substring(root.length() + 1).replace(File.separatorChar, '/');
            }
        }
        return canon;
    }

    private boolean underPublicStorage(String canon) throws Exception {
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null) {
            String root = primary.getCanonicalPath();
            if (canon.equals(root) || canon.startsWith(root + File.separator)) {
                return true;
            }
        }
        File storage = new File("/storage");
        String storageCanon = storage.getCanonicalPath();
        if (canon.startsWith(storageCanon + File.separator)) {
            if (canon.contains("/emulated/") || canon.contains("/self/")) {
                File p = Environment.getExternalStorageDirectory();
                if (p == null) {
                    return false;
                }
                String root = p.getCanonicalPath();
                return canon.equals(root) || canon.startsWith(root + File.separator);
            }
            return true;
        }
        return false;
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
