package com.faizi.tvchannels;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class Reachability {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    /**
     * Quick liveness probe. HTTPS uses the process-wide TLS factory from TlsSetup.
     * For .m3u8 we require a real playlist body; otherwise a 2xx is enough.
     */
    public static boolean check(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", BROWSER_UA);
            c.setConnectTimeout(7000);
            c.setReadTimeout(7000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return false;

            boolean isHls = url.contains(".m3u8");
            if (!isHls) return true;

            InputStream in = c.getInputStream();
            byte[] buf = new byte[1024];
            int n = in.read(buf);
            in.close();
            if (n <= 0) return false;
            String head = new String(buf, 0, n, "UTF-8");
            return head.contains("#EXTM3U");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private Reachability() {}
}
