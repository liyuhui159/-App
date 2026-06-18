package com.liyuhui.smartledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(244, 247, 251);
    private static final int PRIMARY = Color.rgb(91, 95, 239);
    private static final int PRIMARY_DARK = Color.rgb(34, 36, 77);
    private static final int ACCENT = Color.rgb(0, 194, 168);
    private static final int DANGER = Color.rgb(245, 91, 91);
    private static final int WARNING = Color.rgb(255, 181, 71);
    private static final String[] CATEGORIES = {"餐饮", "交通", "购物", "住房", "学习", "医疗", "娱乐", "通讯", "人情", "工资", "退款", "其他"};
    private static final String[] ACCOUNTS = {"微信", "支付宝", "银行卡", "信用卡", "现金", "其他"};

    private LedgerDb db;
    private LinearLayout page;
    private String currentTab = "看板";
    private final SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new LedgerDb(this);
        db.seedRecurring();
        int count = db.runDueRecurring();
        buildFrame();
        showDashboard();
        if (count > 0) toast("已自动补入 " + count + " 条周期账单");
    }

    private void buildFrame() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(16));
        header.setBackground(gradient(PRIMARY, Color.rgb(109, 82, 232), 0));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("灵犀记账", 28, Color.WHITE, true);
        header.addView(title);
        TextView subtitle = text("原生 Android · 自动识别 · 本地隐私 · 预算分析", 14, Color.argb(230, 255, 255, 255), false);
        header.addView(subtitle);

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.setPadding(0, dp(14), 0, 0);
        header.addView(quick);
        Button add = pill("+ 快速记一笔", Color.WHITE, PRIMARY);
        add.setOnClickListener(v -> showAddEntry());
        quick.addView(add, new LinearLayout.LayoutParams(0, dp(44), 1));
        Space(quick, 10, 1);
        Button notification = pill("开启通知自动记账", Color.argb(40, 255, 255, 255), Color.WHITE);
        notification.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        quick.addView(notification, new LinearLayout.LayoutParams(0, dp(44), 1));

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(12), dp(12), dp(12), dp(8));
        navScroll.addView(nav);
        root.addView(navScroll, new LinearLayout.LayoutParams(-1, -2));
        for (String tab : new String[]{"看板", "记账", "智能导入", "流水", "预算", "设置"}) {
            Button b = pill(tab, tab.equals(currentTab) ? PRIMARY : Color.WHITE, tab.equals(currentTab) ? Color.WHITE : PRIMARY_DARK);
            b.setOnClickListener(v -> {
                currentTab = ((Button) v).getText().toString();
                buildFrame();
                openTab(currentTab);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), dp(42));
            lp.setMargins(0, 0, dp(8), 0);
            nav.addView(b, lp);
        }

        ScrollView scroll = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(4), dp(14), dp(28));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void openTab(String tab) {
        if ("看板".equals(tab)) showDashboard();
        else if ("记账".equals(tab)) showAddEntry();
        else if ("智能导入".equals(tab)) showSmartImport();
        else if ("流水".equals(tab)) showRecords("", "全部");
        else if ("预算".equals(tab)) showBudget();
        else showSettings();
    }

    private void showDashboard() {
        currentTab = "看板";
        if (page == null) return;
        page.removeAllViews();
        String month = monthFmt.format(new Date());
        Totals totals = db.monthTotals(month);
        addSectionTitle("本月总览 · " + month);

        LinearLayout row1 = row();
        row1.addView(statCard("收入", money(totals.income), ACCENT), new LinearLayout.LayoutParams(0, dp(92), 1));
        Space(row1, 10, 0);
        row1.addView(statCard("支出", money(totals.expense), DANGER), new LinearLayout.LayoutParams(0, dp(92), 1));
        page.addView(row1);

        LinearLayout row2 = row();
        row2.addView(statCard("结余", money(totals.income - totals.expense), PRIMARY), new LinearLayout.LayoutParams(0, dp(92), 1));
        Space(row2, 10, 0);
        row2.addView(statCard("今日支出", money(totals.todayExpense), WARNING), new LinearLayout.LayoutParams(0, dp(92), 1));
        page.addView(row2);

        addSectionTitle("分类支出占比");
        ChartView pie = new ChartView(this);
        pie.setMode(ChartView.MODE_PIE);
        pie.setValues(db.categoryExpense(month));
        page.addView(cardWrap(pie, dp(230)));

        addSectionTitle("近 7 日消费趋势");
        ChartView bar = new ChartView(this);
        bar.setMode(ChartView.MODE_BAR);
        bar.setValues(db.lastSevenDays());
        page.addView(cardWrap(bar, dp(220)));

        addSectionTitle("最近流水");
        renderEntries(db.listEntries(6, "", "全部"));
    }

    private void showAddEntry() {
        currentTab = "记账";
        page.removeAllViews();
        addSectionTitle("手动记一笔");
        LinearLayout card = card();

        Spinner type = spinner(new String[]{"支出", "收入"});
        Spinner category = spinner(CATEGORIES);
        Spinner account = spinner(ACCOUNTS);
        EditText amount = input("金额，例如 18.50", true);
        EditText note = input("备注，例如 午餐 / 工资 / 房租", false);
        EditText date = input("日期 yyyy-MM-dd，留空默认今天", false);

        card.addView(label("收支类型")); card.addView(type);
        card.addView(label("分类")); card.addView(category);
        card.addView(label("账户")); card.addView(account);
        card.addView(label("金额")); card.addView(amount);
        card.addView(label("备注")); card.addView(note);
        card.addView(label("日期")); card.addView(date);

        Button save = primaryButton("保存账目");
        save.setOnClickListener(v -> {
            double a = parseDouble(amount.getText().toString());
            if (a <= 0) { toast("金额必须大于 0"); return; }
            Entry e = new Entry();
            e.amount = a;
            e.type = type.getSelectedItem().toString();
            e.category = category.getSelectedItem().toString();
            e.account = account.getSelectedItem().toString();
            e.note = note.getText().toString().trim();
            e.source = "手动记账";
            e.time = parseDate(date.getText().toString().trim());
            db.insert(e);
            toast("已保存");
            showDashboard();
        });
        card.addView(save, new LinearLayout.LayoutParams(-1, dp(48)));
        page.addView(card);
    }

    private void showSmartImport() {
        currentTab = "智能导入";
        page.removeAllViews();
        addSectionTitle("智能自动识别账单");
        TextView tip = text("可粘贴微信、支付宝、银行通知、短信或自己写的流水文本。一行一笔，系统会自动识别金额、分类、账户和收支类型。", 14, Color.rgb(95, 100, 120), false);
        page.addView(tip);
        EditText raw = input("示例：\n今天午餐 18.5 微信\n支付宝付款 美团外卖 32.8\n工资收入 6500 招商银行\n滴滴打车 -24.6", false);
        raw.setGravity(Gravity.TOP);
        raw.setMinLines(7);
        page.addView(raw, new LinearLayout.LayoutParams(-1, dp(180)));

        LinearLayout resultBox = card();
        page.addView(resultBox);

        Button parse = primaryButton("智能解析");
        page.addView(parse, new LinearLayout.LayoutParams(-1, dp(48)));
        parse.setOnClickListener(v -> {
            resultBox.removeAllViews();
            List<Entry> entries = SmartParser.parse(raw.getText().toString());
            if (entries.isEmpty()) {
                resultBox.addView(text("未识别到金额，请检查文本里是否包含数字金额。", 15, DANGER, true));
                return;
            }
            resultBox.addView(text("识别结果：" + entries.size() + " 条", 17, PRIMARY_DARK, true));
            for (Entry e : entries) resultBox.addView(entryLine(e, false));
            Button saveAll = primaryButton("全部入账");
            saveAll.setOnClickListener(x -> {
                for (Entry e : entries) db.insert(e);
                toast("已导入 " + entries.size() + " 条账目");
                showDashboard();
            });
            resultBox.addView(saveAll, new LinearLayout.LayoutParams(-1, dp(48)));
        });
    }

    private void showRecords(String query, String typeFilter) {
        currentTab = "流水";
        page.removeAllViews();
        addSectionTitle("交易流水");
        LinearLayout tools = card();
        EditText search = input("搜索备注、分类、账户", false);
        search.setText(query);
        Spinner type = spinner(new String[]{"全部", "支出", "收入"});
        type.setSelection("收入".equals(typeFilter) ? 2 : "支出".equals(typeFilter) ? 1 : 0);
        Button apply = primaryButton("筛选");
        Button export = secondaryButton("导出 CSV / 分享");
        Button importCsv = secondaryButton("粘贴 CSV 导入");
        apply.setOnClickListener(v -> showRecords(search.getText().toString().trim(), type.getSelectedItem().toString()));
        export.setOnClickListener(v -> shareCsv());
        importCsv.setOnClickListener(v -> showCsvImportDialog());
        tools.addView(search); tools.addView(type); tools.addView(apply); tools.addView(export); tools.addView(importCsv);
        page.addView(tools);
        renderEntries(db.listEntries(100, query, typeFilter));
    }

    private void showBudget() {
        currentTab = "预算";
        page.removeAllViews();
        String month = monthFmt.format(new Date());
        addSectionTitle("月度预算 · " + month);
        LinearLayout form = card();
        Spinner category = spinner(CATEGORIES);
        EditText amount = input("预算金额，例如 1200", true);
        Button save = primaryButton("保存预算");
        save.setOnClickListener(v -> {
            double a = parseDouble(amount.getText().toString());
            if (a <= 0) { toast("预算金额必须大于 0"); return; }
            db.setBudget(month, category.getSelectedItem().toString(), a);
            toast("预算已保存");
            showBudget();
        });
        form.addView(label("分类")); form.addView(category); form.addView(label("预算金额")); form.addView(amount); form.addView(save);
        page.addView(form);

        addSectionTitle("预算执行情况");
        Map<String, Double> spend = db.categoryExpense(month);
        List<Budget> budgets = db.listBudgets(month);
        if (budgets.isEmpty()) page.addView(empty("还没有预算，先添加一个分类预算。"));
        for (Budget b : budgets) {
            double used = spend.containsKey(b.category) ? spend.get(b.category) : 0;
            LinearLayout c = card();
            c.addView(text(b.category + "  " + money(used) + " / " + money(b.amount), 16, PRIMARY_DARK, true));
            ProgressView p = new ProgressView(this);
            p.setProgress((float) Math.min(1, used / Math.max(1, b.amount)), used > b.amount ? DANGER : ACCENT);
            c.addView(p, new LinearLayout.LayoutParams(-1, dp(18)));
            if (used > b.amount) c.addView(text("已超支 " + money(used - b.amount), 13, DANGER, true));
            page.addView(c);
        }
    }

    private void showSettings() {
        currentTab = "设置";
        page.removeAllViews();
        addSectionTitle("自动记账设置");
        LinearLayout auto = card();
        auto.addView(text("1. 通知自动记账", 17, PRIMARY_DARK, true));
        auto.addView(text("打开系统“通知使用权”，授权“灵犀自动记账服务”。之后收到微信、支付宝、银行类支付通知时，APP 会尝试自动解析金额并写入本地数据库。", 14, Color.rgb(95, 100, 120), false));
        Button open = primaryButton("去系统设置开启通知访问");
        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        auto.addView(open, new LinearLayout.LayoutParams(-1, dp(48)));
        page.addView(auto);

        addSectionTitle("周期自动入账");
        LinearLayout form = card();
        EditText name = input("名称，例如 房租 / 工资 / 会员", false);
        EditText amount = input("金额", true);
        EditText day = input("每月几号，例如 1", true);
        Spinner type = spinner(new String[]{"支出", "收入"});
        Spinner category = spinner(CATEGORIES);
        Spinner account = spinner(ACCOUNTS);
        Button add = primaryButton("添加周期账单");
        add.setOnClickListener(v -> {
            Recurring r = new Recurring();
            r.name = name.getText().toString().trim();
            r.amount = parseDouble(amount.getText().toString());
            r.day = Math.max(1, Math.min(28, (int) parseDouble(day.getText().toString())));
            r.type = type.getSelectedItem().toString();
            r.category = category.getSelectedItem().toString();
            r.account = account.getSelectedItem().toString();
            if (r.name.isEmpty() || r.amount <= 0) { toast("请填写名称和金额"); return; }
            db.addRecurring(r);
            toast("已添加周期账单");
            showSettings();
        });
        form.addView(name); form.addView(amount); form.addView(day); form.addView(type); form.addView(category); form.addView(account); form.addView(add);
        page.addView(form);

        Button run = secondaryButton("立即执行本月周期账单补入");
        run.setOnClickListener(v -> toast("补入 " + db.runDueRecurring() + " 条"));
        page.addView(run, new LinearLayout.LayoutParams(-1, dp(48)));

        addSectionTitle("已设置的周期账单");
        List<Recurring> list = db.listRecurring();
        if (list.isEmpty()) page.addView(empty("暂无周期账单。"));
        for (Recurring r : list) {
            LinearLayout c = card();
            c.addView(text(r.name + " · 每月 " + r.day + " 日", 16, PRIMARY_DARK, true));
            c.addView(text(r.type + " / " + r.category + " / " + r.account + " / " + money(r.amount), 14, Color.rgb(95, 100, 120), false));
            page.addView(c);
        }
    }

    private void renderEntries(List<Entry> entries) {
        if (entries.isEmpty()) { page.addView(empty("暂无账目。")); return; }
        for (Entry e : entries) page.addView(entryLine(e, true));
    }

    private View entryLine(Entry e, boolean allowDelete) {
        LinearLayout c = card();
        LinearLayout top = row();
        TextView left = text(e.category + " · " + e.account, 16, PRIMARY_DARK, true);
        TextView right = text(("收入".equals(e.type) ? "+" : "-") + money(e.amount), 17, "收入".equals(e.type) ? ACCENT : DANGER, true);
        top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(right);
        c.addView(top);
        c.addView(text((e.note == null || e.note.isEmpty() ? "无备注" : e.note) + "  ·  " + dateFmt.format(new Date(e.time)) + "  ·  " + e.source, 13, Color.rgb(105, 110, 130), false));
        if (allowDelete) {
            Button del = secondaryButton("删除");
            del.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("删除账目").setMessage("确定删除这条记录吗？").setPositiveButton("删除", (d, w) -> { db.delete(e.id); showRecords("", "全部"); }).setNegativeButton("取消", null).show());
            c.addView(del, new LinearLayout.LayoutParams(-1, dp(40)));
        }
        return c;
    }

    private void shareCsv() {
        String csv = Csv.toCsv(db.listEntries(5000, "", "全部"));
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_SUBJECT, "灵犀记账CSV导出");
        send.putExtra(Intent.EXTRA_TEXT, csv);
        startActivity(Intent.createChooser(send, "导出/分享 CSV"));
    }

    private void showCsvImportDialog() {
        EditText edit = input("粘贴 CSV 内容，首行可为表头：date,type,category,account,amount,note,source", false);
        edit.setMinLines(8);
        new AlertDialog.Builder(this).setTitle("CSV 导入").setView(edit).setPositiveButton("导入", (d, w) -> {
            List<Entry> list = Csv.fromCsv(edit.getText().toString());
            for (Entry e : list) db.insert(e);
            toast("已导入 " + list.size() + " 条");
            showRecords("", "全部");
        }).setNegativeButton("取消", null).show();
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(round(Color.WHITE, 18));
        c.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        c.setLayoutParams(lp);
        return c;
    }

    private View cardWrap(View child, int height) {
        LinearLayout c = card();
        c.addView(child, new LinearLayout.LayoutParams(-1, height));
        return c;
    }

    private LinearLayout statCard(String title, String value, int color) {
        LinearLayout c = card();
        c.addView(text(title, 13, Color.rgb(105, 110, 130), false));
        c.addView(text(value, 22, color, true));
        return c;
    }

    private void addSectionTitle(String s) {
        TextView t = text(s, 18, PRIMARY_DARK, true);
        t.setPadding(dp(2), dp(14), dp(2), dp(6));
        page.addView(t);
    }

    private TextView label(String s) {
        TextView t = text(s, 13, Color.rgb(95, 100, 120), true);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView empty(String s) {
        TextView t = text(s, 15, Color.rgb(120, 125, 145), false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(24), dp(16), dp(24));
        t.setBackground(round(Color.WHITE, 18));
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(number ? 46 : 54));
        lp.setMargins(0, dp(4), 0, dp(6));
        e.setLayoutParams(lp);
        return e;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        s.setBackground(round(Color.rgb(248, 250, 255), 14));
        return s;
    }

    private Button primaryButton(String text) { return pill(text, PRIMARY, Color.WHITE); }
    private Button secondaryButton(String text) { return pill(text, Color.WHITE, PRIMARY); }

    private Button pill(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(bg, 16));
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }

    private GradientDrawable round(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private GradientDrawable gradient(int c1, int c2, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2}); g.setCornerRadius(dp(radius)); return g; }
    private void Space(LinearLayout parent, int width, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(dp(width), height == 0 ? 1 : dp(height))); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private String money(double v) { return "¥" + String.format(Locale.CHINA, "%.2f", v); }
    private double parseDouble(String s) { try { return Double.parseDouble(s.replace("¥", "").trim()); } catch (Exception e) { return 0; } }
    private long parseDate(String s) { try { if (!s.isEmpty()) return dateFmt.parse(s).getTime(); } catch (Exception ignored) {} return System.currentTimeMillis(); }

    public static class Entry {
        long id;
        double amount;
        String type = "支出";
        String category = "其他";
        String account = "其他";
        String note = "";
        String source = "手动记账";
        long time = System.currentTimeMillis();
    }

    public static class Totals { double income, expense, todayExpense; }
    public static class Budget { String category; double amount; }
    public static class Recurring { long id; String name, type, category, account, lastMonth; double amount; int day; }

    public static class SmartParser {
        private static final Pattern AMOUNT = Pattern.compile("[-+]?\\d+(?:\\.\\d{1,2})?");
        public static List<Entry> parse(String raw) {
            List<Entry> result = new ArrayList<>();
            if (raw == null) return result;
            String[] lines = raw.split("\\n|；|;。");
            for (String line : lines) {
                Entry e = parseOne(line.trim());
                if (e != null) result.add(e);
            }
            return result;
        }
        public static Entry parseOne(String line) {
            if (line == null || line.trim().isEmpty()) return null;
            Matcher m = AMOUNT.matcher(line.replace(",", ""));
            double chosen = 0;
            while (m.find()) {
                double v = Math.abs(toDouble(m.group()));
                if (v > 0 && v < 100000000) { chosen = v; break; }
            }
            if (chosen <= 0) return null;
            Entry e = new Entry();
            e.amount = chosen;
            e.type = inferType(line);
            e.category = inferCategory(line, e.type);
            e.account = inferAccount(line);
            e.note = cleanNote(line);
            e.source = "智能解析";
            e.time = inferDate(line);
            return e;
        }
        private static String inferType(String s) {
            String t = s.toLowerCase(Locale.ROOT);
            if (s.contains("收入") || s.contains("工资") || s.contains("到账") || s.contains("收款") || s.contains("退款") || s.contains("奖金") || t.contains("income") || s.contains("+")) return "收入";
            return "支出";
        }
        private static String inferCategory(String s, String type) {
            if ("收入".equals(type)) {
                if (s.contains("工资") || s.contains("薪")) return "工资";
                if (s.contains("退款") || s.contains("退回")) return "退款";
                return "其他";
            }
            if (has(s, "饭", "餐", "奶茶", "外卖", "美团", "饿了么", "咖啡", "食堂", "超市")) return "餐饮";
            if (has(s, "滴滴", "打车", "公交", "地铁", "高铁", "火车", "机票", "油费", "停车")) return "交通";
            if (has(s, "淘宝", "京东", "拼多多", "购物", "衣服", "鞋", "数码")) return "购物";
            if (has(s, "房租", "水电", "物业", "燃气", "宽带", "租金")) return "住房";
            if (has(s, "书", "课程", "学费", "考试", "培训", "文具")) return "学习";
            if (has(s, "医院", "药", "挂号", "医保", "体检")) return "医疗";
            if (has(s, "电影", "游戏", "会员", "娱乐", "KTV", "旅游")) return "娱乐";
            if (has(s, "话费", "流量", "通讯")) return "通讯";
            if (has(s, "红包", "礼物", "请客", "份子")) return "人情";
            return "其他";
        }
        private static boolean has(String s, String... keys) { for (String k : keys) if (s.contains(k)) return true; return false; }
        private static String inferAccount(String s) {
            if (s.contains("微信")) return "微信";
            if (s.contains("支付宝") || s.contains("花呗")) return "支付宝";
            if (s.contains("信用卡")) return "信用卡";
            if (s.contains("银行") || s.contains("招商") || s.contains("工商") || s.contains("建设") || s.contains("农业")) return "银行卡";
            if (s.contains("现金")) return "现金";
            return "其他";
        }
        private static String cleanNote(String s) { return s.replaceAll("[-+]?\\d+(?:\\.\\d{1,2})?", "").replace("¥", "").replace("元", "").trim(); }
        private static double toDouble(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
        private static long inferDate(String s) {
            Calendar c = Calendar.getInstance();
            if (s.contains("昨天")) c.add(Calendar.DATE, -1);
            if (s.contains("前天")) c.add(Calendar.DATE, -2);
            Matcher m = Pattern.compile("(20\\d{2})[-/年](\\d{1,2})[-/月](\\d{1,2})").matcher(s);
            if (m.find()) {
                c.set(Calendar.YEAR, Integer.parseInt(m.group(1)));
                c.set(Calendar.MONTH, Integer.parseInt(m.group(2)) - 1);
                c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(m.group(3)));
            }
            return c.getTimeInMillis();
        }
    }

    public static class LedgerDb extends SQLiteOpenHelper {
        private static final String DB = "smart_ledger.db";
        public LedgerDb(Context c) { super(c, DB, null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL, type TEXT, category TEXT, account TEXT, note TEXT, source TEXT, time INTEGER)");
            db.execSQL("CREATE TABLE budgets(id INTEGER PRIMARY KEY AUTOINCREMENT, month TEXT, category TEXT, amount REAL, UNIQUE(month, category))");
            db.execSQL("CREATE TABLE recurring(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, amount REAL, type TEXT, category TEXT, account TEXT, day INTEGER, last_month TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
        public long insert(Entry e) {
            ContentValues v = new ContentValues();
            v.put("amount", e.amount); v.put("type", e.type); v.put("category", e.category); v.put("account", e.account); v.put("note", e.note); v.put("source", e.source); v.put("time", e.time);
            return getWritableDatabase().insert("entries", null, v);
        }
        public void delete(long id) { getWritableDatabase().delete("entries", "id=?", new String[]{String.valueOf(id)}); }
        public List<Entry> listEntries(int limit, String query, String type) {
            List<Entry> list = new ArrayList<>();
            StringBuilder where = new StringBuilder("1=1");
            List<String> args = new ArrayList<>();
            if (query != null && !query.isEmpty()) { where.append(" AND (note LIKE ? OR category LIKE ? OR account LIKE ?)"); String q = "%" + query + "%"; args.add(q); args.add(q); args.add(q); }
            if (type != null && !"全部".equals(type)) { where.append(" AND type=?"); args.add(type); }
            Cursor c = getReadableDatabase().query("entries", null, where.toString(), args.toArray(new String[0]), null, null, "time DESC, id DESC", String.valueOf(limit));
            while (c.moveToNext()) list.add(readEntry(c));
            c.close();
            return list;
        }
        private Entry readEntry(Cursor c) {
            Entry e = new Entry();
            e.id = c.getLong(c.getColumnIndexOrThrow("id")); e.amount = c.getDouble(c.getColumnIndexOrThrow("amount")); e.type = c.getString(c.getColumnIndexOrThrow("type")); e.category = c.getString(c.getColumnIndexOrThrow("category")); e.account = c.getString(c.getColumnIndexOrThrow("account")); e.note = c.getString(c.getColumnIndexOrThrow("note")); e.source = c.getString(c.getColumnIndexOrThrow("source")); e.time = c.getLong(c.getColumnIndexOrThrow("time"));
            return e;
        }
        public Totals monthTotals(String month) {
            Totals t = new Totals();
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
            for (Entry e : listEntries(10000, "", "全部")) {
                String m = new SimpleDateFormat("yyyy-MM", Locale.CHINA).format(new Date(e.time));
                if (!month.equals(m)) continue;
                if ("收入".equals(e.type)) t.income += e.amount; else t.expense += e.amount;
                String d = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(e.time));
                if (today.equals(d) && "支出".equals(e.type)) t.todayExpense += e.amount;
            }
            return t;
        }
        public Map<String, Double> categoryExpense(String month) {
            Map<String, Double> map = new LinkedHashMap<>();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
            for (Entry e : listEntries(10000, "", "支出")) {
                if (!month.equals(f.format(new Date(e.time)))) continue;
                map.put(e.category, map.containsKey(e.category) ? map.get(e.category) + e.amount : e.amount);
            }
            return map;
        }
        public Map<String, Double> lastSevenDays() {
            Map<String, Double> map = new LinkedHashMap<>();
            Calendar c = Calendar.getInstance();
            SimpleDateFormat f = new SimpleDateFormat("MM-dd", Locale.CHINA);
            for (int i = 6; i >= 0; i--) { Calendar x = (Calendar) c.clone(); x.add(Calendar.DATE, -i); map.put(f.format(x.getTime()), 0d); }
            for (Entry e : listEntries(10000, "", "支出")) {
                String d = f.format(new Date(e.time));
                if (map.containsKey(d)) map.put(d, map.get(d) + e.amount);
            }
            return map;
        }
        public void setBudget(String month, String category, double amount) {
            ContentValues v = new ContentValues(); v.put("month", month); v.put("category", category); v.put("amount", amount);
            getWritableDatabase().insertWithOnConflict("budgets", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
        public List<Budget> listBudgets(String month) {
            List<Budget> list = new ArrayList<>();
            Cursor c = getReadableDatabase().query("budgets", null, "month=?", new String[]{month}, null, null, "category ASC");
            while (c.moveToNext()) { Budget b = new Budget(); b.category = c.getString(c.getColumnIndexOrThrow("category")); b.amount = c.getDouble(c.getColumnIndexOrThrow("amount")); list.add(b); }
            c.close(); return list;
        }
        public void seedRecurring() {
            Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM recurring", null); c.moveToFirst(); int n = c.getInt(0); c.close();
            if (n == 0) {
                Recurring r = new Recurring(); r.name = "示例：每月房租"; r.amount = 1200; r.type = "支出"; r.category = "住房"; r.account = "银行卡"; r.day = 1; addRecurring(r);
            }
        }
        public void addRecurring(Recurring r) {
            ContentValues v = new ContentValues(); v.put("name", r.name); v.put("amount", r.amount); v.put("type", r.type); v.put("category", r.category); v.put("account", r.account); v.put("day", r.day); v.put("last_month", "");
            getWritableDatabase().insert("recurring", null, v);
        }
        public List<Recurring> listRecurring() {
            List<Recurring> list = new ArrayList<>(); Cursor c = getReadableDatabase().query("recurring", null, null, null, null, null, "day ASC");
            while (c.moveToNext()) { Recurring r = new Recurring(); r.id = c.getLong(c.getColumnIndexOrThrow("id")); r.name = c.getString(c.getColumnIndexOrThrow("name")); r.amount = c.getDouble(c.getColumnIndexOrThrow("amount")); r.type = c.getString(c.getColumnIndexOrThrow("type")); r.category = c.getString(c.getColumnIndexOrThrow("category")); r.account = c.getString(c.getColumnIndexOrThrow("account")); r.day = c.getInt(c.getColumnIndexOrThrow("day")); r.lastMonth = c.getString(c.getColumnIndexOrThrow("last_month")); list.add(r); }
            c.close(); return list;
        }
        public int runDueRecurring() {
            int count = 0; Calendar cal = Calendar.getInstance(); int today = cal.get(Calendar.DAY_OF_MONTH); String month = new SimpleDateFormat("yyyy-MM", Locale.CHINA).format(new Date());
            for (Recurring r : listRecurring()) {
                if (today >= r.day && !month.equals(r.lastMonth)) {
                    Entry e = new Entry(); e.amount = r.amount; e.type = r.type; e.category = r.category; e.account = r.account; e.note = r.name; e.source = "周期自动入账"; insert(e);
                    ContentValues v = new ContentValues(); v.put("last_month", month); getWritableDatabase().update("recurring", v, "id=?", new String[]{String.valueOf(r.id)}); count++;
                }
            }
            return count;
        }
    }

    public static class Csv {
        public static String toCsv(List<Entry> entries) {
            StringBuilder sb = new StringBuilder("date,type,category,account,amount,note,source\n");
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            for (Entry e : entries) sb.append(f.format(new Date(e.time))).append(',').append(e.type).append(',').append(e.category).append(',').append(e.account).append(',').append(e.amount).append(',').append(esc(e.note)).append(',').append(esc(e.source)).append('\n');
            return sb.toString();
        }
        public static List<Entry> fromCsv(String csv) {
            List<Entry> list = new ArrayList<>(); if (csv == null) return list;
            String[] lines = csv.split("\\n"); SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            for (String line : lines) {
                if (line.toLowerCase(Locale.ROOT).startsWith("date,") || line.trim().isEmpty()) continue;
                String[] p = line.split(",", -1); if (p.length < 5) continue;
                Entry e = new Entry();
                try { e.time = f.parse(p[0].trim()).getTime(); } catch (Exception ignored) {}
                e.type = p[1].trim().isEmpty() ? "支出" : p[1].trim(); e.category = p[2].trim().isEmpty() ? "其他" : p[2].trim(); e.account = p[3].trim().isEmpty() ? "其他" : p[3].trim(); e.amount = SmartParser.toDouble(p[4].trim()); e.note = p.length > 5 ? p[5].replace("\"", "") : ""; e.source = "CSV导入";
                if (e.amount > 0) list.add(e);
            }
            return list;
        }
        private static String esc(String s) { if (s == null) return ""; return '"' + s.replace("\"", "\"\"") + '"'; }
    }

    public static class ChartView extends View {
        static final int MODE_PIE = 1, MODE_BAR = 2;
        private int mode = MODE_PIE;
        private Map<String, Double> values = new LinkedHashMap<>();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = {PRIMARY, ACCENT, WARNING, DANGER, Color.rgb(118, 90, 224), Color.rgb(56, 180, 230), Color.rgb(255, 128, 128), Color.rgb(80, 200, 130)};
        public ChartView(Context c) { super(c); setPadding(8, 8, 8, 8); }
        public void setMode(int m) { mode = m; invalidate(); }
        public void setValues(Map<String, Double> v) { values = v == null ? new LinkedHashMap<>() : v; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); p.setTextSize(28); p.setColor(Color.rgb(105, 110, 130));
            if (values.isEmpty()) { p.setTextAlign(Paint.Align.CENTER); c.drawText("暂无数据", getWidth() / 2f, getHeight() / 2f, p); return; }
            if (mode == MODE_PIE) drawPie(c); else drawBar(c);
        }
        private void drawPie(Canvas c) {
            float total = 0; for (double v : values.values()) total += v;
            RectF oval = new RectF(30, 20, Math.min(getWidth() * 0.55f, getHeight() - 20), Math.min(getWidth() * 0.55f, getHeight() - 20));
            float start = -90; int i = 0;
            for (Map.Entry<String, Double> e : values.entrySet()) { p.setColor(colors[i % colors.length]); float sweep = (float)(e.getValue() / total * 360f); c.drawArc(oval, start, sweep, true, p); start += sweep; i++; }
            i = 0; float y = 38; p.setTextAlign(Paint.Align.LEFT); p.setTextSize(24);
            for (Map.Entry<String, Double> e : values.entrySet()) { p.setColor(colors[i % colors.length]); c.drawRoundRect(getWidth() * 0.62f, y - 18, getWidth() * 0.62f + 22, y + 4, 8, 8, p); p.setColor(PRIMARY_DARK); c.drawText(e.getKey() + "  ¥" + String.format(Locale.CHINA, "%.0f", e.getValue()), getWidth() * 0.62f + 32, y, p); y += 34; i++; if (y > getHeight() - 20) break; }
        }
        private void drawBar(Canvas c) {
            double max = 1; for (double v : values.values()) max = Math.max(max, v);
            int n = values.size(); float gap = 14; float w = (getWidth() - gap * (n + 1)) / n; float base = getHeight() - 42; int i = 0; p.setTextAlign(Paint.Align.CENTER); p.setTextSize(22);
            for (Map.Entry<String, Double> e : values.entrySet()) { float left = gap + i * (w + gap); float h = (float)(e.getValue() / max * (getHeight() - 80)); p.setColor(colors[i % colors.length]); c.drawRoundRect(left, base - h, left + w, base, 12, 12, p); p.setColor(Color.rgb(105, 110, 130)); c.drawText(e.getKey(), left + w / 2, getHeight() - 12, p); i++; }
        }
    }

    public static class ProgressView extends View {
        private float progress = 0; private int color = ACCENT; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public ProgressView(Context c) { super(c); }
        public void setProgress(float progress, int color) { this.progress = progress; this.color = color; invalidate(); }
        @Override protected void onDraw(Canvas c) { p.setColor(Color.rgb(230, 235, 245)); c.drawRoundRect(0, 0, getWidth(), getHeight(), getHeight()/2f, getHeight()/2f, p); p.setColor(color); c.drawRoundRect(0, 0, getWidth()*progress, getHeight(), getHeight()/2f, getHeight()/2f, p); }
    }

    public static class AutoLedgerNotificationService extends NotificationListenerService {
        @Override public void onNotificationPosted(StatusBarNotification sbn) {
            if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;
            CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
            CharSequence text = sbn.getNotification().extras.getCharSequence("android.text");
            String pkg = sbn.getPackageName() == null ? "" : sbn.getPackageName();
            String content = (title == null ? "" : title.toString()) + " " + (text == null ? "" : text.toString());
            if (!shouldParse(pkg, content)) return;
            Entry e = SmartParser.parseOne(content);
            if (e == null) return;
            e.source = "通知自动记账";
            String hash = pkg + "|" + e.amount + "|" + e.note;
            SharedPreferences sp = getSharedPreferences("auto_ledger", MODE_PRIVATE);
            if (hash.equals(sp.getString("last_hash", ""))) return;
            sp.edit().putString("last_hash", hash).apply();
            new LedgerDb(this).insert(e);
        }
        private boolean shouldParse(String pkg, String s) {
            String all = (pkg + " " + s).toLowerCase(Locale.ROOT);
            return all.contains("alipay") || all.contains("tencent.mm") || all.contains("bank") || s.contains("支付") || s.contains("付款") || s.contains("消费") || s.contains("收入") || s.contains("到账") || s.contains("收款");
        }
    }
}
