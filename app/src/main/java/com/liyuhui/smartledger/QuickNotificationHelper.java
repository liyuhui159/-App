package com.liyuhui.smartledger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuickNotificationHelper {
    private static final String CHANNEL_ID = "quick_ledger_channel";
    private static final String REVIEW_CHANNEL_ID = "bill_review_channel";
    private static final int NOTIFICATION_ID = 10086;
    private static final int REVIEW_NOTIFICATION_ID = 10087;
    private static final int GREEN = 0xff00c2a8;
    private static final int RED = 0xfff55b5b;

    public static void show(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannels(manager);

        PendingIntent openApp = PendingIntent.getActivity(context, 1, mainIntent(context), flags());
        PendingIntent expense = PendingIntent.getActivity(context, 2, quickIntent(context, "支出"), flags());
        PendingIntent income = PendingIntent.getActivity(context, 3, quickIntent(context, "收入"), flags());

        Today today = todayTotals(context);
        String incomeText = "🟢 收入 " + money(today.income);
        String expenseText = "🔴 支出 " + money(today.expense);
        String balanceText = "结余 " + money(today.income - today.expense);
        SpannableString line = coloredLine(incomeText + "  " + expenseText + "  " + balanceText);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        Notification.BigTextStyle style = new Notification.BigTextStyle()
                .bigText(coloredLine("🟢 今日收入：" + money(today.income)
                        + "\n🔴 今日支出：" + money(today.expense)
                        + "\n今日结余：" + money(today.income - today.expense)
                        + "\n点通知主体进入 App；下方按钮快速记账。"))
                .setBigContentTitle("灵犀记账 · 今日收支");

        builder.setSmallIcon(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground)
                .setContentTitle("灵犀记账 · 今日收支")
                .setContentText(line)
                .setStyle(style)
                .setContentIntent(openApp)
                .setOngoing(false)
                .setAutoCancel(false)
                .addAction(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground, "记支出", expense)
                .addAction(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground, "记收入", income);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    public static void showBillReview(Context context, String raw, String account, String type, String source, double amount, String note, String category) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannels(manager);

        Intent editIntent = new Intent(context, QuickEntryActivity.class);
        editIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        editIntent.putExtra("raw_text", raw == null ? "" : raw);
        editIntent.putExtra("forced_account", account == null ? "微信" : account);
        editIntent.putExtra("forced_type", type == null ? "支出" : type);
        editIntent.putExtra("source", source == null ? "识别到账单" : source);
        if (amount > 0) editIntent.putExtra("forced_amount", amount);
        if (note != null && note.trim().length() > 0) editIntent.putExtra("forced_note", note.trim());
        if (category != null && category.trim().length() > 0) editIntent.putExtra("forced_category", category.trim());

        PendingIntent openApp = PendingIntent.getActivity(context, 101, mainIntent(context), flags());
        PendingIntent edit = PendingIntent.getActivity(context, 100, editIntent, flags());
        String amountText = amount > 0 ? money(amount) : "待确认";
        String safeType = type == null ? "支出" : type;
        String typeMark = "收入".equals(safeType) ? "🟢 收入" : "🔴 支出";
        String title = "识别到账单 · " + typeMark;
        String text = amountText + (note == null || note.trim().isEmpty() ? "" : " · " + note.trim());

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, REVIEW_CHANNEL_ID)
                : new Notification.Builder(context);

        Notification.BigTextStyle style = new Notification.BigTextStyle()
                .setBigContentTitle(title)
                .bigText(coloredLine(typeMark
                        + "\n金额：" + amountText
                        + "\n账户：" + (account == null ? "微信" : account)
                        + "\n分类：" + (category == null ? "人情" : category)
                        + "\n备注：" + (note == null || note.trim().isEmpty() ? "自动识别记账" : note.trim())
                        + "\n点通知主体进入 App；点按钮进入编辑确认。"));

        builder.setSmallIcon(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(coloredLine(typeMark + " " + text))
                .setStyle(style)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setOngoing(false)
                .addAction(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground, "编辑确认", edit);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) builder.setPriority(Notification.PRIORITY_HIGH);
        manager.notify(REVIEW_NOTIFICATION_ID, builder.build());
    }

    private static SpannableString coloredLine(String s) {
        SpannableString sp = new SpannableString(s);
        applyColor(sp, s, "收入", GREEN);
        applyColor(sp, s, "今日收入", GREEN);
        applyColor(sp, s, "支出", RED);
        applyColor(sp, s, "今日支出", RED);
        return sp;
    }

    private static void applyColor(SpannableString sp, String s, String key, int color) {
        int start = s.indexOf(key);
        while (start >= 0) {
            int end = Math.min(s.length(), start + key.length() + 12);
            sp.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = s.indexOf(key, end);
        }
    }

    private static void ensureChannels(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "灵犀快捷记账", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("今日收支与快捷记账入口");
            manager.createNotificationChannel(channel);
            NotificationChannel reviewChannel = new NotificationChannel(REVIEW_CHANNEL_ID, "账单识别确认", NotificationManager.IMPORTANCE_HIGH);
            reviewChannel.setDescription("识别到账单后提醒确认记账");
            manager.createNotificationChannel(reviewChannel);
        }
    }

    private static Today todayTotals(Context context) {
        Today t = new Today();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        List<MainActivity.Entry> list = new MainActivity.LedgerDb(context).listEntries(10000, "", "全部");
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        for (MainActivity.Entry e : list) {
            if (!today.equals(f.format(new Date(e.time)))) continue;
            if ("收入".equals(e.type)) t.income += e.amount;
            else t.expense += e.amount;
        }
        return t;
    }

    private static String money(double v) {
        return "¥" + String.format(Locale.CHINA, "%.2f", v);
    }

    private static class Today { double income; double expense; }

    private static Intent mainIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    private static Intent quickIntent(Context context, String type) {
        Intent intent = new Intent(context, QuickEntryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("source", "通知栏快捷记账");
        if (type != null) intent.putExtra("forced_type", type);
        return intent;
    }

    private static int flags() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f |= PendingIntent.FLAG_IMMUTABLE;
        return f;
    }
}
