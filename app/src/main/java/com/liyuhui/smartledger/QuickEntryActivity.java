package com.liyuhui.smartledger;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
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
import android.text.InputType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("自动识别到账单", 22, PRIMARY_DARK, true);
        root.addView(title);
        TextView sub = text("已从当前页面/通知中读取信息，请确认后保存。", 13, Color.rgb(100, 105, 125), false);
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub);

        rawPreview = text("", 12, Color.rgb(105, 110, 130), false);
        rawPreview.setPadding(dp(10), dp(8), dp(10), dp(8));
        rawPreview.setBackground(round(Color.rgb(246, 248, 252), 12));
        root.addView(rawPreview, new LinearLayout.LayoutParams(-1, -2));

        typeSpinner = spinner(new String[]{"支出", "收入"});
        categorySpinner = spinner(CATEGORIES);
        accountSpinner = spinner(ACCOUNTS);
        amountInput = input("金额", true);
        noteInput = input("备注", false);
        dateInput = input("日期 yyyy-MM-dd", false);

        root.addView(label("收支类型")); root.addView(typeSpinner);
        root.addView(label("分类")); root.addView(categorySpinner);
        root.addView(label("账户")); root.addView(accountSpinner);
        root.addView(label("金额")); root.addView(amountInput);
        root.addView(label("备注")); root.addView(noteInput);
        root.addView(label("日期")); root.addView(dateInput);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(12), 0, 0);
        root.addView(actions);

        Button cancel = button("取消", Color.rgb(245, 247, 250), PRIMARY_DARK);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView gap = new TextView(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));

        Button save = button("保存入账", PRIMARY, Color.WHITE);
        save.setOnClickListener(v -> saveEntry());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
    }

    private void fillFromIntent() {
        String raw = getIntent().getStringExtra("raw_text");
        String forcedType = getIntent().getStringExtra("forced_type");
        String forcedAccount = getIntent().getStringExtra("forced_account");
        if (raw == null) raw = "";
        rawPreview.setText(raw.length() > 0 ? raw : "通知栏快捷记账：请手动填写金额和备注。 ");

        MainActivity.Entry parsed = MainActivity.SmartParser.parseOne(raw);
        if (parsed == null) parsed = new MainActivity.Entry();
        if (forcedType != null && forcedType.length() > 0) parsed.type = forcedType;
        if (forcedAccount != null && forcedAccount.length() > 0) parsed.account = forcedAccount;

        typeSpinner.setSelection(indexOf(new String[]{"支出", "收入"}, parsed.type));
        categorySpinner.setSelection(indexOf(CATEGORIES, parsed.category));
        accountSpinner.setSelection(indexOf(ACCOUNTS, parsed.account));
        if (parsed.amount > 0) amountInput.setText(String.format(Locale.CHINA, "%.2f", parsed.amount));
        noteInput.setText(makeBetterNote(raw, parsed.note));
        dateInput.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(parsed.time > 0 ? parsed.time : System.currentTimeMillis())));
    }

    private String makeBetterNote(String raw, String fallback) {
        if (raw == null) raw = "";
        if (raw.contains("扫码") || raw.contains("二维码") || raw.contains("收款方备注")) return "微信扫码付款";
        if (raw.contains("转账")) return "微信转账";
        if (raw.contains("支付")) return "支付消费";
        return fallback == null || fallback.trim().isEmpty() ? "快捷记账" : fallback.trim();
    }

    private void saveEntry() {
        double amount = parseDouble(amountInput.getText().toString());
        if (amount <= 0) {
            Toast.makeText(this, "请输入正确金额", Toast.LENGTH_SHORT).show();
            return;
        }
        MainActivity.Entry e = new MainActivity.Entry();
        e.amount = amount;
        e.type = typeSpinner.getSelectedItem().toString();
        e.category = categorySpinner.getSelectedItem().toString();
        e.account = accountSpinner.getSelectedItem().toString();
        e.note = noteInput.getText().toString().trim();
        e.source = getIntent().getStringExtra("source") == null ? "快捷记账" : getIntent().getStringExtra("source");
        e.time = System.currentTimeMillis();
        try {
            e.time = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(dateInput.getText().toString().trim()).getTime();
        } catch (Exception ignored) { }
        new MainActivity.LedgerDb(this).insert(e);
        Toast.makeText(this, "已保存入账", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int indexOf(String[] arr, String v) {
        if (v == null) return arr.length - 1;
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return arr.length - 1;
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.replace("¥", "").trim()); } catch (Exception e) { return 0; }
    }

    private TextView label(String s) {
        TextView t = text(s, 13, Color.rgb(95, 100, 120), true);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private EditText input(String hint, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setSingleLine(false);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(round(Color.rgb(248, 250, 255), 14));
        e.setInputType(number ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL) : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return e;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        s.setBackground(round(Color.rgb(248, 250, 255), 14));
        return s;
    }

    private Button button(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(bg, 15));
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
