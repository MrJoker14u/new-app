package com.dk.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    boolean clock24() { return sp.getBoolean("clock24", false); }
    void setClock24(boolean v) { sp.edit().putBoolean("clock24", v).apply(); }

    boolean expanded() { return sp.getBoolean("expanded", false); }
    void setExpanded(boolean v) { sp.edit().putBoolean("expanded", v).apply(); }

    long wpVer() { return sp.getLong("wp_ver", 0L); }
    void bumpWp() { sp.edit().putLong("wp_ver", System.currentTimeMillis()).apply(); }

    List<String> pins() {
        ArrayList<String> l = new ArrayList<>();
        String s = sp.getString("pins", "");
        if (s != null && !s.isEmpty()) {
            for (String p : s.split(",")) if (!p.isEmpty()) l.add(p);
        }
        return l;
    }

    void pin(String key) {
        List<String> l = pins();
        l.remove(key);
        l.add(0, key);
        StringBuilder sb = new StringBuilder();
        for (String k : l) { if (sb.length() > 0) sb.append(','); sb.append(k); }
        sp.edit().putString("pins", sb.toString()).apply();
    }

    void clearPins() { sp.edit().remove("pins").apply(); }

    void clearAll() {
        sp.edit().clear().putLong("wp_ver", System.currentTimeMillis()).apply();
    }
}
