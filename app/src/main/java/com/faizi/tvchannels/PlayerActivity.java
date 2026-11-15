package com.faizi.tvchannels;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";

    // Masquerade as a desktop browser so UA-filtered CDNs (e.g. Cartoon Network) serve us.
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    private ExoPlayer player;
    private TextView status;
    private ChannelStatus statusStore;
    private String url;
    private DefaultTrackSelector trackSelector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        statusStore = new ChannelStatus(this);
        url = getIntent().getStringExtra(EXTRA_URL);
        String name = getIntent().getStringExtra(EXTRA_NAME);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        SurfaceView surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setText("Loading " + (name != null ? name : "") + " ...\n"
                + "(Press ▲ Up for quality: " + Quality.NAMES[Quality.get(this)] + ")");
        root.addView(status, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        setContentView(root);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(BROWSER_UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000);

        trackSelector = new DefaultTrackSelector(this);
        Quality.apply(trackSelector, Quality.get(this));

        // Bigger buffer runway = fewer, shorter stalls on weak/variable links.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        30_000,  // min buffer
                        60_000,  // max buffer
                        3_000,   // buffer before playback starts
                        6_000)   // buffer required after a rebuffer
                .build();

        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();
        player.setVideoSurfaceView(surface);

        HlsMediaSource source = new HlsMediaSource.Factory(http)
                .createMediaSource(MediaItem.fromUri(url));

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    status.setVisibility(View.GONE);
                    if (url != null) statusStore.setOk(url);   // confirmed playable
                } else if (state == Player.STATE_BUFFERING) {
                    status.setVisibility(View.VISIBLE);
                    status.setText("Buffering ...");
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (url != null) statusStore.setBad(url);       // remember failure -> auto-hidden
                String msg = "Can't play this channel.\nIt will be hidden from the list.";
                status.setVisibility(View.VISIBLE);
                status.setText(msg);
                Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_LONG).show();
                status.postDelayed(new Runnable() {
                    @Override public void run() { finish(); }
                }, 2500);
            }
        });

        player.setMediaSource(source);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_INFO) {
            int q = Quality.cycle(this);
            if (trackSelector != null) Quality.apply(trackSelector, q);
            Toast.makeText(this, "Quality: " + Quality.NAMES[q], Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
