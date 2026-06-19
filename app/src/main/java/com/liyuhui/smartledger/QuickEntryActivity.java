package com.liyuhui.smartledger;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuickEntryActivity extends Activity {
    private static final int PRIMARY = Color.rgb(91, 95, 239);
    private static final int PRIMARY_DARK = Color.rgb(34, 36, 77);
    private static final String[] CATEGORIES = {"餐饮", "交通", "购物", "住房", "学习", "医疗", "娱乐", "通讯", "人情", "工资", "退款", "其他"};
    private static final String[] ACCOUNTS = {"微信", "支付宝", "银行卡", "信用卡", "现金", "其他"};

    private Spinner typeSpinner;
    private Spinner categorySpinner;
    private Spinner accountSpinner;
    private EditText amountInput;
    private EditText noteInput;
    private EditText dateInput;
    private TextView rawPreview;
    private String rawText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotifyPermissionIfNeeded();
        QuickNotificationHelper.show(this);
        buildUi();
        fillFromIntent();
    }

    private void requestNotifyPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackground(liquidBg(Color.argb(235, 244, 247, 255), dp(0)));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout card = glassCard();
        root.addView(card);

        TextView title = text("自动识别到账单", 24, PRIMARY_DARK, true);
        card.addView(title);
        TextView sub = text("确认后直接保存，不再跳回 App 首页。", 13, Color.rgb(100, 105, 125), false);
        sub.setPadding(0, dp(4), 0, dp(12));
        card.addView(sub);

        rawPreview = text("", 13, Color.rgb(75, 82, 105), false);
        rawPreview.setPadding(dp(14), dp(12), dp(14), dp(12));
        rawPreview.setBackground(liquidBg(Color.argb(150, 255, 255, 255), dp(18)));
        card.addView(rawPreview, new LinearLayout.LayoutParams(-1, -2));

        typeSpinner = spinner(new String[]{"💸 支出", "💰 收入"});
        categorySpinner = spinner(categoryItems());
        accountSpinner = spinner(accountItems());
        amountInput = input("金额", true);
        noteInput = input("备注", false);
        dateInput = input("日期 yyyy-MM-dd", false);

        card.addView(label("收支类型")); card.addView(typeSpinner);
        card.addView(label("分类")); card.addView(categorySpinner);
        card.addView(label("账户")); card.addView(accountSpinner);
        card.addView(label("金额")); card.addView(amountInput);
        card.addView(label("备注")); card.addView(noteInput);
        card.addView(label("日期")); card.addView(dateInput);

        Button auto = button("🔍 自动识别商品/分类", Color.argb(120, 255, 255, 255), PRIMARY);
        auto.setOnClickListener(v -> autoRecognizeAgain());
        card.addView(auto, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(14), 0, 0);
        card.addView(actions);

        Button cancel = button("取消", Color.argb(95, 255, 255, 255), PRIMARY_DARK);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(50), 1));

        TextView gap = new TextView(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

        Button save = button("保存入账", Color.rgb(52, 199, 89), Color.WHITE);
        save.setOnClickListener(v -> saveEntry());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(50), 1));
    }

    private LinearLayout glassCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(liquidBg(Color.argb(190, 255, 255, 255), dp(28)));
        card.setElevation(dp(8));
        return card;
    }

    private void fillFromIntent() {
        rawText = getIntent().getStringExtra("raw_text");
        String forcedType = getIntent().getStringExtra("forced_type");
        String forcedAccount = getIntent().getStringExtra("forced_account");
        String forcedCategory = getIntent().getStringExtra("forced_category");
        String forcedNote = getIntent().getStringExtra("forced_note");
        double forcedAmount = getIntent().getDoubleExtra("forced_amount", 0D);
        if (rawText == null) rawText = "";

        MainActivity.Entry parsed = MainActivity.SmartParser.parseOne(rawText);
        if (parsed == null) parsed = new MainActivity.Entry();
        if (forcedType != null && forcedType.length() > 0) parsed.type = forcedType;
        if (forcedAccount != null && forcedAccount.length() > 0) parsed.account = forcedAccount;
        if (forcedCategory != null && forcedCategory.length() > 0) parsed.category = forcedCategory;
        if (forcedAmount > 0) parsed.amount = forcedAmount;

        String smartCategory = MainActivity.SmartParser.inferCategory(rawText, parsed.type);
        if (forcedCategory == null || forcedCategory.length() == 0) parsed.category = smartCategory;
        String note = forcedNote != null && forcedNote.trim().length() > 0 ? forcedNote.trim() : makeBetterNote(rawText, parsed.note);

        rawPreview.setText(buildSummary(rawText, parsed, note));
        setTypeSelection(parsed.type);
        setCategorySelection(parsed.category);
        setAccountSelection(parsed.account);
        if (parsed.amount > 0) amountInput.setText(String.format(Locale.CHINA, "%.2f", parsed.amount));
        noteInput.setText(note);
        dateInput.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(parsed.time > 0 ? parsed.time : System.currentTimeMillis())));
    }

    private void autoRecognizeAgain() {
        String source = (rawText == null ? "" : rawText) + " " + noteInput.getText().toString();
        String type = cleanSpinner(typeSpinner.getSelectedItem().toString());
        String category = MainActivity.SmartParser.inferCategory(source, type);
        String merchant = extractMerchant(source);
        if (merchant.length() > 0) noteInput.setText(merchant);
        setCategorySelection(category);
        MainActivity.Entry e = MainActivity.SmartParser.parseOne(source);
        if (e != null && e.amount > 0 && amountInput.getText().toString().trim().isEmpty()) amountInput.setText(String.format(Locale.CHINA, "%.2f", e.amount));
        rawPreview.setText(buildSummary(source, e == null ? new MainActivity.Entry() : e, noteInput.getText().toString()));
        Toast.makeText(this, "已重新识别商品和分类", Toast.LENGTH_SHORT).show();
    }

    private String buildSummary(String raw, MainActivity.Entry parsed, String note) {
        if (raw == null) raw = "";
        String merchant = extractMerchant(raw);
        String orderNo = extractAfter(raw, "订单号", 42);
        if (orderNo.length() == 0) orderNo = extractAfter(raw, "商家订单号", 42);
        String time = extractDateTime(raw);
        StringBuilder sb = new StringBuilder();
        sb.append("识别摘要\n");
        if (parsed.amount > 0) sb.append("金额：¥").append(String.format(Locale.CHINA, "%.2f", parsed.amount)).append('\n');
        sb.append("账户：").append(parsed.account).append("    分类：").append(parsed.category).append('\n');
        if (merchant.length() > 0) sb.append("商品/商户：").append(merchant).append('\n');
        if (note != null && note.length() > 0) sb.append("备注：").append(note).append('\n');
        if (time.length() > 0) sb.append("时间：").append(time).append('\n');
        if (orderNo.length() > 0) sb.append("订单号：").append(orderNo).append('\n');
        sb.append("保存后会停留当前使用场景，不默认跳转首页。");
        return sb.toString();
    }

    private String makeBetterNote(String raw, String fallback) {
        if (raw == null) raw = "";
        String merchant = extractMerchant(raw);
        if (merchant.length() > 0) return merchant;
        if (raw.contains("红包")) return "微信红包";
        if (raw.contains("扫码") || raw.contains("二维码") || raw.contains("收款方备注")) return "微信扫码付款";
        if (raw.contains("转账") || raw.contains("确认收款")) return "微信转账";
        if (raw.contains("支付")) return "支付消费";
        return fallback == null || fallback.trim().isEmpty() ? "快捷记账" : fallback.trim();
    }

    private String extractMerchant(String raw) {
        if (raw == null) return "";
        String[] known = {"肯德基宅急送", "肯德基", "KFC", "麦当劳", "美团外卖", "饿了么", "瑞幸", "星巴克", "蜜雪冰城", "喜茶", "奈雪", "必胜客", "汉堡王", "华莱士", "塔斯汀", "霸王茶姬", "茶百道", "古茗", "沪上阿姨", "库迪咖啡"};
        for (String k : known) if (raw.contains(k)) return k;
        String[] keys = {"商品说明", "商品名称", "商品", "商户名称", "商户", "收款方", "对方", "备注"};
        for (String key : keys) { String s = extractField(raw, key); if (s.length() > 0) return cleanMerchant(s); }
        return "";
    }

    private String extractField(String raw, String key) {
        int i = raw.indexOf(key);
        if (i < 0) return "";
        String sub = raw.substring(Math.min(raw.length(), i + key.length())).replaceFirst("^[：: ]+", "").trim();
        String[] stopWords = {"商家订单号", "订单号", "账单", "分类", "标签", "备注", "账户", "金额", "日期", "支付", "收支", "取消", "保存"};
        int cut = sub.length();
        for (String stop : stopWords) { int p = sub.indexOf(stop); if (p > 0 && p < cut) cut = p; }
        if (cut > 0 && cut < sub.length()) sub = sub.substring(0, cut);
        if (sub.length() > 36) sub = sub.substring(0, 36);
        return sub;
    }

    private String cleanMerchant(String s) {
        if (s == null) return "";
        return s.replace("订单", "").replace("商家订单号", "").replace("账单", "").replace("分类", "").replace("账户", "").replaceAll("[0-9]{6,}.*", "").replaceAll("[：:，,。；;\\n\\r]", " ").replaceAll("\\s+", " ").trim();
    }

    private String extractAfter(String raw, String key, int maxLen) {
        int i = raw.indexOf(key);
        if (i < 0) return "";
        String sub = raw.substring(Math.min(raw.length(), i + key.length())).trim().replaceFirst("^[：: ]+", "");
        if (sub.length() > maxLen) sub = sub.substring(0, maxLen);
        return sub.replaceAll("[\n\r]", " ").trim();
    }

    private String extractDateTime(String raw) {
        Matcher m = Pattern.compile("20\\d{2}[-年/][0-9]{1,2}[-月/][0-9]{1,2}(?:日)?(?:\\s+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?").matcher(raw);
        if (m.find()) return m.group();
        return "";
    }

    private void saveEntry() {
        double amount = parseDouble(amountInput.getText().toString());
        if (amount <= 0) { Toast.makeText(this, "请输入正确金额", Toast.LENGTH_SHORT).show(); return; }
        MainActivity.Entry e = new MainActivity.Entry();
        e.amount = amount;
        e.type = cleanSpinner(typeSpinner.getSelectedItem().toString());
        e.category = cleanSpinner(categorySpinner.getSelectedItem().toString());
        e.account = cleanSpinner(accountSpinner.getSelectedItem().toString());
        e.note = noteInput.getText().toString().trim();
        e.source = getIntent().getStringExtra("source") == null ? "快捷记账" : getIntent().getStringExtra("source");
        e.time = System.currentTimeMillis();
        try { e.time = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(dateInput.getText().toString().trim()).getTime(); } catch (Exception ignored) { }
        new MainActivity.LedgerDb(this).insert(e);
        QuickNotificationHelper.show(this);
        Toast.makeText(this, "已保存入账", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String[] categoryItems() { String[] arr = new String[CATEGORIES.length]; for (int i = 0; i < CATEGORIES.length; i++) arr[i] = MainActivity.iconForCategory(CATEGORIES[i]) + " " + CATEGORIES[i]; return arr; }
    private String[] accountItems() { String[] arr = new String[ACCOUNTS.length]; for (int i = 0; i < ACCOUNTS.length; i++) arr[i] = iconForAccount(ACCOUNTS[i]) + " " + ACCOUNTS[i]; return arr; }
    private String iconForAccount(String a) { if ("微信".equals(a)) return "💬"; if ("支付宝".equals(a)) return "🔵"; if ("银行卡".equals(a)) return "🏦"; if ("信用卡".equals(a)) return "💳"; if ("现金".equals(a)) return "💵"; return "📌"; }
    private void setTypeSelection(String value) { typeSpinner.setSelection(indexOfClean(new String[]{"💸 支出", "💰 收入"}, value)); }
    private void setCategorySelection(String value) { categorySpinner.setSelection(indexOfClean(categoryItems(), value)); }
    private void setAccountSelection(String value) { accountSpinner.setSelection(indexOfClean(accountItems(), value)); }
    private int indexOfClean(String[] arr, String v) { if (v == null) return arr.length - 1; for (int i = 0; i < arr.length; i++) if (cleanSpinner(arr[i]).equals(v)) return i; return arr.length - 1; }
    private String cleanSpinner(String s) { if (s == null) return ""; return s.replaceAll("^[^\\p{IsHan}A-Za-z0-9]+\\s*", "").trim(); }

    private double parseDouble(String s) { try { return Double.parseDouble(s.replace("¥", "").trim()); } catch (Exception e) { return 0; } }
    private TextView label(String s) { TextView t = text(s, 13, Color.rgb(80, 84, 105), true); t.setPadding(0, dp(12), 0, dp(5)); return t; }
    private EditText input(String hint, boolean number) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(15); e.setSingleLine(false); e.setPadding(dp(14), 0, dp(14), 0); e.setBackground(liquidBg(Color.argb(135, 255, 255, 255), dp(18))); e.setInputType(number ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL) : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); return e; }
    private Spinner spinner(String[] items) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); s.setBackground(liquidBg(Color.argb(135, 255, 255, 255), dp(18))); return s; }
    private Button button(String text, int bg, int fg) { Button b = new Button(this); b.setAllCaps(false); b.setText(text); b.setTextColor(fg); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(liquidBg(bg, dp(22))); b.setElevation(dp(4)); b.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start(); else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start(); return false; }); return b; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private GradientDrawable liquidBg(int color, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.argb(235, 255, 255, 255), color, Color.argb(90, 255, 255, 255)}); g.setCornerRadius(radius); g.setStroke(dp(1), Color.argb(190, 255, 255, 255)); return g; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
