package com.liyuhui.smartledger;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AutoLedgerAccessibilityService extends AccessibilityService {
    private static final int MAX_TEXT_LENGTH = 1600;
    private static final long DUPLICATE_WINDOW_MS = 30_000L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        String packageName = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (!isTargetPackage(packageName)) return;

        StringBuilder builder = new StringBuilder();
        appendEventText(event, builder);
        AccessibilityNodeInfo root = getRootInActiveWindow();
        collectNodeText(root, builder, new HashSet<>(), 0);

        String text = normalize(builder.toString());
        if (!looksLikeBillText(text)) return;

        MainActivity.Entry entry = MainActivity.SmartParser.parseOne(text);
        if (entry == null || entry.amount <= 0) return;
        entry.source = "无障碍自动记账";
        entry.account = inferAccountFromPackage(packageName, entry.account);

        String fingerprint = packageName + "|" + entry.type + "|" + entry.amount + "|" + entry.category + "|" + entry.note;
        SharedPreferences sp = getSharedPreferences("accessibility_auto_ledger", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String last = sp.getString("last_fingerprint", "");
        long lastTime = sp.getLong("last_time", 0L);
        if (fingerprint.equals(last) && now - lastTime < DUPLICATE_WINDOW_MS) return;

        sp.edit()
                .putString("last_fingerprint", fingerprint)
                .putLong("last_time", now)
                .apply();
        new MainActivity.LedgerDb(this).insert(entry);
    }

    @Override
    public void onInterrupt() {
        // 系统中断无障碍服务时无需额外处理。
    }

    private void appendEventText(AccessibilityEvent event, StringBuilder out) {
        List<CharSequence> list = event.getText();
        if (list == null) return;
        for (CharSequence s : list) {
            if (s != null) appendLimited(out, s.toString());
        }
        CharSequence content = event.getContentDescription();
        if (content != null) appendLimited(out, content.toString());
    }

    private void collectNodeText(AccessibilityNodeInfo node, StringBuilder out, Set<Integer> visited, int depth) {
        if (node == null || depth > 12 || out.length() > MAX_TEXT_LENGTH) return;
        int key = System.identityHashCode(node);
        if (visited.contains(key)) return;
        visited.add(key);

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null) appendLimited(out, text.toString());
        if (desc != null) appendLimited(out, desc.toString());

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            collectNodeText(node.getChild(i), out, visited, depth + 1);
        }
    }

    private void appendLimited(StringBuilder out, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;
        if (out.length() + trimmed.length() > MAX_TEXT_LENGTH) return;
        out.append(' ').append(trimmed);
    }

    private String normalize(String input) {
        return input == null ? "" : input.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private boolean isTargetPackage(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("tencent.mm")
                || p.contains("alipay")
                || p.contains("unionpay")
                || p.contains("bank")
                || p.contains("cmb")
                || p.contains("icbc")
                || p.contains("ccb")
                || p.contains("abc")
                || p.contains("boc");
    }

    private boolean looksLikeBillText(String text) {
        if (text == null || text.length() < 4) return false;
        boolean hasMoneyWord = text.contains("¥") || text.contains("元") || text.contains("金额") || text.contains("付款") || text.contains("支付") || text.contains("消费") || text.contains("收款") || text.contains("到账") || text.contains("收入") || text.contains("退款");
        boolean hasDigit = text.matches(".*\\d+(?:\\.\\d{1,2})?.*");
        boolean hasWalletWord = text.contains("微信") || text.contains("支付宝") || text.contains("银行") || text.contains("银行卡") || text.contains("信用卡") || text.contains("余额") || text.contains("商户");
        return hasDigit && hasMoneyWord && hasWalletWord;
    }

    private String inferAccountFromPackage(String pkg, String fallback) {
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.contains("tencent.mm")) return "微信";
        if (p.contains("alipay")) return "支付宝";
        if (p.contains("bank") || p.contains("cmb") || p.contains("icbc") || p.contains("ccb") || p.contains("abc") || p.contains("boc")) return "银行卡";
        return fallback == null || fallback.trim().isEmpty() ? "其他" : fallback;
    }
}
