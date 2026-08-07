package com.liyuhui.smartledger;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Launcher and AI import/settings page. The original dashboard remains available with one tap. */
public class AiImportActivity extends Activity {
    private static final int PRIMARY = Color.rgb(91, 95, 239);
    private static final int PRIMARY_DARK = Color.rgb(34, 36, 77);
    private static final int ACCENT = Color.rgb(0, 170, 145);
    private static final int DANGER = Color.rgb(220, 70, 80);
    private static final int MUTED = Color.rgb(100, 106, 128);

    private CheckBox enableAi;
    private CheckBox enableNotificationAi;
    private EditText endpoint;
    private EditText model;
    private EditText apiKey;
    private EditText confidence;
    private EditText rawText;
    private LinearLayout resultBox;
    private TextView status;
    private Button parseButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadSettings();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        root.setBackgroundColor(Color.rgb(244, 247, 251));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("灵犀 AI 记账", 28, Color.WHITE, true);
        TextView subtitle = text("大模型判断 · 本地规则兜底 · 密钥加密保存", 13, Color.argb(230, 255, 255, 255), false);
        LinearLayout header = card(Color.rgb(91, 95, 239));
        header.setBackground(gradient(PRIMARY, Color.rgb(118, 82, 225)));
        header.addView(title);
        header.addView(subtitle);
        Button dashboard = button("打开完整账本与统计看板", Color.WHITE, PRIMARY);
        dashboard.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        header.addView(dashboard, params(-1, dp(48), 12));
        root.addView(header);

        root.addView(section("AI 智能导入"));
        LinearLayout importCard = card(Color.WHITE);
        importCard.addView(text("粘贴微信、支付宝、银行卡通知或混合账单文本。AI 会判断是否是真实交易，并提取金额、收支、分类、账户、日期和备注。", 14, MUTED, false));
        rawText = input("例如：支付宝支付成功，美团外卖 32.80 元", false);
        rawText.setGravity(Gravity.TOP);
        rawText.setMinLines(7);
        importCard.addView(rawText, params(-1, dp(180), 10));
        parseButton = button("AI 智能判断", PRIMARY, Color.WHITE);
        parseButton.setOnClickListener(v -> parseLedgerText());
        importCard.addView(parseButton, params(-1, dp(50), 10));
        status = text("尚未解析", 13, MUTED, false);
        importCard.addView(status);
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        importCard.addView(resultBox);
        root.addView(importCard);

        root.addView(section("API 设置"));
        LinearLayout settings = card(Color.WHITE);
        enableAi = check("启用 AI 智能判断");
        enableNotificationAi = check("支付通知优先使用 AI 判断（失败自动回退本地规则）");
        endpoint = input("完整接口地址", false);
        model = input("模型名称", false);
        apiKey = input("API Key；留空表示保留已保存密钥", false);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confidence = input("最低置信度，例如 0.62", true);

        settings.addView(enableAi);
        settings.addView(enableNotificationAi);
        settings.addView(label("接口地址"));
        settings.addView(endpoint, params(-1, dp(54), 4));
        settings.addView(label("模型"));
        settings.addView(model, params(-1, dp(54), 4));
        settings.addView(label("API Key"));
        settings.addView(apiKey, params(-1, dp(54), 4));
        settings.addView(label("最低置信度 0.10–1.00"));
        settings.addView(confidence, params(-1, dp(54), 4));

