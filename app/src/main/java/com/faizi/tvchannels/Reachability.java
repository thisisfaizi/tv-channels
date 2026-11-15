package com.faizi.tvchannels;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class Reachability {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    /**
     * Liveness probe. Returns a tri-state matching {@link ChannelStatus}:
     *   OK      — reachable and (for .m3u8) a real playlist,
     *   BAD     — the server DEFINITIVELY rejected it (4xx/5xx, or wrong content),
     *   UNKNOWN — we couldn't tell (timeout / connection error on a weak link).
     *
     * Crucially, timeouts/IO errors return UNKNOWN, not BAD, so a slow or lossy
     * network never auto-hides channels that are actually fine — they're just
     * re-checked next time. HTTPS uses the process-wide TLS factory from TlsSetup.
     */
    public static int check(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", BROWSER_UA);
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code >= 400) return ChannelStatus.BAD;         // 403/404/410/5xx -> really dead
            if (code < 200 || code >= 400) return ChannelStatus.UNKNOWN;

            if (!url.contains(".m3u8")) return ChannelStatus.OK;

            InputStream in = c.getInputStream();
            byte[] buf = new byte[1024];
            int n = in.read(buf);
            in.close();
            if (n <= 0) return ChannelStatus.UNKNOWN;          // couldn't read body -> don't condemn
            String head = new String(buf, 0, n, "UTF-8");
            return head.contains("#EXTM3U") ? ChannelStatus.OK : ChannelStatus.BAD;
        } catch (java.net.UnknownHostException e) {
            return ChannelStatus.UNKNOWN;                      // flaky DNS on weak link -> retry later
        } catch (Exception e) {
            return ChannelStatus.UNKNOWN;                      // timeout / IO -> retry later
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private Reachability() {}
}
