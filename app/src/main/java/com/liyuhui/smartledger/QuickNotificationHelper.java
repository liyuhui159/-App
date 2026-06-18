package com.liyuhui.smartledger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class QuickNotificationHelper {
    private static final String CHANNEL_ID = "quick_ledger_channel";
    private static final int NOTIFICATION_ID = 10086;

    public static void show(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "灵犀快捷记账", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("通知栏快捷记账入口");
            manager.createNotificationChannel(channel);
        }

        PendingIntent open = PendingIntent.getActivity(context, 1, quickIntent(context, null), flags());
        PendingIntent expense = PendingIntent.getActivity(context, 2, quickIntent(context, "支出"), flags());
        PendingIntent income = PendingIntent.getActivity(context, 3, quickIntent(context, "收入"), flags());

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground)
                .setContentTitle("灵犀记账")
                .setContentText("点击快速记账，或直接选择收入/支出")
                .setContentIntent(open)
                .setOngoing(false)
                .setAutoCancel(false)
                .addAction(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground, "记支出", expense)
                .addAction(com.liyuhui.smartledger.R.drawable.ic_launcher_foreground, "记收入", income);

        manager.notify(NOTIFICATION_ID, builder.build());
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
