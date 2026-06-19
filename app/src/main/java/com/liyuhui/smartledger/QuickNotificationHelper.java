package com.liyuhui.smartledger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuickNotificationHelper {
    private static final String CHANNEL_ID = "quick_ledger_channel";
    private static final String REVIEW_CHANNEL_ID = "bill_review_channel";
    private static final int NOTIFICATION_ID = 10086;
    private static final int REVIEW_NOTIFICATION_ID = 10087;

    public static void show(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannels(manager);

        PendingIntent open = PendingIntent.getActivity(context, 1, quickIntent(context, null), flags());
        PendingIntent expense = PendingIntent.getActivity(context, 2, quickIntent(context, "支出"), flags());
        PendingIntent income = PendingIntent.getActivity(context, 3, quickIntent(context, "收入"), flags());

        Today today = todayTotals(context);
        String line = "收入 " + money(today.income) + "  支出 " + money(today.expense) + "  结余 " + money(today.income - today.expense);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        Notification.BigTextStyle style = new Notification.BigTextStyle()
                .bigText("今日收入：" + money(today.income)
                        + "\n今日支出：" + money(today.expense)
                        + "\n今日结余：" + money(today.income - today.expense)
                        + "\n点击快速记账，或直接选择收入/支出。")
                .setBigContentTitle("灵犀记账 · 今日收支");

        builder.setSmallIcon(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground)
                .setContentTitle("灵犀记账 · 今日收支")
                .setContentText(line)
                .setStyle(style)
                .setContentIntent(open)
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

        Intent intent = new Intent(context, QuickEntryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("raw_text", raw == null ? "" : raw);
        intent.putExtra("forced_account", account == null ? "微信" : account);
        intent.putExtra("forced_type", type == null ? "支出" : type);
        intent.putExtra("source", source == null ? "识别到账单" : source);
        if (amount > 0) intent.putExtra("forced_amount", amount);
        if (note != null && note.trim().length() > 0) intent.putExtra("forced_note", note.trim());
        if (category != null && category.trim().length() > 0) intent.putExtra("forced_category", category.trim());

        PendingIntent review = PendingIntent.getActivity(context, 100, intent, flags());
        String amountText = amount > 0 ? money(amount) : "待确认";
        String title = "识别到微信转账，点击确认记账";
        String text = amountText + (note == null || note.trim().isEmpty() ? "" : " · " + note.trim());

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, REVIEW_CHANNEL_ID)
                : new Notification.Builder(context);

        Notification.BigTextStyle style = new Notification.BigTextStyle()
                .setBigContentTitle(title)
                .bigText("金额：" + amountText
                        + "\n账户：" + (account == null ? "微信" : account)
                        + "\n分类：" + (category == null ? "人情" : category)
                        + "\n备注：" + (note == null || note.trim().isEmpty() ? "微信转账" : note.trim())
                        + "\n如果弹窗没有自动出现，点这里确认保存。");

        builder.setSmallIcon(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(style)
                .setContentIntent(review)
                .setAutoCancel(true)
                .setOngoing(false)
                .addAction(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground, "确认记账", review);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }
        manager.notify(REVIEW_NOTIFICATION_ID, builder.build());
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

    private static class Today {
        double income;
        double expense;
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
