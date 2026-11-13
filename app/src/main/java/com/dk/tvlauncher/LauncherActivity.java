package com.dk.tvlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single-activity, programmatic UI (no XML layouts, no libraries) — 1 GB RAM TV ke liye.
 * Compact = 1 row, Expanded = 2 rows. Toggle: MENU key, ya DOWN (compact me) / UP (top row se).
 */
public class LauncherActivity extends Activity {

    private static final class Entry {
        String key;
        ComponentName cn;
        String label;
        Bitmap icon;
        boolean settings;
    }

    private Prefs prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> iconCache = new LruCache<>(128);
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final ArrayList<View> tiles = new ArrayList<>();

    private ImageView wallpaperView;
    private TextView dateView, clockView;
    private HorizontalScrollView scroller;
    private LinearLayout dock, row1, row2;

    private int iconPx, tileW, screenW, screenH;
    private boolean expanded, destroyed, lastDefaultExpanded;
    private int loadGen;
    private long shownWpVer = -1;
    private Bitmap settingsIcon;

    private final Date now = new Date();
    private SimpleDateFormat timeFmt, dateFmt;

    private final Runnable reloadRunnable = new Runnable() {
        @Override public void run() { reload(); }
    };

    private final Runnable applyLayout = new Runnable() {
        @Override public void run() {
            layoutTiles();
            scroller.setTranslationY(dp(10));
            scroller.animate().alpha(1f).translationY(0f).setDuration(140).start();
        }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            iconCache.evictAll();
            ui.removeCallbacks(reloadRunnable);
            ui.postDelayed(reloadRunnable, 500);
        }
    };

    // ACTION_TIME_TICK system har minute bhejta hai — koi polling/timer nahi.
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            rebuildFormats();
            updateClock();
        }
    };
    private boolean timeReceiverOn;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        iconPx = dp(64);
        tileW = dp(80);
        expanded = lastDefaultExpanded = prefs.expanded();

        buildUi();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        reload();
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF2B3A55, 0xFF7A5C7A, 0xFFD98E73}));
        root.addView(wallpaperView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33000000, 0x1A000000, 0xAA000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Date + clock
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);

        dateView = new TextView(this);
        dateView.setTextColor(0xE6FFFFFF);
        dateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenH * 0.036f);
        dateView.setSingleLine(true);

        clockView = new TextView(this);
        clockView.setTextColor(0xD9FFFFFF);
        clockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenH * 0.27f);
        clockView.setIncludeFontPadding(false);
        clockView.setSingleLine(true);

        head.addView(dateView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        head.addView(clockView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hp.topMargin = (int) (screenH * 0.07f);
        root.addView(head, hp);

        // Dock
        scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(dp(16), dp(12), dp(16), dp(8));
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(0xB3101114);
        dockBg.setCornerRadius(dp(24));
        dockBg.setStroke(dp(1), 0x22FFFFFF);
        scroller.setBackground(dockBg);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setClipChildren(false);
        row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setClipChildren(false);
        row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setClipChildren(false);
        scroller.addView(dock, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams dp0 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dp0.setMargins(dp(48), 0, dp(48), dp(32));
        root.addView(scroller, dp0);

        setContentView(root);
        rebuildFormats();
        updateClock();
    }

    private void rebuildFormats() {
        Locale l = Locale.getDefault();
        timeFmt = new SimpleDateFormat(prefs.clock24() ? "HH:mm" : "h:mm", l);
        dateFmt = new SimpleDateFormat("EEEE, d MMMM", l);
    }

    private void updateClock() {
        now.setTime(System.currentTimeMillis());
        clockView.setText(timeFmt.format(now));
        dateView.setText(dateFmt.format(now));
    }

    private View makeTile(final Entry e) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setLayoutParams(new LinearLayout.LayoutParams(tileW, ViewGroup.LayoutParams.WRAP_CONTENT));
        tile.setClipChildren(false);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setTag(e.key);

        final FrameLayout box = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(e.icon);
        box.addView(iv, new FrameLayout.LayoutParams(iconPx, iconPx));

        final View ring = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(3), Color.WHITE);
        g.setCornerRadius(iconPx * 0.22f);
        ring.setBackground(g);
        ring.setAlpha(0f);
        box.addView(ring, new FrameLayout.LayoutParams(iconPx, iconPx));
        tile.addView(box, new LinearLayout.LayoutParams(iconPx, iconPx));

        final TextView label = new TextView(this);
        label.setText(e.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                tileW, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        tile.addView(label, lp);

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                float s = has ? 1.12f : 1f;
                box.animate().scaleX(s).scaleY(s).setDuration(120).start();
                ring.animate().alpha(has ? 1f : 0f).setDuration(120).start();
                label.animate().alpha(has ? 1f : 0f).setDuration(120).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launch(e); }
        });
        if (!e.settings) {
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { pin(e); return true; }
            });
        }
        return tile;
    }

    /** Sirf tiles ko rows me re-arrange karta hai — views dobara nahi bante. */
    private void layoutTiles() {
        View f = getCurrentFocus();
        Object key = f != null ? f.getTag() : null;

        row1.removeAllViews();
        row2.removeAllViews();
        dock.removeAllViews();
        dock.addView(row1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int n = tiles.size();
        if (expanded && n > 1) {
            int first = (n + 1) / 2;
            for (int i = 0; i < n; i++) {
                if (i < first) row1.addView(tiles.get(i)); else row2.addView(tiles.get(i));
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(6);
            dock.addView(row2, p);
        } else {
            for (int i = 0; i < n; i++) row1.addView(tiles.get(i));
        }

        if (key != null) {
            for (View t : tiles) {
                if (key.equals(t.getTag())) { t.requestFocus(); break; }
            }
        }
    }

    private void setExpanded(boolean ex) {
        if (ex == expanded) return;
        expanded = ex;
        ui.removeCallbacks(applyLayout);
        scroller.animate().cancel();
        scroller.animate().alpha(0f).setDuration(70).start();
        ui.postDelayed(applyLayout, 80);
    }

    // ---------------------------------------------------------------- Apps

    private void reload() {
        if (destroyed) return;
        final int gen = ++loadGen;
        io.execute(new Runnable() {
            @Override public void run() {
                final ArrayList<Entry> list = safeQuery();
                if (list == null) return;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!destroyed && gen == loadGen) applyEntries(list);
                    }
                });
            }
        });
    }

    private ArrayList<Entry> safeQuery() {
        try { return queryApps(); } catch (Throwable t) { return null; }
    }

    private ArrayList<Entry> queryApps() {
        PackageManager pm = getPackageManager();
        LinkedHashMap<String, ResolveInfo> map = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || ai.packageName.equals(getPackageName())) continue;
                if (!map.containsKey(ai.packageName)) map.put(ai.packageName, ri);
            }
        }
        ArrayList<Entry> out = new ArrayList<>();
        for (ResolveInfo ri : map.values()) {
            Entry e = new Entry();
            e.key = ri.activityInfo.packageName;
            e.cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            e.label = String.valueOf(ri.loadLabel(pm));
            Bitmap ic = iconCache.get(e.key);
            if (ic == null) {
                try { ic = toIcon(ri.loadIcon(pm)); } catch (Throwable t) { ic = null; }
                if (ic != null) iconCache.put(e.key, ic);
            }
            if (ic == null) continue;   // icon load fail -> skip gracefully
            e.icon = ic;
            out.add(e);
        }
        final List<String> pins = prefs.pins();
        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                int pa = pins.indexOf(a.key), pb = pins.indexOf(b.key);
                if (pa >= 0 && pb >= 0) return pa - pb;
                if (pa >= 0) return -1;
                if (pb >= 0) return 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    /** Icon ko ek hi size ke rounded square bitmap me convert karta hai (anti-aliased). */
    private Bitmap toIcon(Drawable d) {
        int s = iconPx;
        Bitmap raw = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(raw);
        c.drawColor(0xFF24262B);
        d.setBounds(0, 0, s, s);
        d.draw(c);
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawRoundRect(new RectF(0, 0, s, s), s * 0.22f, s * 0.22f, p);
        raw.recycle();
        return out;
    }

    private Bitmap settingsIcon() {
        if (settingsIcon != null) return settingsIcon;
        int s = iconPx;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF33353A);
        c.drawRoundRect(new RectF(0, 0, s, s), s * 0.22f, s * 0.22f, p);
        try {
            Drawable g = getResources().getDrawable(android.R.drawable.ic_menu_preferences, getTheme()).mutate();
            g.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            int pad = (int) (s * 0.24f);
            g.setBounds(pad, pad, s - pad, s - pad);
            g.draw(c);
        } catch (Throwable ignored) { }
        settingsIcon = b;
        return b;
    }

    private void applyEntries(ArrayList<Entry> list) {
        entries.clear();
        entries.addAll(list);
        Entry s = new Entry();
        s.key = "::settings";
        s.label = "Settings";
        s.settings = true;
        s.icon = settingsIcon();
        entries.add(s);

        tiles.clear();
        for (Entry e : entries) tiles.add(makeTile(e));
        layoutTiles();
        if (getCurrentFocus() == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    private void launch(Entry e) {
        if (e.settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).setComponent(e.cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "App open nahi ho paaya", Toast.LENGTH_SHORT).show();
            reload();
        }
    }

    private void pin(Entry e) {
        prefs.pin(e.key);
        Toast.makeText(this, e.label + " ko pehle position par rakha", Toast.LENGTH_SHORT).show();
        reload();
    }

    // ---------------------------------------------------------------- Keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
            int k = ev.getKeyCode();
            if (k == KeyEvent.KEYCODE_MENU) {
                setExpanded(!expanded);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && !expanded && tiles.size() > 1
                    && getCurrentFocus() != null) {
                setExpanded(true);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP && expanded) {
                View f = getCurrentFocus();
                if (f != null && f.getParent() == row1) {
                    setExpanded(false);
                    return true;
                }
            }
            if (k == KeyEvent.KEYCODE_BACK && expanded) {
                setExpanded(false);
                return true;
            }
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override
    public void onBackPressed() {
        // Home launcher ko BACK se band nahi karna.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // HOME dabaya: compact + first app par wapas.
        if (expanded) {
            expanded = false;
            ui.removeCallbacks(applyLayout);
            layoutTiles();
            scroller.setAlpha(1f);
        }
        scroller.scrollTo(0, 0);
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    // ---------------------------------------------------------------- Lifecycle

    @Override
    protected void onStart() {
        super.onStart();
        rebuildFormats();
        updateClock();
        if (!timeReceiverOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            registerReceiver(timeReceiver, f);
            timeReceiverOn = true;
        }
        boolean def = prefs.expanded();
        if (def != lastDefaultExpanded) {
            lastDefaultExpanded = def;
            setExpanded(def);
        }
        applyWallpaperIfChanged();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (timeReceiverOn) {
            unregisterReceiver(timeReceiver);
            timeReceiverOn = false;
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ui.removeCallbacksAndMessages(null);
        try { unregisterReceiver(pkgReceiver); } catch (Throwable ignored) { }
        io.shutdownNow();
        wallpaperView.setImageDrawable(null);
        iconCache.evictAll();
        super.onDestroy();
    }

    private void applyWallpaperIfChanged() {
        final long v = prefs.wpVer();
        if (v == shownWpVer || destroyed) return;
        shownWpVer = v;
        io.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = Wallpaper.load(LauncherActivity.this, screenW, screenH);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (!destroyed) wallpaperView.setImageBitmap(bmp); // null => default gradient
                    }
                });
            }
        });
    }
}
