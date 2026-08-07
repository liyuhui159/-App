package com.liyuhui.smartledger;

import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;
import java.util.Locale;

/** Uses AI for ambiguous payment notifications and falls back to the original local parser. */
public class AiNotificationService extends NotificationListenerService {
    private static final String DEDUPE_PREFS = "ai_notification_dedupe";
    private static final long DEDUPE_WINDOW_MS = 90000L;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        QuickNotificationHelper.show(this);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;
        String pkg = sbn.getPackageName() == null ? "" : sbn.getPackageName().toLowerCase(Locale.ROOT);
        String raw = notificationText(sbn).trim();
        if (!isPayPackage(pkg) || !looksLikePayment(raw) || isDuplicate(pkg, raw)) return;
        remember(pkg, raw);

        MainActivity.Entry local = MainActivity.SmartParser.parseOne(raw);
        AiLedgerClient.Config config = AiLedgerClient.loadConfig(this);
        if (!config.enabled || !config.notificationEnabled || !config.hasApiKey) {
            insertLocal(local, pkg, raw);
            return;
        }

        AiLedgerClient.parseAsync(this, raw, new AiLedgerClient.Callback() {
            @Override public void onSuccess(List<MainActivity.Entry> entries, String modelText) {
                if (entries == null || entries.isEmpty()) {
                    insertLocal(local, pkg, raw);
                    return;
                }
                MainActivity.LedgerDb db = new MainActivity.LedgerDb(AiNotificationService.this);
                int inserted = 0;
                for (MainActivity.Entry e : entries) {
                    if (e == null || e.amount <= 0) continue;
                    if ("其他".equals(e.account)) e.account = accountFromPackage(pkg);
                    if (e.note == null || e.note.trim().isEmpty()) e.note = compact(raw);
                    e.source = "通知AI自动记账";
                    if (db.insert(e) > 0) inserted++;
                    if (inserted >= 5) break;
                }
                if (inserted == 0) insertLocal(local, pkg, raw);
                else QuickNotificationHelper.show(AiNotificationService.this);
            }

            @Override public void onFailure(String message) {
                insertLocal(local, pkg, raw);
            }
        });
    }

    private void insertLocal(MainActivity.Entry entry, String pkg, String raw) {
        if (entry == null || entry.amount <= 0) return;
        entry.source = "通知本地规则兜底";
        if ("其他".equals(entry.account)) entry.account = accountFromPackage(pkg);
        if ("其他".equals(entry.category)) entry.category = MainActivity.SmartParser.inferCategory(raw, entry.type);
        if (entry.note == null || entry.note.trim().isEmpty()) entry.note = compact(raw);
        new MainActivity.LedgerDb(this).insert(entry);
        QuickNotificationHelper.show(this);
    }

    private String notificationText(StatusBarNotification sbn) {
        CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
        CharSequence text = sbn.getNotification().extras.getCharSequence("android.text");
        CharSequence big = sbn.getNotification().extras.getCharSequence("android.bigText");
        CharSequence sub = sbn.getNotification().extras.getCharSequence("android.subText");
        return value(title) + "\n" + value(text) + "\n" + value(big) + "\n" + value(sub);
    }

    private String value(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private boolean isDuplicate(String pkg, String raw) {
        String key = Integer.toHexString((pkg + "|" + raw).hashCode());
        SharedPreferences p = getSharedPreferences(DEDUPE_PREFS, MODE_PRIVATE);
        return key.equals(p.getString("key", ""))
                && System.currentTimeMillis() - p.getLong("time", 0L) < DEDUPE_WINDOW_MS;
    }

    private void remember(String pkg, String raw) {
        String key = Integer.toHexString((pkg + "|" + raw).hashCode());
        getSharedPreferences(DEDUPE_PREFS, MODE_PRIVATE).edit()
                .putString("key", key)
                .putLong("time", System.currentTimeMillis())
                .apply();
    }

    private boolean isPayPackage(String p) {
        return p.contains("tencent.mm") || p.contains("alipay") || p.contains("unionpay")
                || p.contains("bank") || p.contains("cmb") || p.contains("icbc")
                || p.contains("ccb") || p.contains("abc") || p.contains("boc");
    }

    private boolean looksLikePayment(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        boolean money = s.contains("¥") || s.contains("￥") || s.contains("元") || s.contains("金额")
                || s.matches("(?s).*(?:支付|付款|消费|收款|到账|退款|转账|红包).*[0-9]+(?:\\.[0-9]{1,2})?.*");
        boolean word = s.contains("支付") || s.contains("付款") || s.contains("消费") || s.contains("收款")
                || s.contains("到账") || s.contains("退款") || s.contains("转账") || s.contains("红包")
                || s.contains("订单") || s.contains("扣款");
        boolean bad = s.contains("Bytes/s") || s.contains("KiB/s") || s.contains("MiB/s")
                || s.contains("VPN") || s.contains("HUAWEI WATCH") || s.contains("已连接")
                || s.contains("验证码") || s.contains("登录确认") || s.contains("US -");
        return money && word && !bad;
    }

    private String accountFromPackage(String p) {
        if (p.contains("tencent.mm")) return "微信";
        if (p.contains("alipay")) return "支付宝";
        return "银行卡";
    }

    private String compact(String raw) {
        if (raw == null) return "支付通知";
        String s = raw.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }
}
