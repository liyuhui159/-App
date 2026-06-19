package com.liyuhui.smartledger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
    static final int BG = Color.rgb(244, 247, 251);
    static final int PRIMARY = Color.rgb(91, 95, 239);
    static final int PRIMARY_DARK = Color.rgb(34, 36, 77);
    static final int ACCENT = Color.rgb(0, 194, 168);
    static final int DANGER = Color.rgb(245, 91, 91);
    static final int WARNING = Color.rgb(255, 181, 71);
    static final String[] CATEGORIES = {"餐饮", "交通", "购物", "住房", "学习", "医疗", "娱乐", "通讯", "人情", "工资", "退款", "其他"};
    static final String[] ACCOUNTS = {"微信", "支付宝", "银行卡", "信用卡", "现金", "其他"};
    public static final String PREFS = "ledger_settings";
    public static final String KEY_REVIEW_MODE = "review_mode";
    public static final String KEY_CHART_PERIOD = "chart_period";
    public static final String MODE_OVERLAY = "overlay";
    public static final String MODE_NOTIFICATION = "notification";

    private LedgerDb db;
    private LinearLayout page;
    private String currentTab = "看板";
    private String chartPeriod = "日";
    private final SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private final SimpleDateFormat mdFmt = new SimpleDateFormat("MM-dd", Locale.CHINA);

    public static String reviewMode(Context c) { return c.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_REVIEW_MODE, MODE_OVERLAY); }
    public static boolean useBottomOverlay(Context c) { return MODE_OVERLAY.equals(reviewMode(c)); }
    public static void setReviewMode(Context c, String mode) { c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_REVIEW_MODE, mode).apply(); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new LedgerDb(this);
        chartPeriod = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CHART_PERIOD, "日");
        requestNotifyPermissionIfNeeded();
        int cleaned = db.cleanFalseNotificationEntries();
        buildFrame();
        showDashboard();
        QuickNotificationHelper.show(this);
        if (cleaned > 0) toast("已清理 " + cleaned + " 条误识别通知流水");
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) { QuickNotificationHelper.show(this); if (page != null && "看板".equals(currentTab)) showDashboard(); }
    }

    private void requestNotifyPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
    }

    private void buildFrame() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(16));
        header.setBackground(gradient(PRIMARY, Color.rgb(109, 82, 232)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        header.addView(text("灵犀记账", 28, Color.WHITE, true));
        header.addView(text("自动识别 · 智能分类 · 3D 图表 · 本地隐私", 14, Color.argb(230, 255, 255, 255), false));
        LinearLayout quick = new LinearLayout(this); quick.setOrientation(LinearLayout.HORIZONTAL); quick.setPadding(0, dp(12), 0, 0); header.addView(quick);
        Button quickAdd = pill("+ 快速记账", Color.argb(235, 255, 255, 255), PRIMARY);
        quickAdd.setOnClickListener(v -> startActivity(new Intent(this, QuickEntryActivity.class).putExtra("source", "首页快捷记账")));
        quick.addView(quickAdd, new LinearLayout.LayoutParams(0, dp(44), 1)); addGap(quick, 10);
        Button power = pill("开启权限", Color.argb(70, 255, 255, 255), Color.WHITE);
        power.setOnClickListener(v -> showPermissionDialog()); quick.addView(power, new LinearLayout.LayoutParams(0, dp(44), 1));
        HorizontalScrollView navScroll = new HorizontalScrollView(this); navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = new LinearLayout(this); nav.setPadding(dp(12), dp(10), dp(12), dp(8)); navScroll.addView(nav); root.addView(navScroll, new LinearLayout.LayoutParams(-1, -2));
        for (String tab : new String[]{"看板", "记账", "智能导入", "流水", "预算", "设置"}) {
            Button b = pill(tab, tab.equals(currentTab) ? Color.argb(225, 91, 95, 239) : Color.argb(185, 255, 255, 255), tab.equals(currentTab) ? Color.WHITE : PRIMARY_DARK);
            b.setOnClickListener(v -> { currentTab = ((Button) v).getText().toString(); buildFrame(); openTab(currentTab); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(96), dp(42)); lp.setMargins(0, 0, dp(8), 0); nav.addView(b, lp);
        }
        ScrollView scroll = new ScrollView(this);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(14), dp(4), dp(14), dp(28)); scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void openTab(String tab) { if ("看板".equals(tab)) showDashboard(); else if ("记账".equals(tab)) showAddEntry(); else if ("智能导入".equals(tab)) showSmartImport(); else if ("流水".equals(tab)) showRecords("", "全部"); else if ("预算".equals(tab)) showBudget(); else showSettings(); }

    private void showDashboard() {
        currentTab = "看板"; page.removeAllViews();
        long anchor = db.latestEntryTime();
        String month = monthFmt.format(new Date(anchor));
        Totals totals = db.monthTotals(month);
        addSectionTitle("本月总览 · " + month);
        LinearLayout row1 = row(); row1.addView(statCard("收入", money(totals.income), ACCENT), new LinearLayout.LayoutParams(0, dp(90), 1)); addGap(row1, 10); row1.addView(statCard("支出", money(totals.expense), DANGER), new LinearLayout.LayoutParams(0, dp(90), 1)); page.addView(row1);
        LinearLayout row2 = row(); row2.addView(statCard("结余", money(totals.income - totals.expense), PRIMARY), new LinearLayout.LayoutParams(0, dp(90), 1)); addGap(row2, 10); row2.addView(statCard("今日支出", money(totals.todayExpense), WARNING), new LinearLayout.LayoutParams(0, dp(90), 1)); page.addView(row2);

        addSectionTitle("流水支出叠加柱状图 · " + periodTitle(chartPeriod));
        page.addView(text("按日/周/月查看支出；点击柱状图某一柱可查看该周期全部流水", 13, Color.rgb(110,115,135), false));
        page.addView(periodSwitcher());
        List<PeriodRange> ranges = db.periodRanges(chartPeriod, anchor);
        StackedBrickChartView stacked = new StackedBrickChartView(this);
        stacked.setValues(db.categoryExpenseByPeriods(ranges));
        stacked.setBarClickListener((index, label) -> { if (index >= 0 && index < ranges.size()) showPeriodEntriesDialog(ranges.get(index)); });
        page.addView(cardWrap(stacked, dp(270)));

        PeriodRange active = db.activePeriod(chartPeriod, anchor);
        addSectionTitle("分类支出 3D 饼图 · " + active.label);
        page.addView(text("饼图跟随日/周/月周期切换；右侧标签可拖动，双指缩放，并自动记住位置", 13, Color.rgb(110,115,135), false));
        Pie3DView pie = new Pie3DView(this);
        pie.setValues(db.categoryExpenseBetween(active.start, active.end));
        pie.setOnClickListener(v -> showPeriodEntriesDialog(active));
        page.addView(cardWrap(pie, dp(350)));

        addSectionTitle("最近流水 · 按日期分组，可展开/收起");
        renderEntriesGrouped(db.listEntries(40, "", "全部"));
    }

    private String periodTitle(String p) { if ("周".equals(p)) return "最近 6 周"; if ("月".equals(p)) return "最近 6 个月"; return "最近 7 日"; }

    private LinearLayout periodSwitcher() {
        LinearLayout box = row(); box.setPadding(0, dp(8), 0, dp(4));
        for (String p : new String[]{"日", "周", "月"}) {
            Button b = pill(p, p.equals(chartPeriod) ? Color.argb(230, 91, 95, 239) : Color.argb(190, 255, 255, 255), p.equals(chartPeriod) ? Color.WHITE : PRIMARY_DARK);
            b.setOnClickListener(v -> { chartPeriod = ((Button) v).getText().toString(); getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_CHART_PERIOD, chartPeriod).apply(); showDashboard(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1); lp.setMargins(0, 0, dp(8), 0); box.addView(b, lp);
        }
        return box;
    }

    private void showPeriodEntriesDialog(PeriodRange pr) {
        List<Entry> list = db.entriesBetween(pr.start, pr.end);
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setPadding(dp(10), dp(8), dp(10), dp(8));
        double expense = 0, income = 0; for (Entry e : list) { if ("收入".equals(e.type)) income += e.amount; else expense += e.amount; }
        wrap.addView(text(pr.label + "  流水", 18, PRIMARY_DARK, true));
        wrap.addView(text("收入 " + money(income) + "    支出 " + money(expense), 14, Color.rgb(95,100,120), false));
        if (list.isEmpty()) wrap.addView(empty("该周期暂无流水")); else for (Entry e : list) wrap.addView(entryLine(e));
        ScrollView sv = new ScrollView(this); sv.addView(wrap);
        new AlertDialog.Builder(this).setView(sv).setPositiveButton("关闭", null).show();
    }

    private void showAddEntry() {
        currentTab = "记账"; page.removeAllViews(); addSectionTitle("手动记一笔");
        LinearLayout c = card(); Spinner type = spinner(new String[]{"支出", "收入"}); Spinner category = spinner(CATEGORIES); Spinner account = spinner(ACCOUNTS); EditText amount = input("金额，例如 18.50", true); EditText note = input("备注，例如 肯德基 / 微信转账 - A", false); EditText date = input("日期 yyyy-MM-dd，留空默认今天", false);
        c.addView(label("收支类型")); c.addView(type); c.addView(label("分类")); c.addView(category); c.addView(label("账户")); c.addView(account); c.addView(label("金额")); c.addView(amount); c.addView(label("备注")); c.addView(note); c.addView(label("日期")); c.addView(date);
        Button save = primaryButton("保存账目"); save.setOnClickListener(v -> { double a = parseDouble(amount.getText().toString()); if (a <= 0) { toast("金额必须大于 0"); return; } Entry e = new Entry(); e.amount = a; e.type = type.getSelectedItem().toString(); e.category = category.getSelectedItem().toString(); e.account = account.getSelectedItem().toString(); e.note = note.getText().toString().trim(); e.source = "手动记账"; e.time = parseDate(date.getText().toString().trim()); if ("其他".equals(e.category)) e.category = SmartParser.inferCategory(e.note, e.type); db.insert(e); QuickNotificationHelper.show(this); toast("已保存"); showDashboard(); });
        c.addView(save, new LinearLayout.LayoutParams(-1, dp(48))); page.addView(c);
    }

    private void showSmartImport() {
        currentTab = "智能导入"; page.removeAllViews(); addSectionTitle("智能导入");
        page.addView(text("粘贴支付宝、微信、银行或外卖账单文本；系统会自动去噪、识别金额和分类。", 14, Color.rgb(95, 100, 120), false));
        EditText raw = input("例如：微信 红包金额0.01元 等待对方领取", false); raw.setGravity(Gravity.TOP); raw.setMinLines(7); page.addView(raw, new LinearLayout.LayoutParams(-1, dp(170)));
        LinearLayout resultBox = card(); page.addView(resultBox);
        Button parse = primaryButton("智能解析"); parse.setOnClickListener(v -> { resultBox.removeAllViews(); List<Entry> entries = SmartParser.parse(raw.getText().toString()); if (entries.isEmpty()) { resultBox.addView(text("没有识别到账单金额。", 15, DANGER, true)); return; } resultBox.addView(text("识别结果：" + entries.size() + " 条", 17, PRIMARY_DARK, true)); for (Entry e : entries) resultBox.addView(entryLine(e)); Button saveAll = primaryButton("全部入账"); saveAll.setOnClickListener(x -> { for (Entry e : entries) db.insert(e); QuickNotificationHelper.show(this); toast("已导入 " + entries.size() + " 条"); showDashboard(); }); resultBox.addView(saveAll, new LinearLayout.LayoutParams(-1, dp(48))); });
        page.addView(parse, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void showRecords(String query, String typeFilter) {
        currentTab = "流水"; page.removeAllViews(); addSectionTitle("交易流水 · 按日期分组");
        LinearLayout tools = card(); EditText search = input("搜索备注、分类、账户", false); search.setText(query); Spinner type = spinner(new String[]{"全部", "支出", "收入"}); type.setSelection("收入".equals(typeFilter) ? 2 : "支出".equals(typeFilter) ? 1 : 0);
        Button apply = primaryButton("筛选"); apply.setOnClickListener(v -> showRecords(search.getText().toString().trim(), type.getSelectedItem().toString())); Button export = secondaryButton("导出 CSV / 分享"); export.setOnClickListener(v -> shareCsv()); Button clean = secondaryButton("清理误识别通知"); clean.setOnClickListener(v -> { int n = db.cleanFalseNotificationEntries(); toast("已清理 " + n + " 条"); showRecords("", "全部"); });
        tools.addView(search); tools.addView(type); tools.addView(apply); tools.addView(export); tools.addView(clean); page.addView(tools);
        renderEntriesGrouped(db.listEntries(300, query, typeFilter));
    }

    private void showBudget() {
        currentTab = "预算"; page.removeAllViews(); String month = monthFmt.format(new Date(db.latestEntryTime())); addSectionTitle("月度预算 · " + month);
        LinearLayout form = card(); Spinner category = spinner(CATEGORIES); EditText amount = input("预算金额，例如 1200", true); Button save = primaryButton("保存预算");
        save.setOnClickListener(v -> { double a = parseDouble(amount.getText().toString()); if (a <= 0) { toast("预算金额必须大于 0"); return; } db.setBudget(month, category.getSelectedItem().toString(), a); toast("预算已保存"); showBudget(); });
        form.addView(label("分类")); form.addView(category); form.addView(label("预算金额")); form.addView(amount); form.addView(save); page.addView(form);
        addSectionTitle("执行情况"); Map<String, Double> spend = db.categoryExpense(month); List<Budget> budgets = db.listBudgets(month); if (budgets.isEmpty()) page.addView(empty("还没有预算，先添加一个。"));
        for (Budget b : budgets) { double used = spend.containsKey(b.category) ? spend.get(b.category) : 0; LinearLayout c = card(); c.addView(text(iconForCategory(b.category) + " " + b.category + "  " + money(used) + " / " + money(b.amount), 16, PRIMARY_DARK, true)); ProgressView p = new ProgressView(this); p.setProgress((float) Math.min(1, used / Math.max(1, b.amount)), used > b.amount ? DANGER : ACCENT); c.addView(p, new LinearLayout.LayoutParams(-1, dp(18))); if (used > b.amount) c.addView(text("已超支 " + money(used - b.amount), 13, DANGER, true)); page.addView(c); }
    }

    private void showSettings() {
        currentTab = "设置"; page.removeAllViews(); addSectionTitle("权限与自动识别");
        LinearLayout c = card(); c.addView(text("建议同时开启通知权限、通知使用权和无障碍服务。", 15, PRIMARY_DARK, true)); c.addView(text("通知栏会显示今日收入、支出和结余；识别提醒可二选一。", 13, Color.rgb(95, 100, 120), false));
        Button a = primaryButton("开启无障碍服务"); a.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); Button n = secondaryButton("开启通知使用权"); n.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); Button refresh = secondaryButton("刷新通知栏今日收支"); refresh.setOnClickListener(v -> { QuickNotificationHelper.show(this); toast("通知栏已刷新"); }); Button clean = secondaryButton("清理误识别通知流水"); clean.setOnClickListener(v -> toast("已清理 " + db.cleanFalseNotificationEntries() + " 条"));
        c.addView(a, new LinearLayout.LayoutParams(-1, dp(48))); c.addView(n, new LinearLayout.LayoutParams(-1, dp(48))); c.addView(refresh, new LinearLayout.LayoutParams(-1, dp(48))); c.addView(clean, new LinearLayout.LayoutParams(-1, dp(48))); page.addView(c);
        addSectionTitle("识别提醒方式 · 二选一"); LinearLayout mode = card(); CheckBox bottom = new CheckBox(this); bottom.setText("底部玻璃弹窗：识别后在当前页面底部弹出确认卡"); bottom.setTextSize(15); bottom.setTextColor(PRIMARY_DARK); CheckBox notify = new CheckBox(this); notify.setText("通知栏确认：识别后只发通知，不弹底部卡片"); notify.setTextSize(15); notify.setTextColor(PRIMARY_DARK); boolean useOverlay = useBottomOverlay(this); bottom.setChecked(useOverlay); notify.setChecked(!useOverlay); bottom.setOnClickListener(v -> { setReviewMode(this, MODE_OVERLAY); bottom.setChecked(true); notify.setChecked(false); toast("已切换为底部玻璃弹窗"); }); notify.setOnClickListener(v -> { setReviewMode(this, MODE_NOTIFICATION); notify.setChecked(true); bottom.setChecked(false); toast("已切换为通知栏确认"); QuickNotificationHelper.show(this); }); mode.addView(bottom); mode.addView(notify); mode.addView(text("说明：通知主体点其他区域进入 App 首页；通知按钮进入编辑确认页。", 12, Color.rgb(100, 105, 125), false)); page.addView(mode);
    }

    private void renderEntriesGrouped(List<Entry> entries) {
        if (entries.isEmpty()) { page.addView(empty("暂无账目。")); return; }
        LinkedHashMap<String, List<Entry>> grouped = new LinkedHashMap<>();
        for (Entry e : entries) { String d = dateFmt.format(new Date(e.time)); if (!grouped.containsKey(d)) grouped.put(d, new ArrayList<>()); grouped.get(d).add(e); }
        for (Map.Entry<String, List<Entry>> g : grouped.entrySet()) page.addView(dateGroupView(g.getKey(), g.getValue()));
    }

    private View dateGroupView(String date, List<Entry> list) {
        LinearLayout outer = card();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean expanded = sp.getBoolean("group_expand_" + date, true);
        double expense = 0, income = 0; for (Entry e : list) { if ("收入".equals(e.type)) income += e.amount; else expense += e.amount; }
        TextView header = text((expanded ? "▼ " : "▶ ") + date + "  共 " + list.size() + " 笔    支出 " + money(expense) + (income > 0 ? "    收入 " + money(income) : ""), 15, PRIMARY_DARK, true);
        header.setPadding(0, 0, 0, dp(8)); outer.addView(header);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); outer.addView(body);
        if (expanded) for (Entry e : list) body.addView(entryLine(e)); else body.setVisibility(View.GONE);
        header.setOnClickListener(v -> { boolean now = !sp.getBoolean("group_expand_" + date, true); sp.edit().putBoolean("group_expand_" + date, now).apply(); if ("流水".equals(currentTab)) showRecords("", "全部"); else showDashboard(); });
        return outer;
    }

    private View entryLine(Entry e) { LinearLayout c = card(); LinearLayout top = row(); TextView left = text(iconForCategory(e.category) + " " + e.category + " · " + e.account, 16, PRIMARY_DARK, true); TextView right = text(("收入".equals(e.type) ? "+" : "-") + money(e.amount), 17, "收入".equals(e.type) ? ACCENT : DANGER, true); top.addView(left, new LinearLayout.LayoutParams(0, -2, 1)); top.addView(right); c.addView(top); c.addView(text((e.note == null || e.note.isEmpty() ? "无备注" : e.note) + "  ·  " + dateFmt.format(new Date(e.time)) + "  ·  " + e.source, 13, Color.rgb(105, 110, 130), false)); c.setOnClickListener(v -> { Intent i = new Intent(this, EntryDetailActivity.class); i.putExtra("id", e.id); i.putExtra("amount", e.amount); i.putExtra("type", e.type); i.putExtra("category", e.category); i.putExtra("account", e.account); i.putExtra("note", e.note); i.putExtra("source", e.source); i.putExtra("time", e.time); startActivity(i); }); c.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("删除账目").setMessage("确定删除这条记录吗？").setPositiveButton("删除", (d, w) -> { db.delete(e.id); QuickNotificationHelper.show(this); if ("流水".equals(currentTab)) showRecords("", "全部"); else showDashboard(); }).setNegativeButton("取消", null).show(); return true; }); return c; }
    private void showPermissionDialog() { new AlertDialog.Builder(this).setTitle("开启自动记账权限").setMessage("请开启：1. 通知权限；2. 通知使用权；3. 无障碍服务。").setPositiveButton("无障碍", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("通知使用权", (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).show(); }
    private void shareCsv() { String csv = Csv.toCsv(db.listEntries(5000, "", "全部")); Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/csv"); send.putExtra(Intent.EXTRA_SUBJECT, "灵犀记账CSV导出"); send.putExtra(Intent.EXTRA_TEXT, csv); startActivity(Intent.createChooser(send, "导出/分享 CSV")); }

    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(14), dp(14), dp(14), dp(14)); c.setBackground(glassRound(Color.argb(205, 255, 255, 255), 20)); c.setElevation(dp(5)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, dp(8)); c.setLayoutParams(lp); return c; }
    private View cardWrap(View child, int height) { LinearLayout c = card(); c.addView(child, new LinearLayout.LayoutParams(-1, height)); return c; }
    private LinearLayout statCard(String title, String value, int color) { LinearLayout c = card(); c.addView(text(title, 13, Color.rgb(105, 110, 130), false)); c.addView(text(value, 22, color, true)); return c; }
    private void addSectionTitle(String s) { TextView t = text(s, 18, PRIMARY_DARK, true); t.setPadding(dp(2), dp(14), dp(2), dp(6)); page.addView(t); }
    private TextView label(String s) { TextView t = text(s, 13, Color.rgb(95, 100, 120), true); t.setPadding(0, dp(10), 0, dp(4)); return t; }
    private TextView empty(String s) { TextView t = text(s, 15, Color.rgb(120, 125, 145), false); t.setGravity(Gravity.CENTER); t.setPadding(dp(16), dp(24), dp(16), dp(24)); t.setBackground(glassRound(Color.argb(205, 255, 255, 255), 18)); return t; }
    private EditText input(String hint, boolean number) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(15); e.setSingleLine(false); e.setPadding(dp(12), 0, dp(12), 0); e.setBackground(glassRound(Color.argb(175, 248, 250, 255), 16)); e.setInputType(number ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL) : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); return e; }
    private Spinner spinner(String[] items) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); s.setBackground(glassRound(Color.argb(175, 248, 250, 255), 16)); return s; }
    private Button primaryButton(String text) { return pill(text, Color.argb(225, 91, 95, 239), Color.WHITE); }
    private Button secondaryButton(String text) { return pill(text, Color.argb(180, 255, 255, 255), PRIMARY); }
    private Button pill(String text, int bg, int fg) { Button b = new Button(this); b.setAllCaps(false); b.setText(text); b.setTextSize(14); b.setTextColor(fg); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(glassRound(bg, 18)); b.setElevation(dp(3)); b.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).start(); else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(80).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start(); return false; }); return b; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setLineSpacing(dp(2), 1f); return t; }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }
    private void addGap(LinearLayout parent, int width) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(dp(width), 1)); }
    private GradientDrawable glassRound(int color, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.argb(230, 255, 255, 255), color}); g.setCornerRadius(dp(radius)); g.setStroke(dp(1), Color.argb(130, 255, 255, 255)); return g; }
    private GradientDrawable gradient(int c1, int c2) { return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2}); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private String money(double v) { return "¥" + String.format(Locale.CHINA, "%.2f", v); }
    private double parseDouble(String s) { try { return Double.parseDouble(s.replace("¥", "").trim()); } catch (Exception e) { return 0; } }
    private long parseDate(String s) { try { if (!s.isEmpty()) return dateFmt.parse(s).getTime(); } catch (Exception ignored) {} return System.currentTimeMillis(); }
    public static String iconForCategory(String c) { if ("餐饮".equals(c)) return "🍜"; if ("交通".equals(c)) return "🚗"; if ("购物".equals(c)) return "🛍️"; if ("住房".equals(c)) return "🏠"; if ("学习".equals(c)) return "📚"; if ("医疗".equals(c)) return "💊"; if ("娱乐".equals(c)) return "🎮"; if ("通讯".equals(c)) return "📱"; if ("人情".equals(c)) return "🧧"; if ("工资".equals(c)) return "💼"; if ("退款".equals(c)) return "↩️"; return "📌"; }

    public static class Entry { public long id; public double amount; public String type = "支出"; public String category = "其他"; public String account = "其他"; public String note = ""; public String source = "手动记账"; public long time = System.currentTimeMillis(); }
    public static class Totals { double income, expense, todayExpense; }
    public static class Budget { String category; double amount; }
    public static class PeriodRange { String label; long start; long end; PeriodRange(String l, long s, long e) { label = l; start = s; end = e; } }

    public static class SmartParser {
        public static List<Entry> parse(String raw) { List<Entry> result = new ArrayList<>(); if (raw == null) return result; for (String line : raw.split("\\n|；|;。")) { Entry e = parseOne(line.trim()); if (e != null) result.add(e); } return result; }
        public static Entry parseOne(String line) { if (line == null || line.trim().isEmpty()) return null; double chosen = 0; Matcher money = Pattern.compile("[¥￥]?\\s*([-+]?\\d+(?:\\.\\d{1,2})?)\\s*(?:元)?").matcher(line.replace(",", "")); while (money.find()) { double v = Math.abs(toDouble(money.group(1))); if (v > 0 && v < 100000000) { chosen = v; break; } } if (chosen <= 0) return null; Entry e = new Entry(); e.amount = chosen; e.type = inferType(line); e.category = inferCategory(line, e.type); e.account = inferAccount(line); e.note = cleanNote(line); e.source = "智能解析"; e.time = inferDate(line); return e; }
        private static String inferType(String s) { String t = s.toLowerCase(Locale.ROOT); if (s.contains("收入") || s.contains("工资") || s.contains("到账") || s.contains("收款") || s.contains("退款") || s.contains("奖金") || t.contains("income") || s.contains("+")) return "收入"; return "支出"; }
        public static String inferCategory(String s, String type) { if (s == null) return "其他"; String lower = s.toLowerCase(Locale.ROOT); if ("收入".equals(type)) { if (has(s, "工资", "薪", "奖金")) return "工资"; if (has(s, "退款", "退回")) return "退款"; return "其他"; } if (has(s, "红包", "转账", "礼物", "请客", "份子", "恭喜发财", "大吉大利")) return "人情"; if (has(s, "腾讯天游", "腾讯", "游戏", "手游", "和平精英", "王者荣耀", "点券", "充值", "会员", "电影", "KTV", "旅游")) return "娱乐"; if (has(s, "超市", "便利店", "华联", "永辉", "沃尔玛", "盒马", "店内购物", "淘宝", "天猫", "京东", "拼多多", "购物", "衣服", "鞋", "数码", "抖音商城")) return "购物"; if (has(s, "兰州拉面", "拉面", "面馆", "清真", "肯德基", "麦当劳", "必胜客", "汉堡王", "瑞幸", "星巴克", "蜜雪", "喜茶", "奈雪", "茶百道", "古茗", "美团", "饿了么", "外卖", "饭", "餐", "食堂", "咖啡", "奶茶", "火锅", "烧烤", "小吃") || lower.contains("kfc") || lower.contains("mcdonald")) return "餐饮"; if (has(s, "滴滴", "打车", "公交", "地铁", "高铁", "火车", "机票", "油费", "停车", "加油", "高速")) return "交通"; if (has(s, "房租", "水电", "物业", "燃气", "宽带", "租金")) return "住房"; if (has(s, "书", "课程", "学费", "考试", "培训", "文具")) return "学习"; if (has(s, "医院", "药", "挂号", "医保", "体检")) return "医疗"; if (has(s, "话费", "流量", "通讯")) return "通讯"; return "其他"; }
        private static boolean has(String s, String... keys) { for (String k : keys) if (s.contains(k)) return true; return false; }
        private static String inferAccount(String s) { if (s.contains("微信")) return "微信"; if (s.contains("支付宝") || s.contains("花呗")) return "支付宝"; if (s.contains("信用卡")) return "信用卡"; if (s.contains("银行") || s.contains("招商") || s.contains("工商") || s.contains("建设") || s.contains("农业")) return "银行卡"; if (s.contains("现金")) return "现金"; return "其他"; }
        private static String cleanNote(String s) { return s.replaceAll("[-+]?\\d+(?:\\.\\d{1,2})?", "").replace("¥", "").replace("￥", "").replace("元", "").replaceAll("\\s+", " ").trim(); }
        public static double toDouble(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
        private static long inferDate(String s) { Calendar c = Calendar.getInstance(); if (s.contains("昨天")) c.add(Calendar.DATE, -1); if (s.contains("前天")) c.add(Calendar.DATE, -2); Matcher m = Pattern.compile("(20\\d{2})[-/年](\\d{1,2})[-/月](\\d{1,2})").matcher(s); if (m.find()) { c.set(Calendar.YEAR, Integer.parseInt(m.group(1))); c.set(Calendar.MONTH, Integer.parseInt(m.group(2)) - 1); c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(m.group(3))); } return c.getTimeInMillis(); }
    }

    public static class LedgerDb extends SQLiteOpenHelper { public LedgerDb(Context c) { super(c, "smart_ledger.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL, type TEXT, category TEXT, account TEXT, note TEXT, source TEXT, time INTEGER)"); db.execSQL("CREATE TABLE budgets(id INTEGER PRIMARY KEY AUTOINCREMENT, month TEXT, category TEXT, amount REAL, UNIQUE(month, category))"); db.execSQL("CREATE TABLE recurring(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, amount REAL, type TEXT, category TEXT, account TEXT, day INTEGER, last_month TEXT)"); }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
        public long insert(Entry e) { ContentValues v = new ContentValues(); v.put("amount", e.amount); v.put("type", e.type); v.put("category", e.category); v.put("account", e.account); v.put("note", e.note); v.put("source", e.source); v.put("time", e.time); return getWritableDatabase().insert("entries", null, v); }
        public void delete(long id) { getWritableDatabase().delete("entries", "id=?", new String[]{String.valueOf(id)}); }
        public int cleanFalseNotificationEntries() { return getWritableDatabase().delete("entries", "source=? AND (note LIKE ? OR note LIKE ? OR note LIKE ? OR note LIKE ? OR note LIKE ?)", new String[]{"通知自动记账", "%Bytes/s%", "%US%", "%VPN%", "%KiB/s%", "%HUAWEI WATCH%"}); }
        public long latestEntryTime() { Cursor c = getReadableDatabase().rawQuery("SELECT MAX(time) FROM entries", null); long t = System.currentTimeMillis(); if (c.moveToFirst() && !c.isNull(0)) t = c.getLong(0); c.close(); return t; }
        public List<Entry> listEntries(int limit, String query, String type) { List<Entry> list = new ArrayList<>(); StringBuilder where = new StringBuilder("1=1"); List<String> args = new ArrayList<>(); if (query != null && !query.isEmpty()) { where.append(" AND (note LIKE ? OR category LIKE ? OR account LIKE ?)"); String q = "%" + query + "%"; args.add(q); args.add(q); args.add(q); } if (type != null && !"全部".equals(type)) { where.append(" AND type=?"); args.add(type); } Cursor c = getReadableDatabase().query("entries", null, where.toString(), args.toArray(new String[0]), null, null, "time DESC, id DESC", String.valueOf(limit)); while (c.moveToNext()) list.add(readEntry(c)); c.close(); return list; }
        public List<Entry> entriesBetween(long start, long end) { List<Entry> list = new ArrayList<>(); Cursor c = getReadableDatabase().query("entries", null, "time>=? AND time<?", new String[]{String.valueOf(start), String.valueOf(end)}, null, null, "time DESC, id DESC"); while (c.moveToNext()) list.add(readEntry(c)); c.close(); return list; }
        private Entry readEntry(Cursor c) { Entry e = new Entry(); e.id = c.getLong(c.getColumnIndexOrThrow("id")); e.amount = c.getDouble(c.getColumnIndexOrThrow("amount")); e.type = c.getString(c.getColumnIndexOrThrow("type")); e.category = c.getString(c.getColumnIndexOrThrow("category")); e.account = c.getString(c.getColumnIndexOrThrow("account")); e.note = c.getString(c.getColumnIndexOrThrow("note")); e.source = c.getString(c.getColumnIndexOrThrow("source")); e.time = c.getLong(c.getColumnIndexOrThrow("time")); return e; }
        public Totals monthTotals(String month) { Totals t = new Totals(); String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date()); for (Entry e : listEntries(10000, "", "全部")) { String m = new SimpleDateFormat("yyyy-MM", Locale.CHINA).format(new Date(e.time)); if (!month.equals(m)) continue; if ("收入".equals(e.type)) t.income += e.amount; else t.expense += e.amount; String d = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(e.time)); if (today.equals(d) && "支出".equals(e.type)) t.todayExpense += e.amount; } return t; }
        public Map<String, Double> categoryExpense(String month) { Map<String, Double> map = new LinkedHashMap<>(); SimpleDateFormat f = new SimpleDateFormat("yyyy-MM", Locale.CHINA); for (Entry e : listEntries(10000, "", "支出")) { if (!month.equals(f.format(new Date(e.time)))) continue; addMap(map, e.category, e.amount); } return map; }
        public PeriodRange activePeriod(String period, long anchor) { List<PeriodRange> ranges = periodRanges(period, anchor); return ranges.get(ranges.size() - 1); }
        public List<PeriodRange> periodRanges(String period, long anchor) { List<PeriodRange> list = new ArrayList<>(); Calendar base = Calendar.getInstance(); base.setTimeInMillis(anchor); if ("月".equals(period)) { base.set(Calendar.DAY_OF_MONTH, 1); zero(base); for (int i = 5; i >= 0; i--) { Calendar s = (Calendar) base.clone(); s.add(Calendar.MONTH, -i); Calendar e = (Calendar) s.clone(); e.add(Calendar.MONTH, 1); list.add(new PeriodRange(new SimpleDateFormat("yyyy-MM", Locale.CHINA).format(s.getTime()), s.getTimeInMillis(), e.getTimeInMillis())); } } else if ("周".equals(period)) { zero(base); base.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); for (int i = 5; i >= 0; i--) { Calendar s = (Calendar) base.clone(); s.add(Calendar.WEEK_OF_YEAR, -i); Calendar e = (Calendar) s.clone(); e.add(Calendar.DATE, 7); list.add(new PeriodRange(new SimpleDateFormat("MM-dd", Locale.CHINA).format(s.getTime()) + "周", s.getTimeInMillis(), e.getTimeInMillis())); } } else { zero(base); for (int i = 6; i >= 0; i--) { Calendar s = (Calendar) base.clone(); s.add(Calendar.DATE, -i); Calendar e = (Calendar) s.clone(); e.add(Calendar.DATE, 1); list.add(new PeriodRange(new SimpleDateFormat("MM-dd", Locale.CHINA).format(s.getTime()), s.getTimeInMillis(), e.getTimeInMillis())); } } return list; }
        private void zero(Calendar c) { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); }
        public Map<String, Map<String, Double>> categoryExpenseByPeriods(List<PeriodRange> ranges) { Map<String, Map<String, Double>> result = new LinkedHashMap<>(); for (PeriodRange r : ranges) result.put(r.label, new LinkedHashMap<>()); for (PeriodRange r : ranges) for (Entry e : entriesBetween(r.start, r.end)) if (!"收入".equals(e.type)) addMap(result.get(r.label), e.category, e.amount); return result; }
        public Map<String, Double> categoryExpenseBetween(long start, long end) { Map<String, Double> map = new LinkedHashMap<>(); for (Entry e : entriesBetween(start, end)) if (!"收入".equals(e.type)) addMap(map, e.category, e.amount); return map; }
        private void addMap(Map<String, Double> m, String k, double v) { if (k == null || k.length() == 0) k = "其他"; m.put(k, m.containsKey(k) ? m.get(k) + v : v); }
        public void setBudget(String month, String category, double amount) { ContentValues v = new ContentValues(); v.put("month", month); v.put("category", category); v.put("amount", amount); getWritableDatabase().insertWithOnConflict("budgets", null, v, SQLiteDatabase.CONFLICT_REPLACE); }
        public List<Budget> listBudgets(String month) { List<Budget> list = new ArrayList<>(); Cursor c = getReadableDatabase().query("budgets", null, "month=?", new String[]{month}, null, null, "category ASC"); while (c.moveToNext()) { Budget b = new Budget(); b.category = c.getString(c.getColumnIndexOrThrow("category")); b.amount = c.getDouble(c.getColumnIndexOrThrow("amount")); list.add(b); } c.close(); return list; }}

    public static class Csv { public static String toCsv(List<Entry> entries) { StringBuilder sb = new StringBuilder("date,type,category,account,amount,note,source\n"); SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA); for (Entry e : entries) sb.append(f.format(new Date(e.time))).append(',').append(e.type).append(',').append(e.category).append(',').append(e.account).append(',').append(e.amount).append(',').append('"').append(e.note == null ? "" : e.note.replace("\"", "\"\"")).append('"').append(',').append('"').append(e.source == null ? "" : e.source.replace("\"", "\"\"")).append('"').append('\n'); return sb.toString(); }}

    public static class StackedBrickChartView extends View { interface OnBarClick { void onClick(int index, String label); } private Map<String, Map<String, Double>> values = new LinkedHashMap<>(); private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private OnBarClick listener; private final int[] colors = {PRIMARY, ACCENT, WARNING, DANGER, Color.rgb(118,90,224), Color.rgb(56,180,230), Color.rgb(255,128,128), Color.rgb(80,200,130), Color.rgb(255,155,60), Color.rgb(90,130,255), Color.rgb(160,110,60), Color.rgb(120,120,145)}; public StackedBrickChartView(Context c) { super(c); } public void setValues(Map<String, Map<String, Double>> v) { values = v == null ? new LinkedHashMap<>() : v; invalidate(); } public void setBarClickListener(OnBarClick l) { listener = l; }
        @Override public boolean onTouchEvent(MotionEvent e) { if (e.getAction() == MotionEvent.ACTION_UP && listener != null && !values.isEmpty()) { int n = values.size(); float gap = 18, w = (getWidth() - gap * (n + 1)) / Math.max(1, n); int idx = (int) ((e.getX() - gap) / (w + gap)); if (idx >= 0 && idx < n) listener.onClick(idx, new ArrayList<>(values.keySet()).get(idx)); return true; } return true; }
        @Override protected void onDraw(Canvas c) { super.onDraw(c); if (values.isEmpty()) { drawEmpty(c); return; } double max = 1; for (Map<String, Double> m : values.values()) { double sum = 0; for (double v : m.values()) sum += v; max = Math.max(max, sum); } int n = values.size(); float gap = 18; float w = (getWidth() - gap * (n + 1)) / Math.max(1, n); float base = getHeight() - 42; float topLimit = 34; int day = 0; p.setTextAlign(Paint.Align.CENTER); for (Map.Entry<String, Map<String, Double>> dayEntry : values.entrySet()) { float x = gap + day * (w + gap); float y = base; int layer = 0; double sum = 0; for (double v : dayEntry.getValue().values()) sum += v; for (Map.Entry<String, Double> cat : dayEntry.getValue().entrySet()) { float h = (float) (cat.getValue() / max * (base - topLimit)); if (h < 5 && cat.getValue() > 0) h = 5; int color = colorForCategory(cat.getKey(), layer); drawBrick(c, x, y - h, w, h, color); y -= h; layer++; } if (sum > 0) { p.setColor(PRIMARY_DARK); p.setTextSize(18); c.drawText("¥" + String.format(Locale.CHINA, "%.0f", sum), x + w / 2, Math.max(22, y - 8), p); } p.setColor(Color.rgb(105,110,130)); p.setTextSize(20); c.drawText(dayEntry.getKey(), x + w / 2, getHeight() - 12, p); day++; } drawLegend(c); }
        private void drawBrick(Canvas c, float x, float y, float w, float h, int color) { float dx = 10, dy = -7; p.setColor(color); c.drawRoundRect(new RectF(x, y, x + w, y + h), 6, 6, p); Path top = new Path(); top.moveTo(x, y); top.lineTo(x + dx, y + dy); top.lineTo(x + w + dx, y + dy); top.lineTo(x + w, y); top.close(); p.setColor(light(color)); c.drawPath(top, p); Path side = new Path(); side.moveTo(x + w, y); side.lineTo(x + w + dx, y + dy); side.lineTo(x + w + dx, y + h + dy); side.lineTo(x + w, y + h); side.close(); p.setColor(dark(color)); c.drawPath(side, p); }
        private void drawLegend(Canvas c) { p.setTextAlign(Paint.Align.LEFT); p.setTextSize(18); float x = 12, y = 22; int shown = 0; for (String cat : CATEGORIES) { if (!hasCategory(cat)) continue; int color = colorForCategory(cat, shown); p.setColor(color); c.drawRoundRect(x, y - 14, x + 18, y + 4, 6, 6, p); p.setColor(PRIMARY_DARK); c.drawText(iconForCategory(cat) + cat, x + 24, y, p); x += 94; shown++; if (x > getWidth() - 95) { x = 12; y += 25; } if (shown >= 8) break; } }
        private boolean hasCategory(String cat) { for (Map<String, Double> m : values.values()) if (m.containsKey(cat)) return true; return false; }
        private int colorForCategory(String cat, int fallback) { for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(cat)) return colors[i % colors.length]; return colors[fallback % colors.length]; }
        private int light(int c){ return Color.rgb(Math.min(255,(int)(Color.red(c)*1.22)),Math.min(255,(int)(Color.green(c)*1.22)),Math.min(255,(int)(Color.blue(c)*1.22))); } private int dark(int c){ return Color.rgb((int)(Color.red(c)*0.67),(int)(Color.green(c)*0.67),(int)(Color.blue(c)*0.67)); } private void drawEmpty(Canvas c) { p.setColor(Color.rgb(105,110,130)); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(26); c.drawText("暂无数据", getWidth()/2f, getHeight()/2f, p); }}

    public static class Pie3DView extends View { private Map<String, Double> values = new LinkedHashMap<>(); private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private float startAngle = -80f, lastX, lastY, legendX, legendY, labelScale = 1f, pinchStart = 0f; private boolean draggingLegend = false; private SharedPreferences prefs; private final int[] colors = {PRIMARY, ACCENT, WARNING, DANGER, Color.rgb(118,90,224), Color.rgb(56,180,230), Color.rgb(255,128,128), Color.rgb(80,200,130), Color.rgb(255,155,60), Color.rgb(90,130,255), Color.rgb(160,110,60), Color.rgb(120,120,145)}; public Pie3DView(Context c){super(c); prefs=c.getSharedPreferences(PREFS, MODE_PRIVATE); legendX=prefs.getFloat("pie_label_x", -1f); legendY=prefs.getFloat("pie_label_y", 38f); labelScale=prefs.getFloat("pie_label_scale", 1f);} public void setValues(Map<String,Double> v){values=v==null?new LinkedHashMap<>():v;invalidate();}
        @Override public boolean onTouchEvent(MotionEvent e){ if(e.getPointerCount()>=2){ float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1); float dist=(float)Math.sqrt(dx*dx+dy*dy); if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN) pinchStart=dist; else if(e.getActionMasked()==MotionEvent.ACTION_MOVE && pinchStart>0){ labelScale=Math.max(0.7f,Math.min(1.8f,labelScale+(dist-pinchStart)/450f)); pinchStart=dist; prefs.edit().putFloat("pie_label_scale",labelScale).apply(); invalidate(); } return true; } if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY(); if(legendX<0) legendX=getWidth()*0.58f; draggingLegend=lastX>getWidth()*0.45f; return true;} if(e.getAction()==MotionEvent.ACTION_MOVE){ if(draggingLegend){ legendX+=e.getX()-lastX; legendY+=e.getY()-lastY; legendX=Math.max(4,Math.min(getWidth()-90,legendX)); legendY=Math.max(10,Math.min(getHeight()-40,legendY)); prefs.edit().putFloat("pie_label_x",legendX).putFloat("pie_label_y",legendY).apply(); } else startAngle+=(e.getX()-lastX)/2.2f; lastX=e.getX();lastY=e.getY(); invalidate(); return true;} return true;}
        @Override protected void onDraw(Canvas c){ if(values.isEmpty()){p.setColor(Color.rgb(105,110,130));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(28);c.drawText("暂无数据",getWidth()/2f,getHeight()/2f,p);return;} if(legendX<0) legendX=getWidth()*0.58f; double total=0; for(double v:values.values()) total+=v; float cx=getWidth()*0.32f; float cy=getHeight()*0.44f; float rx=Math.min(getWidth()*0.27f, getHeight()*0.31f); float ry=rx*0.62f; RectF oval=new RectF(cx-rx,cy-ry,cx+rx,cy+ry); for(int layer=16; layer>=1; layer--){ float y=layer*2.4f; float a=startAngle; int i=0; for(Map.Entry<String,Double> e:values.entrySet()){float sweep=(float)(e.getValue()/total*360f); p.setColor(dark(colors[i%colors.length])); RectF o=new RectF(oval.left,oval.top+y,oval.right,oval.bottom+y); c.drawArc(o,a,sweep,true,p); a+=sweep;i++;}} float a=startAngle; int i=0; for(Map.Entry<String,Double> e:values.entrySet()){float sweep=(float)(e.getValue()/total*360f); p.setColor(colors[i%colors.length]); c.drawArc(oval,a,sweep,true,p); drawSliceText(c, oval, a, sweep, e.getKey(), e.getValue(), total); a+=sweep;i++;} drawLargeLegend(c,total); }
        private void drawSliceText(Canvas c, RectF oval, float start, float sweep, String cat, double val, double total) { if (sweep < 26) return; double mid = Math.toRadians(start + sweep / 2f); float x = oval.centerX() + (float)Math.cos(mid) * oval.width() * 0.20f; float y = oval.centerY() + (float)Math.sin(mid) * oval.height() * 0.20f; p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(18); c.drawText(iconForCategory(cat), x, y - 16, p); c.drawText("¥" + String.format(Locale.CHINA, "%.0f", val), x, y + 6, p); c.drawText(String.format(Locale.CHINA, "%.0f%%", val / total * 100d), x, y + 28, p); p.setTypeface(Typeface.DEFAULT); }
        private void drawLargeLegend(Canvas c, double total) { p.setTextAlign(Paint.Align.LEFT); float x = legendX; float y = legendY; int i = 0; for(Map.Entry<String,Double> e:values.entrySet()){ double percent=e.getValue()/total*100d; p.setColor(colors[i%colors.length]); c.drawRoundRect(x, y - 20*labelScale, x + 24*labelScale, y + 4*labelScale, 8, 8, p); p.setColor(PRIMARY_DARK); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(22*labelScale); c.drawText(iconForCategory(e.getKey()) + " " + e.getKey(), x + 34*labelScale, y, p); p.setTypeface(Typeface.DEFAULT); p.setTextSize(19*labelScale); p.setColor(Color.rgb(80, 86, 105)); c.drawText("¥" + String.format(Locale.CHINA,"%.2f", e.getValue()) + "  " + String.format(Locale.CHINA,"%.1f%%", percent), x + 34*labelScale, y + 28*labelScale, p); y += 62*labelScale; i++; if (y > getHeight() - 18) break; } } private int dark(int c){return Color.rgb((int)(Color.red(c)*0.62),(int)(Color.green(c)*0.62),(int)(Color.blue(c)*0.62));}}

    public static class ProgressView extends View { private float progress; private int color = ACCENT; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); public ProgressView(Context c){super(c);} public void setProgress(float progress,int color){this.progress=progress;this.color=color;invalidate();} @Override protected void onDraw(Canvas c){p.setColor(Color.rgb(230,235,245));c.drawRoundRect(0,0,getWidth(),getHeight(),getHeight()/2f,getHeight()/2f,p);p.setColor(color);c.drawRoundRect(0,0,getWidth()*progress,getHeight(),getHeight()/2f,getHeight()/2f,p);}}

    public static class AutoLedgerNotificationService extends android.service.notification.NotificationListenerService { @Override public void onListenerConnected(){ super.onListenerConnected(); QuickNotificationHelper.show(this); } @Override public void onNotificationPosted(android.service.notification.StatusBarNotification sbn){ if(sbn==null || sbn.getNotification()==null || sbn.getNotification().extras==null)return; String pkg=sbn.getPackageName()==null?"":sbn.getPackageName().toLowerCase(Locale.ROOT); CharSequence title=sbn.getNotification().extras.getCharSequence("android.title"); CharSequence text=sbn.getNotification().extras.getCharSequence("android.text"); CharSequence big=sbn.getNotification().extras.getCharSequence("android.bigText"); String raw=(title==null?"":title.toString())+" "+(text==null?"":text.toString())+" "+(big==null?"":big.toString()); if(!isPayPackage(pkg) || !looksLikePayment(raw)) return; Entry e=SmartParser.parseOne(raw); if(e==null)return; e.source="通知自动记账"; if("其他".equals(e.account)) e.account=accountFromPackage(pkg); if("其他".equals(e.category)) e.category=SmartParser.inferCategory(raw,e.type); new LedgerDb(this).insert(e); QuickNotificationHelper.show(this); } private boolean isPayPackage(String p){ return p.contains("tencent.mm") || p.contains("alipay") || p.contains("unionpay") || p.contains("bank") || p.contains("cmb") || p.contains("icbc") || p.contains("ccb") || p.contains("abc") || p.contains("boc"); } private boolean looksLikePayment(String s){ if(s==null)return false; boolean money=s.contains("¥")||s.contains("元")||s.contains("金额")||s.matches(".*(?:支付|付款|消费|收款|到账|退款|转账|红包).*[0-9]+(?:\\.[0-9]{1,2})?.*"); boolean word=s.contains("支付")||s.contains("付款")||s.contains("消费")||s.contains("收款")||s.contains("到账")||s.contains("退款")||s.contains("转账")||s.contains("红包")||s.contains("订单"); boolean bad=s.contains("Bytes/s")||s.contains("KiB/s")||s.contains("VPN")||s.contains("HUAWEI WATCH")||s.contains("已连接")||s.contains("US -"); return money && word && !bad; } private String accountFromPackage(String p){ if(p.contains("tencent.mm"))return "微信"; if(p.contains("alipay"))return "支付宝"; return "银行卡"; }}
}
