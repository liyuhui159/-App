package com.liyuhui.smartledger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Clean launcher page. Advanced charts and legacy tools remain available from the statistics page. */
public class HomeActivity extends Activity {
    private static final int BG = Color.rgb(246, 247, 251);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(30, 34, 48);
    private static final int MUTED = Color.rgb(108, 113, 130);
    private static final int PRIMARY = Color.rgb(88, 82, 232);
    private static final int PRIMARY_LIGHT = Color.rgb(238, 237, 255);
    private static final int SUCCESS = Color.rgb(27, 167, 125);
    private static final int DANGER = Color.rgb(224, 77, 88);
    private static final int WARNING = Color.rgb(235, 156, 53);

    private MainActivity.LedgerDb db;
    private LinearLayout page;
    private LinearLayout bottomNav;
    private TextView statusChip;
    private String currentTab = "首页";
    private String recordFilter = "全部";
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new MainActivity.LedgerDb(this);
        requestNotificationPermission();
        buildFrame();
        renderCurrent();
        QuickNotificationHelper.show(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (page != null) renderCurrent();
        updateHeaderStatus();
    }

    @Override protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
        }
    }

    private void buildFrame() {
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = horizontal();
        header.setPadding(dp(18), dp(12), dp(14), dp(10));
        header.setBackgroundColor(Color.WHITE);
        LinearLayout titleBox = vertical();
        titleBox.addView(text("灵犀记账", 23, TEXT, true));
        titleBox.addView(text("自动识别付款 · 智能商家分类", 12, MUTED, false));
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        statusChip = text("", 12, SUCCESS, true);
        statusChip.setGravity(Gravity.CENTER);
        statusChip.setPadding(dp(10), dp(7), dp(10), dp(7));
        header.addView(statusChip);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = vertical();
        page.setPadding(dp(15), dp(9), dp(15), dp(26));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomNav = horizontal();
        bottomNav.setPadding(dp(8), dp(5), dp(8), dp(7));
        bottomNav.setBackgroundColor(Color.WHITE);
        root.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(66)));
        rebuildBottomNav();
        updateHeaderStatus();
    }

    private void rebuildBottomNav() {
        bottomNav.removeAllViews();
        String[][] tabs = {
                {"首页", "⌂"}, {"流水", "≡"}, {"统计", "▥"}, {"我的", "⚙"}
        };
        for (String[] tab : tabs) {
            Button button = navButton(tab[1] + "\n" + tab[0], tab[0].equals(currentTab));
            button.setOnClickListener(v -> {
                currentTab = tab[0];
                rebuildBottomNav();
                renderCurrent();
            });
            bottomNav.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
        }
    }

    private void renderCurrent() {
        page.removeAllViews();
        if ("流水".equals(currentTab)) renderRecords();
        else if ("统计".equals(currentTab)) renderStatistics();
        else if ("我的".equals(currentTab)) renderMine();
        else renderHome();
    }

    private void renderHome() {
        String month = monthFormat.format(new Date());
        MainActivity.Totals totals = db.monthTotals(month);

        TextView date = text(new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date()),
                14, MUTED, false);
        date.setPadding(dp(2), dp(4), 0, dp(5));
        page.addView(date);

        LinearLayout hero = card();
        hero.setBackground(gradient(Color.rgb(87, 82, 232), Color.rgb(118, 83, 220), 25));
        hero.addView(text("本月支出", 14, Color.argb(225, 255, 255, 255), false));
        hero.addView(text(money(totals.expense), 34, Color.WHITE, true));
        LinearLayout sub = horizontal();
        sub.setPadding(0, dp(13), 0, 0);
        sub.addView(heroMetric("本月收入", money(totals.income)), new LinearLayout.LayoutParams(0, -2, 1));
        sub.addView(heroMetric("本月结余", money(totals.income - totals.expense)), new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(sub);
        page.addView(hero);

        LinearLayout autoCard = card();
        LinearLayout autoRow = horizontal();
        TextView autoIcon = text(hasNotificationAccess() ? "✓" : "!", 19,
                hasNotificationAccess() ? SUCCESS : WARNING, true);
        autoIcon.setGravity(Gravity.CENTER);
        autoIcon.setBackground(round(hasNotificationAccess()
                ? Color.rgb(231, 248, 241) : Color.rgb(255, 245, 229), 18));
        autoRow.addView(autoIcon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        addGap(autoRow, 11);
        LinearLayout autoText = vertical();
        autoText.addView(text(hasNotificationAccess() ? "自动记账正在运行" : "还差一步：开启付款通知读取",
                16, TEXT, true));
        autoText.addView(text(autoStatusDescription(), 12, MUTED, false));
        autoRow.addView(autoText, new LinearLayout.LayoutParams(0, -2, 1));
        autoCard.addView(autoRow);
        autoCard.setOnClickListener(v -> showAutoPermissionDialog());
        page.addView(autoCard);

        LinearLayout actions = horizontal();
        actions.addView(actionButton("＋", "记一笔", () -> startActivity(
                new Intent(this, QuickEntryActivity.class).putExtra("source", "首页快捷记账"))),
                new LinearLayout.LayoutParams(0, dp(88), 1));
        addGap(actions, 9);
        actions.addView(actionButton("⚡", "自动记账", this::showAutoPermissionDialog),
                new LinearLayout.LayoutParams(0, dp(88), 1));
        addGap(actions, 9);
        actions.addView(actionButton("AI", "分类设置", () -> startActivity(
                new Intent(this, AiImportActivity.class))),
                new LinearLayout.LayoutParams(0, dp(88), 1));
        page.addView(actions);

        page.addView(section("最近账单"));
        List<MainActivity.Entry> recent = db.listEntries(8, "", "全部");
        if (recent.isEmpty()) {
            page.addView(empty("还没有账单\n开启自动记账后，微信或支付宝付款会显示在这里。"));
        } else {
            for (MainActivity.Entry entry : recent) page.addView(entryCard(entry));
            Button all = textButton("查看全部流水 →");
            all.setOnClickListener(v -> {
                currentTab = "流水";
                rebuildBottomNav();
                renderCurrent();
            });
            page.addView(all, new LinearLayout.LayoutParams(-1, dp(46)));
        }
    }

    private void renderRecords() {
        LinearLayout title = horizontal();
        title.addView(text("交易流水", 22, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button export = textButton("导出");
        export.setOnClickListener(v -> shareCsv());
        title.addView(export, new LinearLayout.LayoutParams(dp(72), dp(42)));
        page.addView(title);

        LinearLayout filters = horizontal();
        for (String filter : new String[]{"全部", "支出", "收入"}) {
            Button button = filterButton(filter, filter.equals(recordFilter));
            button.setOnClickListener(v -> {
                recordFilter = filter;
                renderCurrent();
            });
            filters.addView(button, new LinearLayout.LayoutParams(0, dp(42), 1));
            if (!"收入".equals(filter)) addGap(filters, 8);
        }
        page.addView(filters);

        List<MainActivity.Entry> entries = db.listEntries(120, "", recordFilter);
        if (entries.isEmpty()) page.addView(empty("当前筛选条件下没有流水"));
        else for (MainActivity.Entry entry : entries) page.addView(entryCard(entry));
    }

    private void renderStatistics() {
        String month = monthFormat.format(new Date());
        MainActivity.Totals totals = db.monthTotals(month);
        page.addView(text("本月统计", 22, TEXT, true));
        page.addView(text(month + " · 支出 " + money(totals.expense), 13, MUTED, false));

        LinearLayout summary = horizontal();
        summary.addView(statCard("收入", money(totals.income), SUCCESS), new LinearLayout.LayoutParams(0, dp(86), 1));
        addGap(summary, 9);
        summary.addView(statCard("支出", money(totals.expense), DANGER), new LinearLayout.LayoutParams(0, dp(86), 1));
        page.addView(summary);

        page.addView(section("消费分类"));
        Map<String, Double> categoryMap = db.categoryExpense(month);
        if (categoryMap.isEmpty()) {
            page.addView(empty("本月暂无支出数据"));
        } else {
            List<Map.Entry<String, Double>> categories = new ArrayList<>(categoryMap.entrySet());
            Collections.sort(categories, (a, b) -> Double.compare(b.getValue(), a.getValue()));
            double max = categories.get(0).getValue();
            for (Map.Entry<String, Double> category : categories) {
                page.addView(categoryRow(category.getKey(), category.getValue(), max, totals.expense));
            }
        }

        Button advanced = primaryButton("查看日 / 周 / 月图表与预算");
        advanced.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        page.addView(advanced, buttonParams());
    }

    private void renderMine() {
        page.addView(text("我的", 22, TEXT, true));
        page.addView(text("权限、AI 分类和数据工具集中放在这里。", 13, MUTED, false));

        page.addView(settingCard("AI", "AI 商家分类",
                aiStatusDescription(), () -> startActivity(new Intent(this, AiImportActivity.class))));
        page.addView(settingCard("⚡", "自动记账权限",
                hasNotificationAccess() ? "通知读取权限已开启" : "未开启，无法自动识别微信/支付宝付款",
                this::showAutoPermissionDialog));
        page.addView(settingCard("⌁", "商家分类记忆",
                "已记住 " + MerchantCategoryStore.size(this) + " 个常用商家",
                () -> startActivity(new Intent(this, AiImportActivity.class))));
        page.addView(settingCard("⇧", "导出账单 CSV",
                "备份或分享全部交易流水", this::shareCsv));
        page.addView(settingCard("▥", "高级统计与预算",
                "进入原有日/周/月图表、预算和详细设置",
                () -> startActivity(new Intent(this, MainActivity.class))));

        LinearLayout note = card();
        note.addView(text("分类逻辑", 15, TEXT, true));
        note.addView(text("同一商家优先使用你手机中的历史分类；明确商家使用本地规则；只有未知商家才调用 AI。账本数据仍保存在本机 SQLite 数据库。",
                13, MUTED, false));
        page.addView(note);
    }

    private View entryCard(MainActivity.Entry entry) {
        LinearLayout card = card();
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        LinearLayout row = horizontal();
        TextView icon = text(LedgerCategories.icon(entry.category), 22, TEXT, false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(Color.rgb(245, 245, 252), 17));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        addGap(row, 11);
        LinearLayout info = vertical();
        String merchant = entry.note == null || entry.note.trim().isEmpty() ? entry.category : entry.note;
        info.addView(text(merchant, 15, TEXT, true));
        info.addView(text(entry.category + " · " + entry.account + " · "
                + dateFormat.format(new Date(entry.time)), 12, MUTED, false));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(("收入".equals(entry.type) ? "+" : "-") + money(entry.amount),
                16, "收入".equals(entry.type) ? SUCCESS : TEXT, true));
        card.addView(row);
        card.setOnClickListener(v -> openEntry(entry));
        return card;
    }

    private void openEntry(MainActivity.Entry entry) {
        Intent intent = new Intent(this, EntryDetailActivity.class);
        intent.putExtra("id", entry.id);
        intent.putExtra("amount", entry.amount);
        intent.putExtra("type", entry.type);
        intent.putExtra("category", entry.category);
        intent.putExtra("account", entry.account);
        intent.putExtra("note", entry.note);
        intent.putExtra("source", entry.source);
        intent.putExtra("time", entry.time);
        startActivity(intent);
    }

    private View categoryRow(String category, double value, double max, double total) {
        LinearLayout card = card();
        card.setPadding(dp(13), dp(11), dp(13), dp(11));
        LinearLayout top = horizontal();
        top.addView(text(LedgerCategories.icon(category) + "  " + category, 15, TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        String percent = total <= 0 ? "0%" : String.format(Locale.CHINA, "%.0f%%", value / total * 100d);
        top.addView(text(money(value) + " · " + percent, 13, MUTED, true));
        card.addView(top);

        LinearLayout bar = horizontal();
        bar.setBackground(round(Color.rgb(237, 239, 246), 5));
        View fill = new View(this);
        fill.setBackground(round(PRIMARY, 5));
        float valueWeight = (float) Math.max(0.03d, value / Math.max(1d, max));
        bar.addView(fill, new LinearLayout.LayoutParams(0, dp(8), valueWeight));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(8), 1f - valueWeight));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(8));
        barParams.setMargins(0, dp(9), 0, 0);
        card.addView(bar, barParams);
        return card;
    }

    private View settingCard(String iconValue, String title, String description, Runnable action) {
        LinearLayout card = card();
        LinearLayout row = horizontal();
        TextView icon = text(iconValue, 17, PRIMARY, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(PRIMARY_LIGHT, 16));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        addGap(row, 11);
        LinearLayout textBox = vertical();
        textBox.addView(text(title, 15, TEXT, true));
        textBox.addView(text(description, 12, MUTED, false));
        row.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text("›", 25, Color.rgb(160, 164, 177), false));
        card.addView(row);
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private String autoStatusDescription() {
        if (!hasNotificationAccess()) return "点击开启后，付款通知才会自动写入账本";
        AiLedgerClient.Config config = AiLedgerClient.loadConfig(this);
        if (config.enabled && config.notificationEnabled && config.hasApiKey) {
            return "金额本地提取，未知商家由 AI 判断品类";
        }
        return "金额会自动记录；未知商家暂时归入“其他”";
    }

    private String aiStatusDescription() {
        AiLedgerClient.Config config = AiLedgerClient.loadConfig(this);
        if (!config.enabled) return "已关闭；仍使用商家记忆和本地规则";
        if (!config.hasApiKey) return "未配置 API Key；未知商家会归入“其他”";
        return "已开启；未知商家才会调用 AI";
    }

    private void updateHeaderStatus() {
        if (statusChip == null) return;
        boolean permission = hasNotificationAccess();
        AiLedgerClient.Config config = AiLedgerClient.loadConfig(this);
        if (!permission) {
            statusChip.setText("● 待开启");
            statusChip.setTextColor(WARNING);
            statusChip.setBackground(round(Color.rgb(255, 245, 229), 15));
        } else if (config.enabled && config.hasApiKey) {
            statusChip.setText("● AI分类中");
            statusChip.setTextColor(SUCCESS);
            statusChip.setBackground(round(Color.rgb(231, 248, 241), 15));
        } else {
            statusChip.setText("● 自动记账");
            statusChip.setTextColor(PRIMARY);
            statusChip.setBackground(round(PRIMARY_LIGHT, 15));
        }
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName())
                && enabled.contains("AiNotificationService");
    }

    private void showAutoPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("开启自动记账")
                .setMessage("请在系统列表中找到“灵犀记账”，开启通知使用权。升级旧版本后也需要重新确认一次权限。")
                .setPositiveButton("去开启", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    } catch (Exception error) {
                        toast("无法打开通知使用权页面");
                    }
                })
                .setNegativeButton("稍后", null).show();
    }

    private void shareCsv() {
        String csv = MainActivity.Csv.toCsv(db.listEntries(5000, "", "全部"));
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_SUBJECT, "灵犀记账 CSV 导出");
        send.putExtra(Intent.EXTRA_TEXT, csv);
        startActivity(Intent.createChooser(send, "导出/分享 CSV"));
    }

    private LinearLayout heroMetric(String title, String value) {
        LinearLayout metric = vertical();
        metric.addView(text(title, 12, Color.argb(205, 255, 255, 255), false));
        metric.addView(text(value, 16, Color.WHITE, true));
        return metric;
    }

    private LinearLayout statCard(String title, String value, int valueColor) {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.addView(text(title, 12, MUTED, false));
        card.addView(text(value, 20, valueColor, true));
        return card;
    }

    private View actionButton(String icon, String title, Runnable action) {
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(9), dp(6), dp(9));
        card.setBackground(round(CARD, 20));
        card.setElevation(dp(1));
        card.addView(text(icon, 20, PRIMARY, true));
        card.addView(text(title, 13, TEXT, true));
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(round(CARD, 21));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(6));
        card.setLayoutParams(params);
        return card;
    }

    private TextView section(String value) {
        TextView view = text(value, 17, TEXT, true);
        view.setPadding(dp(2), dp(15), 0, dp(3));
        return view;
    }

    private TextView empty(String value) {
        TextView view = text(value, 14, MUTED, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(18), dp(28), dp(18), dp(28));
        view.setBackground(round(CARD, 20));
        return view;
    }

    private Button navButton(String value, boolean selected) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(selected ? PRIMARY : MUTED);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private Button filterButton(String value, boolean selected) {
        Button button = textButton(value);
        button.setTextColor(selected ? Color.WHITE : MUTED);
        button.setBackground(round(selected ? PRIMARY : Color.WHITE, 16));
        return button;
    }

    private Button primaryButton(String value) {
        Button button = textButton(value);
        button.setTextColor(Color.WHITE);
        button.setBackground(round(PRIMARY, 17));
        return button;
    }

    private Button textButton(String value) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(PRIMARY);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundColor(Color.TRANSPARENT);
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

    private void addGap(LinearLayout parent, int width) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(dp(width), 1));
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(12), 0, dp(4));
        return params;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (color == CARD) drawable.setStroke(dp(1), Color.rgb(235, 237, 243));
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private String money(double value) {
        return "¥" + String.format(Locale.CHINA, "%.2f", value);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}
