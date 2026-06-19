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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BillAssistService extends AccessibilityService {
    private static final int MAX_LEN = 1600;
    private static final long REPEAT_MS = 30000L;
    private static final long PENDING_MS = 180000L;
    private static final long EVENT_THROTTLE_MS = 260L;
    private long lastEventAt = 0L;
    private String lastShortText = "";

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

        StringBuilder eventTextBuilder = new StringBuilder();
        addEventText(event, eventTextBuilder);
        String eventText = normalize(eventTextBuilder.toString());
        long now = System.currentTimeMillis();
        if (eventText.equals(lastShortText) && now - lastEventAt < EVENT_THROTTLE_MS) return;
        lastShortText = eventText;
        lastEventAt = now;

        boolean hasPending = hasValidPending(pkg) || hasValidDraft(pkg);
        boolean needDeepScan = fastMayBePayment(eventText) || hasPending || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        if (!needDeepScan) return;

        String text = eventText;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            StringBuilder sb = new StringBuilder(eventText);
            readNode(root, sb, new HashSet<>(), 0);
            text = normalize(sb.toString());
        }
        if (text.length() < 4) return;

        if (isWeChatTransferInputPage(text)) {
            cacheTransferDraft(pkg, text);
            return;
        }

        if (isWeChatPaymentSuccess(text)) {
            cachePendingPayment(pkg, text);
            showAfterSuccessIfDraftReady(pkg, text);
            return;
        }

        if (tryCompletePendingFromChat(pkg, text)) return;

        if (isBillDetail(text)) {
            showPopup(pkg, text, 0D, null, null, "账单页面识别");
        }
    }

    @Override
    public void onInterrupt() { }

    private boolean hasValidPending(String pkg) {
        SharedPreferences sp = getSharedPreferences("bill_assist_pending", MODE_PRIVATE);
        long savedAt = sp.getLong("time", 0L);
        String pendingPkg = sp.getString("pkg", "");
        return savedAt > 0 && pkg.equals(pendingPkg) && System.currentTimeMillis() - savedAt <= PENDING_MS;
    }

    private boolean hasValidDraft(String pkg) {
        SharedPreferences sp = getSharedPreferences("bill_assist_draft", MODE_PRIVATE);
        long savedAt = sp.getLong("time", 0L);
        String draftPkg = sp.getString("pkg", "");
        return savedAt > 0 && pkg.equals(draftPkg) && System.currentTimeMillis() - savedAt <= PENDING_MS;
    }

    private boolean fastMayBePayment(String t) {
        if (t == null) return false;
        return t.contains("支付成功") || t.contains("确认收款") || t.contains("¥") || t.contains("元") || t.contains("转账") || t.contains("红包") || t.contains("账单") || t.contains("订单") || t.contains("付款") || t.contains("收款") || t.contains("消费") || t.contains("转账给") || t.contains("转账金额") || t.contains("微信号");
    }

    private boolean isWeChatTransferInputPage(String t) {
        if (t == null) return false;
        boolean title = t.contains("转账给") || t.contains("转账金额");
        boolean amount = t.contains("¥") || t.matches(".*(?:转账金额|金额).*[-+]?\\d+(?:\\.\\d{1,2})?.*");
        boolean button = t.contains("转账");
        boolean notSuccess = !t.contains("支付成功") && !t.contains("确认收款") && !t.contains("当前状态");
        return title && amount && button && notSuccess;
    }

    private void cacheTransferDraft(String pkg, String text) {
        double amount = extractTransferAmount(text);
        if (amount <= 0) return;
        String recipient = extractTransferTarget(text);
        String wechatId = extractWechatId(text);
        String message = extractTransferMessage(text);
        SharedPreferences sp = getSharedPreferences("bill_assist_draft", MODE_PRIVATE);
        sp.edit()
                .putString("pkg", pkg)
                .putString("raw", text)
                .putFloat("amount", (float) amount)
                .putString("recipient", recipient)
                .putString("wechat_id", wechatId)
                .putString("message", message)
                .putLong("time", System.currentTimeMillis())
                .apply();
    }

    private double extractTransferAmount(String text) {
        if (text == null) return 0D;
        Matcher m = Pattern.compile("¥\\s*([0-9]+(?:\\.[0-9]{1,2})?)").matcher(text);
        if (m.find()) return toDouble(m.group(1));
        m = Pattern.compile("转账金额\\s*([0-9]+(?:\\.[0-9]{1,2})?)").matcher(text);
        if (m.find()) return toDouble(m.group(1));
        return 0D;
    }

    private String extractTransferTarget(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("转账给\\s*(.{1,28}?)(?:微信号|转账金额|¥|头像|$)").matcher(text);
        if (m.find()) return cleanName(m.group(1));
        return "";
    }

    private String extractWechatId(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("微信号[:：]?\\s*([A-Za-z0-9_\\-]{4,32})").matcher(text);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    private String extractTransferMessage(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("(?:你好|留言|备注)\\s*([^转账]{0,28})").matcher(text);
        if (text.contains("你好")) return "你好";
        if (m.find()) return cleanName(m.group(1));
        return "";
    }

    private void cachePendingPayment(String pkg, String text) {
        MainActivity.Entry e = MainActivity.SmartParser.parseOne(text);
        if (e == null || e.amount <= 0) return;
        SharedPreferences sp = getSharedPreferences("bill_assist_pending", MODE_PRIVATE);
        sp.edit()
                .putString("pkg", pkg)
                .putString("raw", text)
                .putFloat("amount", (float) e.amount)
                .putString("recipient", extractRecipient(text))
                .putLong("time", System.currentTimeMillis())
                .apply();
    }

    private void showAfterSuccessIfDraftReady(String pkg, String successText) {
        SharedPreferences draft = getSharedPreferences("bill_assist_draft", MODE_PRIVATE);
        long savedAt = draft.getLong("time", 0L);
        String draftPkg = draft.getString("pkg", "");
        if (savedAt <= 0 || !pkg.equals(draftPkg) || System.currentTimeMillis() - savedAt > PENDING_MS) return;

        double draftAmount = draft.getFloat("amount", 0F);
        double successAmount = extractTransferAmount(successText);
        if (successAmount <= 0) {
            MainActivity.Entry e = MainActivity.SmartParser.parseOne(successText);
            if (e != null) successAmount = e.amount;
        }
        double amount = successAmount > 0 ? successAmount : draftAmount;
        if (amount <= 0) return;

        String recipient = bestName(draft.getString("recipient", ""), extractRecipient(successText));
        String msg = draft.getString("message", "");
        String wechatId = draft.getString("wechat_id", "");
        String note = "微信转账" + (recipient.length() > 0 ? " - " + recipient : "") + (msg.length() > 0 ? "（" + msg + "）" : "");
        String raw = draft.getString("raw", "") + "\n支付成功页确认：" + successText + (wechatId.length() > 0 ? "\n微信号：" + wechatId : "");

        String key = pkg + "|draft-success|" + amount + "|" + note;
        SharedPreferences repeat = getSharedPreferences("bill_assist_repeat", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (key.equals(repeat.getString("last", "")) && now - repeat.getLong("last_time", 0L) < REPEAT_MS) return;
        repeat.edit().putString("last", key).putLong("last_time", now).apply();
        draft.edit().clear().apply();
        getSharedPreferences("bill_assist_pending", MODE_PRIVATE).edit().clear().apply();

        showPopup(pkg, raw, amount, note, "人情", "微信转账页+支付成功页识别");
    }

    private boolean tryCompletePendingFromChat(String pkg, String chatText) {
        SharedPreferences sp = getSharedPreferences("bill_assist_pending", MODE_PRIVATE);
        long savedAt = sp.getLong("time", 0L);
        if (savedAt <= 0 || System.currentTimeMillis() - savedAt > PENDING_MS) return false;
        String pendingPkg = sp.getString("pkg", "");
        if (!pkg.equals(pendingPkg)) return false;
        if (!looksLikeChatPage(chatText)) return false;

        double amount = sp.getFloat("amount", 0F);
        String paymentRaw = sp.getString("raw", "");
        String recipient = bestName(sp.getString("recipient", ""), extractChatContact(chatText));
        String payKind = inferPayKind(paymentRaw + " " + chatText);
        String note = payKind + (recipient.length() > 0 ? " - " + recipient : "");
        String allRaw = paymentRaw + "\n聊天页二次识别：" + chatText;

        String key = pkg + "|" + amount + "|" + note;
        SharedPreferences repeat = getSharedPreferences("bill_assist_repeat", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (key.equals(repeat.getString("last", "")) && now - repeat.getLong("last_time", 0L) < REPEAT_MS) return true;
        repeat.edit().putString("last", key).putLong("last_time", now).apply();
        sp.edit().clear().apply();

        showPopup(pkg, allRaw, amount, note, "人情", "微信支付成功页+聊天页识别");
        return true;
    }

    private void showPopup(String pkg, String raw, double forcedAmount, String forcedNote, String forcedCategory, String source) {
        MainActivity.Entry e = MainActivity.SmartParser.parseOne(raw);
        if ((e == null || e.amount <= 0) && forcedAmount <= 0) return;
        Intent i = new Intent(this, QuickEntryActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("raw_text", raw);
        i.putExtra("forced_account", accountFromPackage(pkg));
        i.putExtra("forced_type", "支出");
        i.putExtra("source", source);
        if (forcedAmount > 0) i.putExtra("forced_amount", forcedAmount);
        if (forcedNote != null && forcedNote.trim().length() > 0) i.putExtra("forced_note", forcedNote.trim());
        if (forcedCategory != null && forcedCategory.trim().length() > 0) i.putExtra("forced_category", forcedCategory.trim());
        startActivity(i);
    }

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

    private boolean isWeChatPaymentSuccess(String t) {
        return t.contains("支付成功")
                && t.matches(".*[-+]?\\d+(?:\\.\\d{1,2})?.*")
                && ((t.contains("待") && t.contains("确认收款")) || t.contains("完成") || t.contains("请收款"));
    }

    private boolean isBillDetail(String t) {
        if (t == null || t.length() < 8) return false;
        boolean money = t.matches(".*[-+]?\\d+(?:\\.\\d{1,2})?.*") && (t.contains("元") || t.contains("¥") || t.contains("付款") || t.contains("支付") || t.contains("消费") || t.contains("收款") || t.contains("到账") || t.contains("退款"));
        boolean detail = t.contains("支付成功") || t.contains("当前状态") || t.contains("转账时间") || t.contains("转账单号") || t.contains("支付方式") || t.contains("账单") || t.contains("订单") || t.contains("二维码收款") || t.contains("收款方备注") || t.contains("商品说明") || t.contains("商户");
        return money && detail;
    }

    private boolean looksLikeChatPage(String t) {
        if (t == null) return false;
        boolean chatWords = t.contains("发送") || t.contains("按住 说话") || t.contains("语音") || t.contains("表情") || t.contains("红包") || t.contains("转账") || t.contains("收款") || t.contains("[转账] 请收款");
        boolean notSuccessPage = !t.contains("支付成功") && !t.contains("完成");
        return chatWords && notSuccessPage;
    }

    private String inferPayKind(String t) {
        if (t == null) return "微信转账";
        if (t.contains("红包") || t.contains("微信红包")) return "微信红包";
        if (t.contains("转账") || t.contains("确认收款") || t.contains("收款")) return "微信转账";
        return "微信支付";
    }

    private String extractRecipient(String t) {
        if (t == null) return "";
        Matcher m = Pattern.compile("待(.{1,20}?)确认收款").matcher(t);
        if (m.find()) return cleanName(m.group(1));
        m = Pattern.compile("转账给\\s*(.{1,28}?)(?:微信号|转账金额|¥|头像|$)").matcher(t);
        if (m.find()) return cleanName(m.group(1));
        return "";
    }

    private String extractChatContact(String t) {
        if (t == null) return "";
        String[] parts = t.split(" ");
        for (String p : parts) {
            String s = cleanName(p);
            if (s.length() >= 1 && s.length() <= 12 && !isUiWord(s)) return s;
        }
        return "";
    }

    private String bestName(String a, String b) {
        a = cleanName(a);
        b = cleanName(b);
        if (a.length() > 0) return a;
        return b;
    }

    private String cleanName(String s) {
        if (s == null) return "";
        return s.replace("待", "")
                .replace("确认收款", "")
                .replace("微信号", "")
                .replace("转账给", "")
                .replace("转账金额", "")
                .replace("微信", "")
                .replace("支付成功", "")
                .replace("¥", "")
                .replaceAll("L[0-9A-Za-z_\\-]{4,32}", "")
                .replaceAll("[0-9.]+", "")
                .replaceAll("[：:，,。；;\n\r]", "")
                .trim();
    }

    private boolean isUiWord(String s) {
        return s.equals("返回") || s.equals("更多") || s.equals("发送") || s.equals("语音") || s.equals("表情") || s.equals("完成") || s.equals("支付成功") || s.equals("聊天信息") || s.equals("微信") || s.equals("按住说话") || s.equals("转账") || s.equals("红包") || s.equals("修改") || s.equals("头像") || s.equals("微信号");
    }

    private double toDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0D; }
    }

    private void addEventText(AccessibilityEvent event, StringBuilder sb) {
        List<CharSequence> list = event.getText();
        if (list != null) for (CharSequence cs : list) add(sb, cs);
        add(sb, event.getContentDescription());
    }

    private void readNode(AccessibilityNodeInfo node, StringBuilder sb, Set<Integer> visited, int depth) {
        if (node == null || depth > 9 || sb.length() > MAX_LEN) return;
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
        if (sb.indexOf(s) >= 0) return;
        if (sb.length() + s.length() > MAX_LEN) return;
        sb.append(' ').append(s);
    }

    private String normalize(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }
}
