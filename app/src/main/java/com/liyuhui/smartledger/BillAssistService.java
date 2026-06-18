package com.liyuhui.smartledger;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BillAssistService extends AccessibilityService {
    private static final int MAX_LEN = 1800;
    private static final long REPEAT_MS = 30000L;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        QuickNotificationHelper.show(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (!isPayRelatedPackage(pkg)) return;

        StringBuilder sb = new StringBuilder();
        addEventText(event, sb);
        readNode(getRootInActiveWindow(), sb, new HashSet<>(), 0);
        String text = normalize(sb.toString());
        if (!isBillDetail(text)) return;

        MainActivity.Entry e = MainActivity.SmartParser.parseOne(text);
        if (e == null || e.amount <= 0) return;

        String key = pkg + "|" + e.amount + "|" + e.type + "|" + e.note;
        SharedPreferences sp = getSharedPreferences("bill_assist", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (key.equals(sp.getString("last", "")) && now - sp.getLong("last_time", 0L) < REPEAT_MS) return;
        sp.edit().putString("last", key).putLong("last_time", now).apply();

        Intent i = new Intent(this, QuickEntryActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("raw_text", text);
        i.putExtra("forced_account", accountFromPackage(pkg));
        i.putExtra("source", "账单页面识别");
        startActivity(i);
    }

    @Override
    public void onInterrupt() { }

    private boolean isPayRelatedPackage(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("tencent.mm") || p.contains("alipay") || p.contains("unionpay") || p.contains("bank") || p.contains("cmb") || p.contains("icbc") || p.contains("ccb") || p.contains("abc") || p.contains("boc");
    }

    private String accountFromPackage(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.contains("tencent.mm")) return "微信";
        if (p.contains("alipay")) return "支付宝";
        return "银行卡";
    }

    private boolean isBillDetail(String t) {
        if (t == null || t.length() < 8) return false;
        boolean money = t.matches(".*[-+]?\\d+(?:\\.\\d{1,2})?.*") && (t.contains("元") || t.contains("¥") || t.contains("付款") || t.contains("支付") || t.contains("消费") || t.contains("收款") || t.contains("到账") || t.contains("退款"));
        boolean detail = t.contains("支付成功") || t.contains("当前状态") || t.contains("转账时间") || t.contains("转账单号") || t.contains("支付方式") || t.contains("账单") || t.contains("订单") || t.contains("二维码收款") || t.contains("收款方备注");
        return money && detail;
    }

    private void addEventText(AccessibilityEvent event, StringBuilder sb) {
        List<CharSequence> list = event.getText();
        if (list != null) for (CharSequence cs : list) add(sb, cs);
        add(sb, event.getContentDescription());
    }

    private void readNode(AccessibilityNodeInfo node, StringBuilder sb, Set<Integer> visited, int depth) {
        if (node == null || depth > 12 || sb.length() > MAX_LEN) return;
        int id = System.identityHashCode(node);
        if (visited.contains(id)) return;
        visited.add(id);
        add(sb, node.getText());
        add(sb, node.getContentDescription());
        for (int i = 0; i < node.getChildCount(); i++) readNode(node.getChild(i), sb, visited, depth + 1);
    }

    private void add(StringBuilder sb, CharSequence cs) {
        if (cs == null) return;
        String s = cs.toString().trim();
        if (s.isEmpty()) return;
        if (sb.length() + s.length() > MAX_LEN) return;
        sb.append(' ').append(s);
    }

    private String normalize(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }
}
