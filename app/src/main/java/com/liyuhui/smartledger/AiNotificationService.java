package com.liyuhui.smartledger;

import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Automatic bookkeeping service: local facts first, merchant memory/rules second, AI category last. */
public class AiNotificationService extends NotificationListenerService {
    private static final String DEDUPE_PREFS = "ai_notification_dedupe_v2";
    private static final long DEDUPE_WINDOW_MS = 180000L;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        QuickNotificationHelper.show(this);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;
        String pkg = sbn.getPackageName() == null ? "" : sbn.getPackageName().toLowerCase(Locale.ROOT);
        String raw = notificationText(sbn).trim();
        if (!isPayPackage(pkg) || !PaymentNotificationParser.looksLikePayment(raw)) return;

        String fingerprint = fingerprint(pkg, raw);
        if (isDuplicate(fingerprint) || !pending.add(fingerprint)) return;

        MainActivity.Entry entry = PaymentNotificationParser.parse(pkg, raw);
        if (entry == null) {
            pending.remove(fingerprint);
            return;
        }

        String merchant = MerchantCategoryStore.extractMerchant(raw);
        if (!merchant.isEmpty()) entry.note = merchant;

        String remembered = MerchantCategoryStore.find(this, merchant);
        if (!remembered.isEmpty()) {
            entry.category = remembered;
            entry.source = "通知商家记忆分类";
            insertAndFinish(entry, merchant, fingerprint, true);
            return;
        }

        String strong = LedgerCategories.inferStrong(merchant + " " + raw, entry.type);
        if (!"其他".equals(strong)) {
            entry.category = strong;
            entry.source = "通知本地商家分类";
            insertAndFinish(entry, merchant, fingerprint, true);
            return;
        }

        AiLedgerClient.Config config = AiLedgerClient.loadConfig(this);
        if (!config.enabled || !config.notificationEnabled || !config.hasApiKey) {
            entry.category = "其他";
            entry.source = "通知自动记账·待分类";
            insertAndFinish(entry, merchant, fingerprint, false);
            return;
        }

        AiLedgerClient.classifyAsync(this, merchant, raw, entry.type,
                new AiLedgerClient.CategoryCallback() {
                    @Override public void onSuccess(AiLedgerClient.CategoryResult result) {
                        entry.category = result.category;
                        if (!result.merchant.isEmpty() && merchant.isEmpty()) entry.note = result.merchant;
                        entry.source = "通知AI商家分类";
                        insertAndFinish(entry,
                                merchant.isEmpty() ? result.merchant : merchant,
                                fingerprint, true);
                    }

                    @Override public void onFailure(String message) {
                        entry.category = "其他";
                        entry.source = "通知自动记账·AI未确认";
                        insertAndFinish(entry, merchant, fingerprint, false);
                    }
                });
    }

    private void insertAndFinish(MainActivity.Entry entry, String merchant,
                                 String fingerprint, boolean rememberCategory) {
        try {
            if (entry.note == null || entry.note.trim().isEmpty()) entry.note = "支付通知";
            long id = new MainActivity.LedgerDb(this).insert(entry);
            if (id > 0) {
                if (rememberCategory && !merchant.isEmpty()) {
                    MerchantCategoryStore.remember(this, merchant, entry.category);
                }
                rememberFingerprint(fingerprint);
                QuickNotificationHelper.show(this);
            }
        } finally {
            pending.remove(fingerprint);
        }
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

    private boolean isDuplicate(String fingerprint) {
        SharedPreferences preferences = getSharedPreferences(DEDUPE_PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        boolean duplicate = false;
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, ?> item : preferences.getAll().entrySet()) {
            long time = item.getValue() instanceof Number
                    ? ((Number) item.getValue()).longValue() : 0L;
            if (now - time >= DEDUPE_WINDOW_MS) {
                editor.remove(item.getKey());
            } else if (item.getKey().equals("fp_" + fingerprint)) {
                duplicate = true;
            }
        }
        editor.apply();
        return duplicate;
    }

    private void rememberFingerprint(String fingerprint) {
        getSharedPreferences(DEDUPE_PREFS, MODE_PRIVATE).edit()
                .putLong("fp_" + fingerprint, System.currentTimeMillis()).apply();
    }

    private String fingerprint(String pkg, String raw) {
        return Integer.toHexString((pkg + "|" + raw).hashCode());
    }

    private boolean isPayPackage(String packageName) {
        return packageName.contains("tencent.mm") || packageName.contains("alipay")
                || packageName.contains("unionpay") || packageName.contains("bank")
                || packageName.contains("cmb") || packageName.contains("icbc")
                || packageName.contains("ccb") || packageName.contains("abc")
                || packageName.contains("boc");
    }
}
