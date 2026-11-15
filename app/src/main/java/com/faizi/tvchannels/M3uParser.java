package com.faizi.tvchannels;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class M3uParser {

    /** Parses an M3U/M3U8 playlist. #EXTINF gives name + optional group-title. */
    public static List<Channel> parse(BufferedReader r) throws IOException {
        List<Channel> out = new ArrayList<>();
        String line;
        String name = null;
        String category = null;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                name = (comma >= 0 && comma + 1 < line.length())
                        ? line.substring(comma + 1).trim() : "Channel";
                category = primaryCategory(attr(line, "group-title"));
            } else if (line.startsWith("#")) {
                // #EXTM3U, #EXTVLCOPT, comments — ignored
            } else {
                out.add(new Channel(name != null ? name : line, line, category));
                name = null;
                category = null;
            }
        }
        return out;
    }

    /** Extracts attr="value" from an #EXTINF line. */
    private static String attr(String line, String key) {
        String needle = key + "=\"";
        int i = line.indexOf(needle);
        if (i < 0) return null;
        int start = i + needle.length();
        int end = line.indexOf('"', start);
        if (end < 0) return null;
        return line.substring(start, end);
    }

    /** "Animation;Kids" -> "Kids"? No — keep first token, title-cased lightly. */
    private static String primaryCategory(String raw) {
        if (raw == null) return "Other";
        String c = raw.trim();
        int semi = c.indexOf(';');
        if (semi >= 0) c = c.substring(0, semi).trim();
        return c.length() == 0 ? "Other" : c;
    }

    private M3uParser() {}
}
