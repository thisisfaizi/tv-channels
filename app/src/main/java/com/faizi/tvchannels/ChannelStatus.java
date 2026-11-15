package com.faizi.tvchannels;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers which stream URLs work ("ok") or don't ("bad"), with timestamps,
 * so dead channels can be auto-hidden and re-checked occasionally.
 */
public class ChannelStatus {

    public static final int UNKNOWN = -1;
    public static final int BAD = 0;
    public static final int OK = 1;

    // Re-probe a "bad" channel after this long (it may come back); re-probe "ok" less often.
    private static final long BAD_TTL = 6L * 3600 * 1000;      // 6 hours
    private static final long OK_TTL = 24L * 3600 * 1000;      // 1 day

    private final SharedPreferences prefs;

    public ChannelStatus(Context ctx) {
        prefs = ctx.getSharedPreferences("channel_status", Context.MODE_PRIVATE);
    }

    private static String key(String url) {
        return "s_" + Integer.toHexString(url.hashCode());
    }

    /** Returns OK/BAD/UNKNOWN, treating stale entries as UNKNOWN (needs re-check). */
    public int get(String url) {
        String v = prefs.getString(key(url), null);
        if (v == null) return UNKNOWN;
        int sep = v.indexOf(':');
        if (sep < 0) return UNKNOWN;
        try {
            int state = Integer.parseInt(v.substring(0, sep));
            long when = Long.parseLong(v.substring(sep + 1));
            long age = System.currentTimeMillis() - when;
            if (state == BAD && age > BAD_TTL) return UNKNOWN;
            if (state == OK && age > OK_TTL) return UNKNOWN;
            return state;
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    public void set(String url, int state) {
        prefs.edit()
                .putString(key(url), state + ":" + System.currentTimeMillis())
                .apply();
    }

    public void setOk(String url) { set(url, OK); }
    public void setBad(String url) { set(url, BAD); }
}
