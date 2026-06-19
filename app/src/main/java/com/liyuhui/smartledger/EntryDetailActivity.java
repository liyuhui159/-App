package com.liyuhui.smartledger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EntryDetailActivity extends Activity {
    private static final int PRIMARY = Color.rgb(91, 95, 239);
    private static final int PRIMARY_DARK = Color.rgb(34, 36, 77);
    private static final int BG = Color.rgb(244, 247, 251);
    private static final int DANGER = Color.rgb(245, 91, 91);
    private static final int ACCENT = Color.rgb(0, 194, 168);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        root.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);

        long id = getIntent().getLongExtra("id", -1L);
        String type = getIntent().getStringExtra("type");
        String category = getIntent().getStringExtra("category");
        String account = getIntent().getStringExtra("account");
        String note = getIntent().getStringExtra("note");
        String source = getIntent().getStringExtra("source");
        double amount = getIntent().getDoubleExtra("amount", 0D);
        long time = getIntent().getLongExtra("time", System.currentTimeMillis());
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(time));

        TextView title = text("账目详情", 26, PRIMARY_DARK, true);
        root.addView(title);
        TextView money = text(("收入".equals(type) ? "+" : "-") + "¥" + String.format(Locale.CHINA, "%.2f", amount), 36, "收入".equals(type) ? ACCENT : PRIMARY, true);
        money.setGravity(Gravity.CENTER);
        money.setPadding(0, dp(18), 0, dp(18));
        root.addView(money);

        LinearLayout card = card();
        card.addView(row("收支类型", safe(type)));
        card.addView(row("分类", MainActivity.iconForCategory(category) + " " + safe(category)));
        card.addView(row("账户", safe(account)));
        card.addView(row("备注", safe(note)));
        card.addView(row("来源", safe(source)));
        card.addView(row("时间", date));
        root.addView(card);

        Button edit = button("编辑本笔账目", Color.rgb(52, 199, 89), Color.WHITE);
        edit.setOnClickListener(v -> {
            Intent i = new Intent(this, QuickEntryActivity.class);
            i.putExtra("entry_id", id);
            i.putExtra("forced_amount", amount);
            i.putExtra("forced_type", safe(type));
            i.putExtra("forced_category", safe(category));
            i.putExtra("forced_account", safe(account));
            i.putExtra("forced_note", safe(note));
            i.putExtra("forced_time", time);
            i.putExtra("source", source == null || source.trim().isEmpty() ? "手动编辑" : source);
            i.putExtra("raw_text", safe(note) + " " + safe(category) + " " + safe(account));
            startActivity(i);
            finish();
        });
        root.addView(edit, new LinearLayout.LayoutParams(-1, dp(48)));

        Button close = button("返回", PRIMARY, Color.WHITE);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, dp(10), 0, 0);
        root.addView(close, lp);
    }

    private LinearLayout row(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        r.addView(text(k, 13, Color.rgb(105, 110, 130), true));
        TextView value = text(v, 18, PRIMARY_DARK, false);
        value.setPadding(0, dp(4), 0, 0);
        r.addView(value);
        return r;
    }

    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(16), dp(16), dp(16)); c.setBackground(glassRound(Color.argb(205, 255, 255, 255), 20)); c.setElevation(dp(5)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(10), 0, dp(18)); c.setLayoutParams(lp); return c; }
    private Button button(String text, int bg, int fg) { Button b = new Button(this); b.setAllCaps(false); b.setText(text); b.setTextColor(fg); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(glassRound(bg, 16)); b.setElevation(dp(3)); return b; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s == null ? "" : s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private GradientDrawable glassRound(int color, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.argb(235, 255, 255, 255), color}); g.setCornerRadius(dp(radius)); g.setStroke(dp(1), Color.argb(130, 255, 255, 255)); return g; }
    private String safe(String s) { return s == null || s.trim().isEmpty() ? "其他" : s.trim(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
