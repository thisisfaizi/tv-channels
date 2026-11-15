package com.faizi.tvchannels;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;

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
    private String name;
    private DefaultTrackSelector trackSelector;

    private final Handler handler = new Handler();
    private boolean everReady;   // stream actually rendered at least once
    private boolean done;        // we've decided to give up and leave
    private int retries;
    private static final int MAX_RETRIES = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        statusStore = new ChannelStatus(this);
        url = getIntent().getStringExtra(EXTRA_URL);
        name = getIntent().getStringExtra(EXTRA_NAME);

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

        // Retry transient segment/load errors several times before surfacing a fatal error.
        HlsMediaSource source = new HlsMediaSource.Factory(http)
                .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6))
                .createMediaSource(MediaItem.fromUri(url));

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    everReady = true;
                    retries = 0;                               // recovered cleanly
                    status.setVisibility(View.GONE);
                    if (url != null) statusStore.setOk(url);   // confirmed playable
                } else if (state == Player.STATE_BUFFERING) {
                    status.setVisibility(View.VISIBLE);
                    status.setText("Buffering ...");
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                handlePlayerError(error);
            }
        });

        player.setMediaSource(source);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /**
     * A playback error here does NOT mean the channel is dead. On a weak/variable
     * link the buffer drains and ExoPlayer raises a transient load error — that is a
     * bandwidth problem, not a broken stream. So we:
     *   - retry (re-prepare) several times with a short backoff,
     *   - only mark a channel BAD (auto-hide) when it's DEFINITIVELY unplayable
     *     (bad HTTP status / not found / unsupported) AND it never started,
     *   - never hide a channel that already played, or one that only timed out.
     */
    private void handlePlayerError(PlaybackException error) {
        if (done || player == null) return;
        int code = error.errorCode;
        Log.e("NDV", "player error " + error.getErrorCodeName() + " (" + code + ")", error);

        boolean definitive =
                code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
             || code == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
             || code == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
             || code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
             || code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
             || code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
             || code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
             || code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
             || code == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED;

        // Genuinely unplayable and never got going -> hide it from the list.
        if (definitive && !everReady) {
            giveUp("Can't play this channel\n(" + error.getErrorCodeName() + ").\nIt will be hidden.", true);
            return;
        }

        // Otherwise it's transient / bandwidth -> try to recover.
        if (retries < MAX_RETRIES) {
            retries++;
            status.setVisibility(View.VISIBLE);
            status.setText("Reconnecting… (" + retries + "/" + MAX_RETRIES + ")");
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (done || player == null) return;
                    player.seekToDefaultPosition();   // jump to live edge
                    player.prepare();
                    player.setPlayWhenReady(true);
                }
            }, 1500);
            return;
        }

        // Out of retries. If it played before, it's a weak link — keep it in the list.
        if (everReady) {
            giveUp("Weak connection — playback stopped.\nTry again, pick Low quality, or use Ethernet.", false);
        } else {
            giveUp("Can't reach this channel\n(" + error.getErrorCodeName() + ").", false);
        }
    }

    private void giveUp(String msg, boolean hide) {
        done = true;
        if (hide && url != null) statusStore.setBad(url);
        status.setVisibility(View.VISIBLE);
        status.setText(msg);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        handler.postDelayed(new Runnable() {
            @Override public void run() { finish(); }
        }, 3000);
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
        handler.removeCallbacksAndMessages(null);   // cancel pending retry/finish
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
