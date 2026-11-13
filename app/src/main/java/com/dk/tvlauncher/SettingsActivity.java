package com.dk.tvlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/** Minimal settings: wallpaper, layout, clock format, order reset, system settings, about, restore. */
public class SettingsActivity extends Activity {

    private static final int REQ_PICK = 1;
    private Prefs prefs;
    private TextView vLayout, vClock;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        Wallpaper.importFile(this); // folder bana deta hai (adb push ke liye)

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0F1013);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(520),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(panel, pp);

        TextView title = new TextView(this);
        title.setText("Launcher Settings");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        title.setPadding(dp(20), 0, 0, dp(16));
        panel.addView(title);

        View first = addRow(panel, "Change wallpaper", "", new View.OnClickListener() {
            @Override public void onClick(View v) { chooseWallpaper(); }
        }).first;
        vLayout = addRow(panel, "Default layout", "", new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.setExpanded(!prefs.expanded()); refresh(); }
        }).value;
        vClock = addRow(panel, "Clock format", "", new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.setClock24(!prefs.clock24()); refresh(); }
        }).value;
        addRow(panel, "Reset app order", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.clearPins();
                toast("App order reset ho gaya (A-Z)");
            }
        });
        addRow(panel, "Android system settings", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (Throwable t) { toast("System settings open nahi hui"); }
            }
        });
        addRow(panel, "Launcher info", "v1.0", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Runtime rt = Runtime.getRuntime();
                long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                toast("TV Launcher v1.0 | heap use: " + usedMb + " MB");
            }
        });
        addRow(panel, "Restore defaults", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.clearAll();
                File wp = Wallpaper.file(SettingsActivity.this);
                if (wp.exists()) wp.delete();
                refresh();
                toast("Default settings restore ho gayi");
            }
        });

        setContentView(root);
        refresh();
        first.requestFocus();
    }

    private void refresh() {
        vLayout.setText(prefs.expanded() ? "Expanded" : "Compact");
        vClock.setText(prefs.clock24() ? "24-hour" : "12-hour");
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private static final class Row { View first; TextView value; }

    private Row addRow(LinearLayout list, String title, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        row.setFocusable(true);
        row.setClickable(true);

        StateListDrawable sd = new StateListDrawable();
        GradientDrawable f = new GradientDrawable();
        f.setColor(0x33FFFFFF);
        f.setCornerRadius(dp(12));
        f.setStroke(dp(2), 0x88FFFFFF);
        sd.addState(new int[]{android.R.attr.state_focused}, f);
        sd.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        row.setBackground(sd);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(0xFFB8BCC4);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);

        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(click);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        list.addView(row, lp);

        Row r = new Row();
        r.first = row;
        r.value = v;
        return r;
    }

    // ---- Wallpaper

    private void chooseWallpaper() {
        final File imp = Wallpaper.importFile(this);
        if (imp != null && imp.exists()) {
            runSave(new Saver() {
                @Override public boolean save() throws Exception {
                    try (InputStream in = new FileInputStream(imp)) {
                        boolean ok = Wallpaper.saveFrom(SettingsActivity.this, in);
                        if (ok) imp.delete();
                        return ok;
                    }
                }
            });
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(i, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            toast("File picker nahi mila. PC se adb push karo:\n" + (imp != null ? imp.getAbsolutePath() : "-")
                    + "\nphir yahan dobara OK dabao.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri u = data.getData();
            runSave(new Saver() {
                @Override public boolean save() throws Exception {
                    try (InputStream in = getContentResolver().openInputStream(u)) {
                        return in != null && Wallpaper.saveFrom(SettingsActivity.this, in);
                    }
                }
            });
        }
    }

    private interface Saver { boolean save() throws Exception; }

    private void runSave(final Saver s) {
        toast("Wallpaper set ho raha hai...");
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok;
                try { ok = s.save(); } catch (Throwable t) { ok = false; }
                final boolean r = ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (r) prefs.bumpWp();
                        toast(r ? "Wallpaper set ho gaya" : "Wallpaper set nahi ho paaya");
                    }
                });
            }
        }).start();
    }
}
