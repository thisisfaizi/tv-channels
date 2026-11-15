package com.faizi.tvchannels;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

@OptIn(markerClass = UnstableApi.class)
public final class Quality {

    // 0 = Low, 1 = Auto, 2 = High
    public static final String[] NAMES = {"Low (saves data)", "Auto", "High (HD)"};

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    public static int get(Context c) {
        return prefs(c).getInt("quality", 0);   // default Low for slow links
    }

    public static void set(Context c, int q) {
        prefs(c).edit().putInt("quality", q).apply();
    }

    public static int cycle(Context c) {
        int q = (get(c) + 1) % NAMES.length;
        set(c, q);
        return q;
    }

    /** Applies the quality level to a live track selector (re-selects without restarting). */
    public static void apply(DefaultTrackSelector ts, int q) {
        DefaultTrackSelector.Parameters.Builder b = ts.buildUponParameters();
        b.setForceLowestBitrate(false)
                .setForceHighestSupportedBitrate(false)
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(Integer.MAX_VALUE);
        switch (q) {
            case 0: // Low — smoothest on weak links: lowest variant, cap at 360p
                b.setForceLowestBitrate(true).setMaxVideoSize(640, 360);
                break;
            case 1: // Auto — adaptive, capped at 480p so it never overreaches
                b.setMaxVideoSize(854, 480);
                break;
            case 2: // High — unlimited
                b.setForceHighestSupportedBitrate(true);
                break;
        }
        ts.setParameters(b.build());
    }

    private Quality() {}
}