        Button save = button("保存 API 设置", PRIMARY, Color.WHITE);
        save.setOnClickListener(v -> saveSettings());
        Button test = button("测试接口", Color.rgb(236, 238, 255), PRIMARY);
        test.setOnClickListener(v -> testConnection());
        Button clear = button("清除已保存 API Key", Color.rgb(255, 238, 240), DANGER);
        clear.setOnClickListener(v -> {
            AiLedgerClient.clearApiKey(this);
            apiKey.setText("");
            toast("已清除 API Key");
            loadSettings();
        });
        settings.addView(save, params(-1, dp(48), 10));
        settings.addView(test, params(-1, dp(48), 8));
        settings.addView(clear, params(-1, dp(48), 8));
        settings.addView(text("个人自用可在手机中填写密钥；准备公开发布时，应改为调用你自己的后端代理，避免把长期密钥交给客户端。", 12, DANGER, false));
        root.addView(settings);
    }

    private void loadSettings() {
        AiLedgerClient.Config c = AiLedgerClient.loadConfig(this);
        enableAi.setChecked(c.enabled);
        enableNotificationAi.setChecked(c.notificationEnabled);
        endpoint.setText(c.endpoint);
        model.setText(c.model);
        confidence.setText(String.format(Locale.US, "%.2f", c.minConfidence));
        apiKey.setHint(c.hasApiKey ? "已安全保存；留空表示不修改" : "尚未填写 API Key");
    }

    private void saveSettings() {
        try {
            AiLedgerClient.saveConfig(
                    this,
                    enableAi.isChecked(),
                    enableNotificationAi.isChecked(),
                    endpoint.getText().toString(),
                    model.getText().toString(),
                    parseConfidence(confidence.getText().toString()),
                    apiKey.getText().toString());
            apiKey.setText("");
            loadSettings();
            toast("AI 设置已保存");
        } catch (Exception e) {
            toast("保存失败：" + safeMessage(e));
        }
    }

    private void parseLedgerText() {
        String raw = rawText.getText().toString().trim();
        if (raw.isEmpty()) { toast("请先粘贴账单文字"); return; }
        saveSettings();
        resultBox.removeAllViews();
        parseButton.setEnabled(false);
        status.setText("正在调用 AI 判断…");
        status.setTextColor(PRIMARY);

        if (!AiLedgerClient.canUseAi(this)) {
            showLocalFallback(raw, "未配置可用 API，已使用本地规则");
            return;
        }

        AiLedgerClient.parseAsync(this, raw, new AiLedgerClient.Callback() {
            @Override public void onSuccess(List<MainActivity.Entry> entries, String modelText) {
                parseButton.setEnabled(true);
                if (entries.isEmpty()) {
                    List<MainActivity.Entry> local = MainActivity.SmartParser.parse(raw);
                    if (!local.isEmpty()) {
                        for (MainActivity.Entry e : local) e.source = "本地规则兜底";
                        renderEntries(local, "AI 未确认有效账单，本地规则识别到 " + local.size() + " 笔");
                    } else {
                        status.setText("AI 判断：没有可入账的真实交易");
                        status.setTextColor(MUTED);
                        resultBox.addView(text("该文本可能只是普通通知、余额信息、广告或不完整交易。", 14, MUTED, false));
                    }
                    return;
                }
                renderEntries(entries, "AI 已识别 " + entries.size() + " 笔，可确认后写入账本");
            }

            @Override public void onFailure(String message) {
                showLocalFallback(raw, "AI 请求失败：" + message + "；已回退本地规则");
            }
        });
    }

    private void showLocalFallback(String raw, String message) {
        parseButton.setEnabled(true);
        List<MainActivity.Entry> local = MainActivity.SmartParser.parse(raw);
        for (MainActivity.Entry e : local) e.source = "本地规则兜底";
        if (local.isEmpty()) {
            status.setText(message);
            status.setTextColor(DANGER);
            resultBox.addView(text("本地规则也未识别到账单金额。", 14, MUTED, false));
        } else {
            renderEntries(local, message + "，识别到 " + local.size() + " 笔");
        }
    }

    private void renderEntries(List<MainActivity.Entry> entries, String message) {
        resultBox.removeAllViews();
        status.setText(message);
        status.setTextColor(ACCENT);
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        for (MainActivity.Entry e : entries) {
            LinearLayout row = card(Color.rgb(250, 251, 255));
            row.addView(text(MainActivity.iconForCategory(e.category) + " " + e.category + " · " + e.account,
                    16, PRIMARY_DARK, true));
            row.addView(text(("收入".equals(e.type) ? "+" : "-") + "¥" + String.format(Locale.CHINA, "%.2f", e.amount),
                    20, "收入".equals(e.type) ? ACCENT : DANGER, true));
            row.addView(text(e.note + " · " + date.format(new Date(e.time)) + " · " + e.source, 13, MUTED, false));
            resultBox.addView(row);
        }
        Button saveAll = button("确认并全部入账", ACCENT, Color.WHITE);
        saveAll.setOnClickListener(v -> {
            MainActivity.LedgerDb db = new MainActivity.LedgerDb(this);
            int saved = 0;
            for (MainActivity.Entry e : entries) if (db.insert(e) > 0) saved++;
            QuickNotificationHelper.show(this);
            toast("已写入 " + saved + " 笔账目");
            startActivity(new Intent(this, MainActivity.class));
        });
        resultBox.addView(saveAll, params(-1, dp(50), 10));
    }

    private void testConnection() {
        saveSettings();
        if (!AiLedgerClient.canUseAi(this)) { toast("请先启用 AI 并填写 API Key"); return; }
        status.setText("正在测试接口…");
        status.setTextColor(PRIMARY);
        AiLedgerClient.parseAsync(this, "支付宝支付成功，美团外卖 12.34 元", new AiLedgerClient.Callback() {
            @Override public void onSuccess(List<MainActivity.Entry> entries, String modelText) {
                status.setText(entries.isEmpty() ? "接口已连接，但示例未识别为账单，请检查模型或提示兼容性" : "接口测试成功，示例识别正常");
                status.setTextColor(entries.isEmpty() ? DANGER : ACCENT);
            }
            @Override public void onFailure(String message) {
                status.setText("接口测试失败：" + message);
                status.setTextColor(DANGER);
            }
        });
    }

    private float parseConfidence(String value) {
        try { return Math.max(0.1f, Math.min(1f, Float.parseFloat(value.trim()))); }
        catch (Exception ignored) { return AiLedgerClient.DEFAULT_MIN_CONFIDENCE; }
    }

    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(round(color, 22));
        c.setElevation(dp(4));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(8), 0, dp(8));
        c.setLayoutParams(p);
        return c;
    }

    private TextView section(String value) {
        TextView t = text(value, 19, PRIMARY_DARK, true);
        t.setPadding(dp(2), dp(14), dp(2), dp(4));
        return t;
    }

    private TextView label(String value) {
        TextView t = text(value, 13, MUTED, true);
        t.setPadding(0, dp(8), 0, dp(3));
        return t;
    }

    private EditText input(String hint, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(Color.rgb(246, 248, 253), 15));
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return e;
    }

    private CheckBox check(String value) {
        CheckBox c = new CheckBox(this);
        c.setText(value);
        c.setTextSize(14);
        c.setTextColor(PRIMARY_DARK);
        return c;
    }

    private Button button(String value, int background, int foreground) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(14);
        b.setTextColor(foreground);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(background, 18));
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    private LinearLayout.LayoutParams params(int width, int height, int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(0, dp(topMargin), 0, 0);
        return p;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), Color.argb(80, 120, 125, 150));
        return g;
    }

    private GradientDrawable gradient(int start, int end) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        g.setCornerRadius(dp(24));
        return g;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
