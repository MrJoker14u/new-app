package com.dk.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Wallpaper ek hi baar decode hota hai (RGB_565, screen size tak) — RAM bachane ke liye. */
final class Wallpaper {
    private Wallpaper() {}

    static File file(Context c) { return new File(c.getFilesDir(), "wallpaper.jpg"); }

    /** adb push se image yahan rakho: Android/data/com.dk.tvlauncher/files/wallpaper_import.jpg */
    static File importFile(Context c) {
        File d = c.getExternalFilesDir(null);
        return d == null ? null : new File(d, "wallpaper_import.jpg");
    }

    private static int sample(int w, int h, int tw, int th) {
        int s = 1;
        while (w / (s * 2) >= tw && h / (s * 2) >= th) s *= 2;
        return s;
    }

    static Bitmap load(Context c, int tw, int th) {
        File f = file(c);
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0) return null;
            o.inSampleSize = sample(o.outWidth, o.outHeight, tw, th);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean saveFrom(Context c, InputStream in) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                total += n;
                if (total > 25_000_000) return false;
            }
            byte[] data = bo.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0) return false;
            o.inSampleSize = sample(o.outWidth, o.outHeight, 1920, 1080);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (b == null) return false;
            File tmp = new File(c.getFilesDir(), "wallpaper.tmp");
            FileOutputStream fo = new FileOutputStream(tmp);
            b.compress(Bitmap.CompressFormat.JPEG, 92, fo);
            fo.close();
            b.recycle();
            return tmp.renameTo(file(c));
        } catch (Throwable t) {
            return false;
        }
    }
}
