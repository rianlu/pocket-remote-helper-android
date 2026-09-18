package com.pocketremote.helper.store;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** API 24+ 安装 APK 用的 content:// 授权，不依赖 AndroidX FileProvider。 */
public final class FileShareProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (getContext() == null) {
            throw new FileNotFoundException();
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith("/")) {
            throw new FileNotFoundException();
        }
        String rel = path.startsWith("/") ? path.substring(1) : path;
        int slash = rel.indexOf('/');
        if (slash <= 0) {
            throw new FileNotFoundException();
        }
        String dir = rel.substring(0, slash);
        String name = rel.substring(slash + 1);
        File file = new TransferStore().resolve(dir, name);
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException(path);
        }
        int m = ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode != null && mode.indexOf('w') >= 0) {
            m = ParcelFileDescriptor.MODE_READ_WRITE;
        }
        return ParcelFileDescriptor.open(file, m);
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String s, String[] a) {
        return 0;
    }
}
