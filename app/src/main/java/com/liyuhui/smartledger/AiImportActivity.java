package com.liyuhui.smartledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Settings and test page for background merchant category classification. */
public class AiImportActivity extends Activity {
    private static final int BG = Color.rgb(246, 247, 251);
    private static final int TEXT = Color.rgb(31, 35, 48);
    private static final int MUTED = Color.rgb(105, 111, 128);
    private static final int PRIMARY = Color.rgb(87, 82, 232);
    private static final int SUCCESS = Color.rgb(25, 163, 124);
    private static final int DANGER = Color.rgb(221, 75, 86);

    private CheckBox enabled;
    private CheckBox notificationEnabled;
    private EditText endpoint;
    private EditText model;
    private EditText apiKey;
    private EditText confidence;
    private EditText testMerchant;
    private TextView testResult;
    private TextView memoryInfo;
    private Button testButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildPage();
        loadSettings();
    }

    private void buildPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(14), dp(16), dp(32));
        root.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout titleRow = horizontal();
        Button back = smallButton("‹ 返回", Color.TRANSPARENT, TEXT);
        back.setOnClickListener(v -> finish());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(82), dp(44)));
        TextView title = text("AI 分类设置", 23, TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(titleRow);

        LinearLayout intro = card();
        intro.setBackground(gradient(Color.rgb(90, 84, 235), Color.rgb(120, 84, 218), 24));
        intro.addView(text("AI 只负责判断消费品类", 21, Color.WHITE, true));
        intro.addView(text("金额、支付账户和时间在手机本地提取；只有无法确定品类的商家名称才会发送给 AI。", 14,
                Color.argb(230, 255, 255, 255), false));
        LinearLayout flow = horizontal();
        flow.setPadding(0, dp(12), 0, 0);
        flow.addView(flowChip("商家记忆"), new LinearLayout.LayoutParams(0, dp(40), 1));
        addGap(flow, 7);
        flow.addView(flowChip("本地规则"), new LinearLayout.LayoutParams(0, dp(40), 1));
        addGap(flow, 7);
        flow.addView(flowChip("AI 分类"), new LinearLayout.LayoutParams(0, dp(40), 1));
        intro.addView(flow);
        root.addView(intro);

        root.addView(section("自动分类"));
        LinearLayout switchCard = card();
        enabled = check("启用 AI 智能分类");
        notificationEnabled = check("微信 / 支付宝付款通知使用 AI 分类");
        switchCard.addView(enabled);
        switchCard.addView(notificationEnabled);
        switchCard.addView(text("已知商家不会重复请求 AI；分类结果会保存在本机，下一次直接使用。", 13, MUTED, false));
        root.addView(switchCard);

        root.addView(section("接口配置"));
        LinearLayout settings = card();
        settings.addView(label("接口地址"));
        endpoint = input("https://api.openai.com/v1/responses", false, false);
        settings.addView(endpoint, fieldParams());
        settings.addView(label("模型"));
        model = input("gpt-5-mini", false, false);
        settings.addView(model, fieldParams());
        settings.addView(label("API Key"));
        apiKey = input("留空表示保留已保存的密钥", false, true);
        settings.addView(apiKey, fieldParams());
        settings.addView(label("最低置信度"));
        confidence = input("0.68", true, false);
        settings.addView(confidence, fieldParams());

        Button save = primaryButton("保存设置");
        save.setOnClickListener(v -> saveSettings(true));
        settings.addView(save, buttonParams());
        Button clearKey = secondaryButton("清除已保存的 API Key");
        clearKey.setTextColor(DANGER);
        clearKey.setOnClickListener(v -> confirmClearKey());
        settings.addView(clearKey, buttonParams());
        root.addView(settings);

        root.addView(section("分类测试"));
        LinearLayout testCard = card();
        testCard.addView(text("输入微信付款记录里可能出现的商家名称，查看最终会被分到哪一类。", 13, MUTED, false));
        testMerchant = input("例如：鲜丰水果（万达店）", false, false);
        testCard.addView(testMerchant, fieldParams());
        testButton = primaryButton("测试商家分类");
        testButton.setOnClickListener(v -> testCategory());
        testCard.addView(testButton, buttonParams());
        testResult = text("尚未测试", 14, MUTED, false);
        testResult.setPadding(dp(4), dp(10), dp(4), dp(2));
        testCard.addView(testResult);
        root.addView(testCard);

        root.addView(section("商家分类记忆"));
        LinearLayout memoryCard = card();
        memoryInfo = text("", 14, MUTED, false);
        memoryCard.addView(memoryInfo);
        Button clearMemory = secondaryButton("清空商家分类记忆");
        clearMemory.setOnClickListener(v -> confirmClearMemory());
        memoryCard.addView(clearMemory, buttonParams());
        root.addView(memoryCard);
    }

    private void loadSettings() {
        AiLedgerClient.Config config = AiLedgerClient.loadConfig(this);
        enabled.setChecked(config.enabled);
        notificationEnabled.setChecked(config.notificationEnabled);
        endpoint.setText(config.endpoint);
        model.setText(config.model);
        confidence.setText(String.format(Locale.US, "%.2f", config.minConfidence));
        apiKey.setText("");
        apiKey.setHint(config.hasApiKey ? "已加密保存；留空表示不修改" : "尚未填写 API Key");
        refreshMemory();
    }

    private boolean saveSettings(boolean showToast) {
        try {
            AiLedgerClient.saveConfig(this,
                    enabled.isChecked(), notificationEnabled.isChecked(),
                    endpoint.getText().toString(), model.getText().toString(),
                    parseConfidence(confidence.getText().toString()),
                    apiKey.getText().toString());
            apiKey.setText("");
            loadSettings();
            if (showToast) toast("AI 分类设置已保存");
            return true;
        } catch (Exception error) {
            toast("保存失败：" + safeMessage(error));
            return false;
        }
    }

    private void testCategory() {
        String merchant = testMerchant.getText().toString().trim();
        if (merchant.isEmpty()) {
            toast("请先输入商家名称");
            return;
        }
        if (!saveSettings(false)) return;

        String remembered = MerchantCategoryStore.find(this, merchant);
        if (!remembered.isEmpty()) {
            showResult(remembered, "来自商家分类记忆", 1d, SUCCESS);
            return;
        }
        String local = LedgerCategories.inferStrong(merchant, "支出");
        if (!"其他".equals(local)) {
            showResult(local, "本地商家规则已能判断，不需要调用 AI", 1d, SUCCESS);
            return;
        }
        if (!AiLedgerClient.canUseAi(this)) {
            showResult("其他", "尚未配置可用的 API Key", 0d, DANGER);
            return;
        }

        testButton.setEnabled(false);
        testResult.setText("正在调用 AI 判断商家类型…");
        testResult.setTextColor(PRIMARY);
        AiLedgerClient.classifyAsync(this, merchant,
                "微信支付成功\n付款给：" + merchant + "\n金额 18.80 元",
                "支出", new AiLedgerClient.CategoryCallback() {
                    @Override public void onSuccess(AiLedgerClient.CategoryResult result) {
                        testButton.setEnabled(true);
                        MerchantCategoryStore.remember(AiImportActivity.this, merchant, result.category);
                        showResult(result.category,
                                result.reason.isEmpty() ? "AI 商家分类" : result.reason,
                                result.confidence, SUCCESS);
                        refreshMemory();
                    }

                    @Override public void onFailure(String message) {
                        testButton.setEnabled(true);
                        showResult("其他", message, 0d, DANGER);
                    }
                });
    }

    private void showResult(String category, String reason, double score, int color) {
        String confidenceText = score > 0
                ? " · 置信度 " + String.format(Locale.CHINA, "%.0f%%", score * 100d) : "";
        testResult.setText(LedgerCategories.icon(category) + "  " + category
                + confidenceText + "\n" + reason);
        testResult.setTextColor(color);
    }

    private void refreshMemory() {
        int count = MerchantCategoryStore.size(this);
        String examples = MerchantCategoryStore.summary(this, 5);
        memoryInfo.setText("已记住 " + count + " 个商家"
                + (examples.isEmpty() ? "\n自动记账后，这里会逐渐积累常用店铺。" : "\n\n" + examples));
    }

    private void confirmClearKey() {
        new AlertDialog.Builder(this)
                .setTitle("清除 API Key")
                .setMessage("清除后，未知商家会暂时归入“其他”，本地规则和商家记忆仍可使用。")
                .setPositiveButton("清除", (dialog, which) -> {
                    AiLedgerClient.clearApiKey(this);
                    loadSettings();
                    toast("API Key 已清除");
                })
                .setNegativeButton("取消", null).show();
    }

    private void confirmClearMemory() {
        new AlertDialog.Builder(this)
                .setTitle("清空商家分类记忆")
                .setMessage("常用商家之后会重新经过本地规则或 AI 分类。")
                .setPositiveButton("清空", (dialog, which) -> {
                    MerchantCategoryStore.clear(this);
                    refreshMemory();
                    toast("商家分类记忆已清空");
                })
                .setNegativeButton("取消", null).show();
    }

    private float parseConfidence(String value) {
        try {
            return Math.max(0.1f, Math.min(1f, Float.parseFloat(value.trim())));
        } catch (Exception ignored) {
            return AiLedgerClient.DEFAULT_MIN_CONFIDENCE;
        }
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(Color.WHITE, 22));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(7), 0, dp(7));
        card.setLayoutParams(params);
        return card;
    }

    private TextView section(String value) {
        TextView view = text(value, 17, TEXT, true);
        view.setPadding(dp(3), dp(16), 0, dp(3));
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13, MUTED, true);
        view.setPadding(dp(2), dp(9), 0, dp(4));
        return view;
    }

    private CheckBox check(String value) {
        CheckBox box = new CheckBox(this);
        box.setText(value);
        box.setTextSize(15);
        box.setTextColor(TEXT);
        box.setPadding(0, dp(4), 0, dp(4));
        return box;
    }

    private EditText input(String hint, boolean number, boolean password) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(14);
        field.setTextColor(TEXT);
        field.setHintTextColor(Color.rgb(155, 160, 176));
        field.setSingleLine(true);
        field.setPadding(dp(13), 0, dp(13), 0);
        field.setBackground(round(Color.rgb(247, 248, 252), 15));
        if (number) {
            field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else if (password) {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        }
        return field;
    }

    private TextView flowChip(String value) {
        TextView chip = text(value, 13, Color.WHITE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(round(Color.argb(45, 255, 255, 255), 14));
        return chip;
    }

    private Button primaryButton(String value) {
        return smallButton(value, PRIMARY, Color.WHITE);
    }

    private Button secondaryButton(String value) {
        return smallButton(value, Color.rgb(244, 245, 251), TEXT);
    }

    private Button smallButton(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(background == Color.TRANSPARENT ? null : round(background, 16));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, 0, 0, dp(3));
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private void addGap(LinearLayout parent, int width) {
        parent.addView(new TextView(this), new LinearLayout.LayoutParams(dp(width), 1));
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (color == Color.WHITE || color == Color.rgb(247, 248, 252)) {
            drawable.setStroke(dp(1), Color.rgb(233, 235, 242));
        }
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
