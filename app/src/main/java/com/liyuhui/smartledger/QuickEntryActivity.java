package com.liyuhui.smartledger;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
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
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuickEntryActivity extends Activity {
    private static final int PRIMARY = Color.rgb(91, 95, 239);
    private static final int PRIMARY_DARK = Color.rgb(34, 36, 77);
    private static final String[] CATEGORIES = {"餐饮", "交通", "购物", "住房", "学习", "医疗", "娱乐", "通讯", "人情", "工资", "退款", "其他"};
    private static final String[] ACCOUNTS = {"微信", "支付宝", "银行卡", "信用卡", "现金", "其他"};
    private final SimpleDateFormat dateTimeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private final Calendar selectedTime = Calendar.getInstance();

    private Spinner typeSpinner;
    private Spinner categorySpinner;
    private Spinner accountSpinner;
    private EditText amountInput;
    private EditText noteInput;
    private EditText dateInput;
    private TextView rawPreview;
    private String rawText = "";
    private long entryId = -1L;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotifyPermissionIfNeeded();
        QuickNotificationHelper.show(this);
        buildUi();
        fillFromIntent();
    }

    private void requestNotifyPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
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
        TextView sub = text("可再次编辑金额、分类、账户、备注和日期时间。", 13, Color.rgb(100, 105, 125), false);
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
        dateInput = input("日期时间 yyyy-MM-dd HH:mm", false);
        dateInput.setSingleLine(true);
        dateInput.setInputType(InputType.TYPE_CLASS_DATETIME);

        card.addView(label("收支类型")); card.addView(typeSpinner);
        card.addView(label("分类")); card.addView(categorySpinner);
        card.addView(label("账户")); card.addView(accountSpinner);
        card.addView(label("金额")); card.addView(amountInput);
        card.addView(label("备注")); card.addView(noteInput);
        card.addView(label("日期时间"));

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.addView(dateInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView gapDate = new TextView(this);
        dateRow.addView(gapDate, new LinearLayout.LayoutParams(dp(8), 1));
        Button calendarBtn = button("📅", Color.argb(120, 255, 255, 255), PRIMARY);
        calendarBtn.setTextSize(20);
        calendarBtn.setOnClickListener(v -> showDateTimePicker());
        dateRow.addView(calendarBtn, new LinearLayout.LayoutParams(dp(58), dp(48)));
        card.addView(dateRow);

        Button auto = button("🔍 自动识别商户/商品/分类", Color.argb(120, 255, 255, 255), PRIMARY);
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

    private LinearLayout glassCard() { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackground(liquidBg(Color.argb(190, 255, 255, 255), dp(28))); card.setElevation(dp(8)); return card; }

    private void fillFromIntent() {
        entryId = getIntent().getLongExtra("entry_id", -1L);
        rawText = getIntent().getStringExtra("raw_text");
        String forcedType = getIntent().getStringExtra("forced_type");
        String forcedAccount = getIntent().getStringExtra("forced_account");
        String forcedCategory = getIntent().getStringExtra("forced_category");
        String forcedNote = getIntent().getStringExtra("forced_note");
        double forcedAmount = getIntent().getDoubleExtra("forced_amount", 0D);
        long forcedTime = getIntent().getLongExtra("forced_time", 0L);
        if (rawText == null) rawText = "";

        MainActivity.Entry parsed = MainActivity.SmartParser.parseOne(rawText);
        if (parsed == null) parsed = new MainActivity.Entry();
        if (forcedType != null && forcedType.length() > 0) parsed.type = forcedType;
        if (forcedAccount != null && forcedAccount.length() > 0) parsed.account = forcedAccount;
        if (forcedCategory != null && forcedCategory.length() > 0) parsed.category = forcedCategory;
        if (forcedAmount > 0) parsed.amount = forcedAmount;
        if (forcedTime > 0) parsed.time = forcedTime;

        String note = forcedNote != null && forcedNote.trim().length() > 0 ? forcedNote.trim() : makeBetterNote(rawText, parsed.note);
        String smartCategory = inferSmartCategory(rawText + " " + note, parsed.type);
        if (forcedCategory == null || forcedCategory.length() == 0 || "其他".equals(forcedCategory)) parsed.category = smartCategory;

        rawPreview.setText(buildSummary(rawText, parsed, note));
        setTypeSelection(parsed.type);
        setCategorySelection(parsed.category);
        setAccountSelection(parsed.account);
        if (parsed.amount > 0) amountInput.setText(String.format(Locale.CHINA, "%.2f", parsed.amount));
        noteInput.setText(note);
        selectedTime.setTimeInMillis(parsed.time > 0 ? parsed.time : System.currentTimeMillis());
        dateInput.setText(dateTimeFmt.format(new Date(selectedTime.getTimeInMillis())));
        if (entryId > 0) Toast.makeText(this, "正在编辑已有账目", Toast.LENGTH_SHORT).show();
    }

    private void showDateTimePicker() {
        syncCalendarFromInput();
        DatePickerDialog dateDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedTime.set(Calendar.YEAR, year);
            selectedTime.set(Calendar.MONTH, month);
            selectedTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            TimePickerDialog timeDialog = new TimePickerDialog(this, (timeView, hour, minute) -> {
                selectedTime.set(Calendar.HOUR_OF_DAY, hour);
                selectedTime.set(Calendar.MINUTE, minute);
                selectedTime.set(Calendar.SECOND, 0);
                selectedTime.set(Calendar.MILLISECOND, 0);
                dateInput.setText(dateTimeFmt.format(new Date(selectedTime.getTimeInMillis())));
            }, selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE), true);
            timeDialog.setTitle("选择时间，精确到分钟");
            timeDialog.show();
        }, selectedTime.get(Calendar.YEAR), selectedTime.get(Calendar.MONTH), selectedTime.get(Calendar.DAY_OF_MONTH));
        dateDialog.setTitle("选择日期");
        dateDialog.show();
    }

    private void syncCalendarFromInput() { try { selectedTime.setTime(dateTimeFmt.parse(dateInput.getText().toString().trim())); } catch (Exception ignored) { } }

    private void autoRecognizeAgain() {
        String source = (rawText == null ? "" : rawText) + " " + noteInput.getText().toString();
        String type = cleanSpinner(typeSpinner.getSelectedItem().toString());
        String category = inferSmartCategory(source, type);
        String merchant = extractMerchant(source);
        if (merchant.length() > 0) noteInput.setText(merchant);
        setCategorySelection(category);
        MainActivity.Entry e = MainActivity.SmartParser.parseOne(source);
        if (e != null && e.amount > 0 && amountInput.getText().toString().trim().isEmpty()) amountInput.setText(String.format(Locale.CHINA, "%.2f", e.amount));
        rawPreview.setText(buildSummary(source, e == null ? new MainActivity.Entry() : e, noteInput.getText().toString()));
        Toast.makeText(this, "已根据商户/商品关键词重新分类", Toast.LENGTH_SHORT).show();
    }

    private String buildSummary(String raw, MainActivity.Entry parsed, String note) {
        if (raw == null) raw = "";
        String merchant = extractMerchant(raw + " " + note);
        String orderNo = extractAfter(raw, "订单号", 42);
        if (orderNo.length() == 0) orderNo = extractAfter(raw, "商家订单号", 42);
        String time = extractDateTime(raw);
        StringBuilder sb = new StringBuilder();
        sb.append(entryId > 0 ? "编辑摘要\n" : "识别摘要\n");
        if (parsed.amount > 0) sb.append("金额：¥").append(String.format(Locale.CHINA, "%.2f", parsed.amount)).append('\n');
        sb.append("账户：").append(parsed.account).append("    分类：").append(parsed.category).append('\n');
        if (merchant.length() > 0) sb.append("商户/商品：").append(merchant).append('\n');
        if (note != null && note.length() > 0) sb.append("备注：").append(note).append('\n');
        if (time.length() > 0) sb.append("原始时间：").append(time).append('\n');
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

    private String inferSmartCategory(String raw, String type) {
        if (raw == null) raw = "";
        String t = raw.toLowerCase(Locale.ROOT);
        if ("收入".equals(type)) return MainActivity.SmartParser.inferCategory(raw, type);
        if (has(raw, "红包", "转账", "份子", "礼物", "请客", "人情")) return "人情";
        if (has(raw, "腾讯天游", "腾讯", "游戏", "手游", "和平精英", "王者荣耀", "点券", "皮肤", "充值", "steam", "bilibili", "哔哩哔哩", "会员")) return "娱乐";
        if (has(raw, "超市", "便利店", "华联", "永辉", "沃尔玛", "盒马", "商店", "店内购物", "购物", "商城")) return "购物";
        if (has(raw, "兰州拉面", "拉面", "面馆", "清真", "餐馆", "饭店", "小吃", "米线", "麻辣烫", "烧烤", "火锅", "奶茶", "咖啡", "肯德基", "麦当劳", "美团", "饿了么")) return "餐饮";
        if (has(raw, "滴滴", "地铁", "公交", "高铁", "火车", "机票", "停车", "加油", "油费", "高速")) return "交通";
        if (has(raw, "医院", "药", "药房", "挂号", "医保", "体检")) return "医疗";
        String c = MainActivity.SmartParser.inferCategory(raw, type);
        return c == null || c.trim().isEmpty() ? "其他" : c;
    }

    private boolean has(String s, String... keys) { for (String k : keys) if (s.contains(k)) return true; return false; }

    private String extractMerchant(String raw) {
        if (raw == null) return "";
        String[] known = {"清真中国兰州拉面", "兰州拉面", "腾讯天游", "和平精英", "浏阳市洞阳镇聪花超市", "聪花超市", "华联", "肯德基宅急送", "肯德基", "KFC", "麦当劳", "美团外卖", "饿了么", "瑞幸", "星巴克", "蜜雪冰城", "喜茶", "奈雪", "必胜客", "汉堡王", "华莱士", "塔斯汀", "霸王茶姬", "茶百道", "古茗", "沪上阿姨", "库迪咖啡"};
        for (String k : known) if (raw.contains(k)) return k;
        String title = extractTitleBeforeAmount(raw);
        if (title.length() > 0) return title;
        String[] keys = {"商品说明", "商品名称", "商品", "商户全称", "商户名称", "商户", "收款方", "对方", "备注"};
        for (String key : keys) { String s = extractField(raw, key); if (s.length() > 0) return cleanMerchant(s); }
        return "";
    }

    private String extractTitleBeforeAmount(String raw) {
        Matcher m = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9_（）()·\\-]{2,42})\\s*[-−]\\s*\\d+(?:\\.\\d{1,2})").matcher(raw);
        String best = "";
        while (m.find()) {
            String s = cleanMerchant(m.group(1));
            if (s.length() > 1 && !isBadMerchant(s)) best = s;
        }
        return best;
    }

    private boolean isBadMerchant(String s) { return s.contains("账目详情") || s.contains("自动识别") || s.contains("收支类型") || s.contains("分类") || s.contains("账户") || s.contains("金额"); }

    private String extractField(String raw, String key) {
        int i = raw.indexOf(key);
        if (i < 0) return "";
        String sub = raw.substring(Math.min(raw.length(), i + key.length())).replaceFirst("^[：: ]+", "").trim();
        String[] stopWords = {"商家订单号", "订单号", "账单", "分类", "标签", "备注", "账户", "金额", "日期", "支付", "收支", "取消", "保存", "交易单号", "收单机构"};
        int cut = sub.length();
        for (String stop : stopWords) { int p = sub.indexOf(stop); if (p > 0 && p < cut) cut = p; }
        if (cut > 0 && cut < sub.length()) sub = sub.substring(0, cut);
        if (sub.length() > 36) sub = sub.substring(0, 36);
        return sub;
    }

    private String cleanMerchant(String s) {
        if (s == null) return "";
        return s.replace("订单", "").replace("商家订单号", "").replace("账单", "").replace("分类", "").replace("账户", "").replace("支付成功", "").replace("当前状态", "").replaceAll("[0-9]{6,}.*", "").replaceAll("[：:，,。；;\\n\\r]", " ").replaceAll("\\s+", " ").trim();
    }

    private String extractAfter(String raw, String key, int maxLen) { int i = raw.indexOf(key); if (i < 0) return ""; String sub = raw.substring(Math.min(raw.length(), i + key.length())).trim().replaceFirst("^[：: ]+", ""); if (sub.length() > maxLen) sub = sub.substring(0, maxLen); return sub.replaceAll("[\n\r]", " ").trim(); }
    private String extractDateTime(String raw) { Matcher m = Pattern.compile("20\\d{2}[-年/][0-9]{1,2}[-月/][0-9]{1,2}(?:日)?(?:\\s+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?").matcher(raw); if (m.find()) return m.group(); return ""; }

    private void saveEntry() {
        double amount = parseDouble(amountInput.getText().toString());
        if (amount <= 0) { Toast.makeText(this, "请输入正确金额", Toast.LENGTH_SHORT).show(); return; }
        MainActivity.Entry e = new MainActivity.Entry();
        e.id = entryId;
        e.amount = amount;
        e.type = cleanSpinner(typeSpinner.getSelectedItem().toString());
        e.category = cleanSpinner(categorySpinner.getSelectedItem().toString());
        e.account = cleanSpinner(accountSpinner.getSelectedItem().toString());
        e.note = noteInput.getText().toString().trim();
        e.source = getIntent().getStringExtra("source") == null ? (entryId > 0 ? "手动编辑" : "快捷记账") : getIntent().getStringExtra("source");
        e.time = selectedTime.getTimeInMillis();
        try { e.time = dateTimeFmt.parse(dateInput.getText().toString().trim()).getTime(); } catch (Exception ignored) { }
        MainActivity.LedgerDb db = new MainActivity.LedgerDb(this);
        if (entryId > 0) {
            ContentValues v = new ContentValues();
            v.put("amount", e.amount); v.put("type", e.type); v.put("category", e.category); v.put("account", e.account); v.put("note", e.note); v.put("source", e.source); v.put("time", e.time);
            SQLiteDatabase sql = db.getWritableDatabase();
            sql.update("entries", v, "id=?", new String[]{String.valueOf(entryId)});
            Toast.makeText(this, "已更新账目", Toast.LENGTH_SHORT).show();
        } else {
            db.insert(e);
            Toast.makeText(this, "已保存入账", Toast.LENGTH_SHORT).show();
        }
        QuickNotificationHelper.show(this);
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
