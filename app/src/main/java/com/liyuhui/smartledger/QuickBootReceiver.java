package com.liyuhui.smartledger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class QuickBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "com.liyuhui.smartledger.SHOW_QUICK_LEDGER".equals(action)) {
            QuickNotificationHelper.show(context);
        }
    }
}
