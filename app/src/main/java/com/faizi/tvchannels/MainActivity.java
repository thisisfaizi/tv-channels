package com.faizi.tvchannels;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.StateSet;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    private final List<Channel> all = new ArrayList<>();
    private final List<Channel> shown = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();

    private ChannelStatus status;
    private ArrayAdapter<Channel> adapter;
    private LinearLayout tabsRow;
    private String current = "All";
    private ExecutorService probePool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TlsSetup.install(this);
        status = new ChannelStatus(this);

        loadChannels();
        buildCategories();

        LinearLayout rootV = new LinearLayout(this);
        rootV.setOrientation(LinearLayout.VERTICAL);
        rootV.setBackgroundColor(0xFF0E0E0E);

        // --- Tab bar ---
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(8, 10, 8, 10);
        hs.addView(tabsRow);
        rootV.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildTabs();

        // --- Channel list ---
        ListView list = new ListView(this);
        list.setBackgroundColor(0xFF0E0E0E);
        list.setCacheColorHint(0xFF0E0E0E);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0xFF1C1C1C));
        list.setDividerHeight(1);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0xFF2B6FFF));
        adapter = new ArrayAdapter<Channel>(this, android.R.layout.simple_list_item_1, shown) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(20);
                tv.setPadding(48, 26, 48, 26);
                return tv;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Channel c = shown.get(pos);
                Intent i = new Intent(MainActivity.this, PlayerActivity.class);
                i.putExtra(PlayerActivity.EXTRA_URL, c.url);
                i.putExtra(PlayerActivity.EXTRA_NAME, c.name);
                startActivity(i);
            }
        });
        rootV.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(rootV);
        refilter();
        // Start focus on the tab bar so the remote's Left/Right switch categories immediately.
        if (tabsRow.getChildCount() > 0) tabsRow.getChildAt(0).requestFocus();

        startProbing();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Pick up channels that just failed during playback.
        if (adapter != null) refilter();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (probePool != null) probePool.shutdownNow();
    }

    // ---- data ----

    private void loadChannels() {
        try {
            InputStream in = getAssets().open("channels.m3u");
            all.addAll(M3uParser.parse(new BufferedReader(new InputStreamReader(in))));
            in.close();
        } catch (Exception ignored) {
        }
        File ext = Environment.getExternalStorageDirectory();
        for (String f : new String[]{"cartoons.m3u", "pakistan.m3u"}) {
            File file = new File(ext, f);
            if (file.exists()) {
                try {
                    FileInputStream in = new FileInputStream(file);
                    all.addAll(M3uParser.parse(new BufferedReader(new InputStreamReader(in))));
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void buildCategories() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Channel c : all) set.add(c.category);
        List<String> sorted = new ArrayList<>(set);
        Collections.sort(sorted);
        categories.clear();
        categories.add("All");
        categories.addAll(sorted);
    }

    // ---- UI ----

    private void buildTabs() {
        tabsRow.removeAllViews();
        for (final String cat : categories) {
            final TextView tab = new TextView(this);
            tab.setText(cat);
            tab.setTextSize(17);
            tab.setPadding(36, 18, 36, 18);
            tab.setFocusable(true);
            tab.setFocusableInTouchMode(true);
            tab.setBackgroundDrawable(tabBackground());
            tab.setSelected(cat.equals(current));
            tab.setTextColor(cat.equals(current) ? 0xFFFFFFFF : 0xFFB0B0B0);
            tab.setTypeface(null, cat.equals(current) ? Typeface.BOLD : Typeface.NORMAL);
            // Switch category as soon as the tab is highlighted with the D-pad.
            tab.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) selectCategory(cat);
                }
            });
            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectCategory(cat); }
            });
            tabsRow.addView(tab);
        }
    }

    private void selectCategory(String cat) {
        boolean changed = !cat.equals(current);
        current = cat;
        for (int i = 0; i < tabsRow.getChildCount(); i++) {
            TextView t = (TextView) tabsRow.getChildAt(i);
            boolean sel = t.getText().toString().equals(current);
            t.setSelected(sel);
            t.setTextColor(sel ? 0xFFFFFFFF : 0xFFB0B0B0);
            t.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (changed) refilter();
    }

    /** focused = bright blue, selected = dark blue, otherwise transparent. */
    private StateListDrawable tabBackground() {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(0xFF2B6FFF));
        d.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(0xFF14346A));
        d.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
        return d;
    }

    private void refilter() {
        shown.clear();
        for (Channel c : all) {
            if (!current.equals("All") && !c.category.equals(current)) continue;
            if (status.get(c.url) == ChannelStatus.BAD) continue;   // hide known-dead
            shown.add(c);
        }
        adapter.notifyDataSetChanged();
    }

    // ---- background liveness probe ----

    private void startProbing() {
        final List<Channel> todo = new ArrayList<>();
        for (Channel c : all) {
            if (status.get(c.url) == ChannelStatus.UNKNOWN) todo.add(c);
        }
        if (todo.isEmpty()) { setTitle("TV Channels"); return; }

        final AtomicInteger remaining = new AtomicInteger(todo.size());
        setTitle("TV Channels  —  checking " + remaining.get() + "…");
        probePool = Executors.newFixedThreadPool(6);
        for (final Channel c : todo) {
            probePool.submit(new Runnable() {
                @Override public void run() {
                    final boolean ok = Reachability.check(c.url);
                    status.set(c.url, ok ? ChannelStatus.OK : ChannelStatus.BAD);
                    final int left = remaining.decrementAndGet();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!ok) refilter();     // drop dead ones as we find them
                            setTitle(left > 0 ? "TV Channels  —  checking " + left + "…"
                                              : "TV Channels");
                        }
                    });
                }
            });
        }
    }
}
