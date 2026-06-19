package com.liyuhui.smartledger;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BillAssistService extends AccessibilityService {
    private static final int MAX_LEN = 2600;
    private static final long REPEAT_MS = 30000L;
    private static final long PENDING_MS = 180000L;
    private static final long EVENT_THROTTLE_MS = 220L;
    private static final int OVERLAY_WIDTH_DP = 348;

    private long lastEventAt = 0L;
    private long lastProbeAt = 0L;
    private String lastShortText = "";
    private String lastOverlayKey = "";
    private WindowManager overlayManager;
    private View overlayView;
    private float overlayDownX;
    private float overlayDownY;
    private int overlayStartX;
    private int overlayStartY;

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

        String eventText = readEventText(event);
        long now = System.currentTimeMillis();
        if (eventText.equals(lastShortText) && now - lastEventAt < EVENT_THROTTLE_MS) return;
        lastShortText = eventText;
        lastEventAt = now;

        boolean hasPending = hasValidPending(pkg) || hasValidDraft(pkg);
        boolean needDeepScan = fastMayBePayment(eventText)
                || hasPending
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED;
        if (!needDeepScan) return;

        String pageText = readCurrentWindowText(eventText);
        if (pageText.length() < 2) return;

        if (isWeChatTransferInputPage(pageText)) {
            cacheTransferDraft(pkg, pageText);
            return;
        }

        if (isWeChatPaymentSuccess(pageText)) {
            cachePendingPayment(pkg, pageText);
            showAfterSuccessIfDraftReady(pkg, pageText);
            return;
        }

        if (tryCompletePendingFromChat(pkg, pageText)) return;

        if (isBillDetail(pageText)) {
            EntryCandidate candidate = buildCandidate(pkg, pageText, 0D, null, null, "账单页面识别");
            showConfirmForCandidate(candidate);
            return;
        }

        if (maybeWeChatTransferScene(pageText) && now - lastProbeAt > 6000L && overlayView == null) {
            lastProbeAt = now;
            showProbeOverlay(pkg, pageText);
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        removeGlassOverlay();
        super.onDestroy();
    }

    private String readEventText(AccessibilityEvent event) {
        StringBuilder sb = new StringBuilder();
        List<CharSequence> list = event.getText();
        if (list != null) for (CharSequence cs : list) add(sb, cs);
        add(sb, event.getContentDescription());
        return normalize(sb.toString());
    }

    private String readCurrentWindowText(String prefix) {
        String text = prefix == null ? "" : prefix;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            StringBuilder sb = new StringBuilder(text);
            readNode(root, sb, new HashSet<>(), 0);
            text = normalize(sb.toString());
        }
        return text;
    }

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
        return t.contains("支付成功") || t.contains("转账成功") || t.contains("确认收款") || t.contains("请收款")
                || t.contains("¥") || t.contains("￥") || t.contains("元") || t.contains("转账") || t.contains("红包")
                || t.contains("账单") || t.contains("订单") || t.contains("付款") || t.contains("收款") || t.contains("消费")
                || t.contains("转账给") || t.contains("转账金额") || t.contains("微信号") || t.contains("二维码收款");
    }

    private boolean maybeWeChatTransferScene(String t) {
        if (t == null) return false;
        return t.contains("转账给") || t.contains("转账金额") || t.contains("微信号") || t.contains("[转账] 请收款")
                || t.contains("支付成功") || t.contains("转账成功") || t.contains("账单详情") || t.contains("二维码收款")
                || (t.contains("转账") && (t.contains("¥") || t.contains("￥") || t.contains("请收款") || t.contains("收款")));
    }

    private boolean isWeChatTransferInputPage(String t) {
        if (t == null) return false;
        boolean notSuccess = !t.contains("支付成功") && !t.contains("转账成功") && !t.contains("确认收款") && !t.contains("当前状态");
        boolean hasTitle = t.contains("转账给") || t.contains("转账金额") || t.contains("微信号");
        boolean hasMoney = extractAmount(t) > 0;
        boolean hasTransferWord = t.contains("转账");
        return notSuccess && hasTransferWord && (hasMoney || hasTitle);
    }

    private void cacheTransferDraft(String pkg, String text) {
        double amount = extractAmount(text);
        if (amount <= 0) {
            showDebugTextOverlay("读到了转账页，但没读到金额：\n" + shorten(text, 220));
            return;
        }
        String recipient = extractTransferTarget(text);
        String wechatId = extractWechatId(text);
        String message = extractTransferMessage(text);
        SharedPreferences sp = getSharedPreferences("bill_assist_draft", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastTip = sp.getLong("draft_tip_time", 0L);
        sp.edit()
                .putString("pkg", pkg)
                .putString("raw", text)
                .putFloat("amount", (float) amount)
                .putString("recipient", recipient)
                .putString("wechat_id", wechatId)
                .putString("message", message)
                .putLong("time", now)
                .putLong("draft_tip_time", now)
                .apply();
        if (now - lastTip > 2500L) {
            String note = buildTransferNote(recipient, message);
            showDraftGlassOverlay(amount, note, inferCategoryForPage(text, "支出", "人情"));
        }
    }

    private void cachePendingPayment(String pkg, String text) {
        double amount = extractAmount(text);
        if (amount <= 0) return;
        SharedPreferences sp = getSharedPreferences("bill_assist_pending", MODE_PRIVATE);
        sp.edit()
                .putString("pkg", pkg)
                .putString("raw", text)
                .putFloat("amount", (float) amount)
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
        double successAmount = extractAmount(successText);
        double amount = successAmount > 0 ? successAmount : draftAmount;
        if (amount <= 0) return;

        String recipient = bestName(draft.getString("recipient", ""), extractRecipient(successText));
        String msg = draft.getString("message", "");
        String wechatId = draft.getString("wechat_id", "");
        String note = buildTransferNote(recipient, msg);
        String raw = draft.getString("raw", "") + "\n支付成功页确认：" + successText + (wechatId.length() > 0 ? "\n微信号：" + wechatId : "");

        String key = pkg + "|draft-success|" + amount + "|" + note;
        SharedPreferences repeat = getSharedPreferences("bill_assist_repeat", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (key.equals(repeat.getString("last", "")) && now - repeat.getLong("last_time", 0L) < REPEAT_MS) return;
        repeat.edit().putString("last", key).putLong("last_time", now).apply();
        draft.edit().clear().apply();
        getSharedPreferences("bill_assist_pending", MODE_PRIVATE).edit().clear().apply();

        EntryCandidate candidate = buildCandidate(pkg, raw, amount, note, "人情", "微信转账页+支付成功页识别");
        showConfirmForCandidate(candidate);
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

        EntryCandidate candidate = buildCandidate(pkg, allRaw, amount, note, "人情", "微信支付成功页+聊天页识别");
        showConfirmForCandidate(candidate);
        return true;
    }

    private void manualRecognizeCurrentPage(String pkg) {
        String text = readCurrentWindowText("");
        if (text.length() < 2) {
            showDebugTextOverlay("没有读到当前页面文字。请确认无障碍服务已重新开启。 ");
            return;
        }
        double amount = extractAmount(text);
        if (amount > 0) {
            String type = inferTypeForPage(text);
            String recipient = extractTransferTarget(text);
            String note = buildNoteForPage(text, recipient);
            String category = inferCategoryForPage(text, type, null);
            EntryCandidate candidate = buildCandidate(pkg, text, amount, note, category, "手动强制识别当前页");
            candidate.type = type;
            showConfirmForCandidate(candidate);
            return;
        }
        showDebugTextOverlay("已读取当前页，但没找到金额。读到的文字：\n" + shorten(text, 280));
    }

    private EntryCandidate buildCandidate(String pkg, String raw, double forcedAmount, String forcedNote, String forcedCategory, String source) {
        EntryCandidate c = new EntryCandidate();
        c.raw = raw == null ? "" : raw;
        c.account = accountFromPackage(pkg);
        c.type = inferTypeForPage(c.raw);
        c.amount = forcedAmount > 0 ? forcedAmount : extractAmount(c.raw);
        c.category = inferCategoryForPage(c.raw, c.type, forcedCategory);
        c.note = forcedNote != null && forcedNote.trim().length() > 0 ? forcedNote.trim() : buildNoteForPage(c.raw, extractTransferTarget(c.raw));
        c.source = source == null ? "无障碍识别" : source;
        return c;
    }

    private void showConfirmForCandidate(EntryCandidate c) {
        if (c == null || c.amount <= 0) return;
        String key = c.account + "|" + c.type + "|" + c.amount + "|" + c.note + "|" + c.source;
        if (key.equals(lastOverlayKey) && overlayView != null) return;
        lastOverlayKey = key;
        QuickNotificationHelper.showBillReview(this, c.raw, c.account, c.type, c.source, c.amount, c.note, c.category);
        showConfirmGlassOverlay(c);
    }

    private double extractAmount(String text) {
        if (text == null) return 0D;
        Matcher m = Pattern.compile("[¥￥]\\s*([-+]?[0-9]+(?:\\.[0-9]{1,2})?)").matcher(text);
        if (m.find()) return Math.abs(toDouble(m.group(1)));
        m = Pattern.compile("(?:金额|转账金额|收款|付款|消费)\\s*([-+]?[0-9]+(?:\\.[0-9]{1,2})?)").matcher(text);
        if (m.find()) return Math.abs(toDouble(m.group(1)));
        if (text.contains("转账") || text.contains("支付成功") || text.contains("转账成功") || text.contains("账单详情") || text.contains("二维码收款")) {
            m = Pattern.compile("(?:^|\\s)([-+]?[0-9]+\\.[0-9]{1,2})(?:\\s|$)").matcher(text);
            double best = 0D;
            while (m.find()) {
                double v = Math.abs(toDouble(m.group(1)));
                if (v > 0 && (best <= 0 || v < best)) best = v;
            }
            if (best > 0) return best;
        }
        return 0D;
    }

    private String inferTypeForPage(String raw) {
        if (raw == null) return "支出";
        if (raw.contains("收款") && !raw.contains("付款") && !raw.contains("待") && !raw.contains("请收款")) return "收入";
        if (raw.contains("退款") || raw.contains("到账")) return "收入";
        return "支出";
    }

    private String inferCategoryForPage(String raw, String type, String forced) {
        if (forced != null && forced.trim().length() > 0) return forced.trim();
        if (raw == null) return "其他";
        if (raw.contains("转账") || raw.contains("红包") || raw.contains("请收款") || raw.contains("确认收款")) return "人情";
        String c = MainActivity.SmartParser.inferCategory(raw, type == null ? "支出" : type);
        if (c == null || c.trim().isEmpty()) return "其他";
        return c;
    }

    private String buildNoteForPage(String raw, String recipient) {
        if (raw == null) raw = "";
        if (raw.contains("转账") || raw.contains("请收款") || raw.contains("确认收款")) return buildTransferNote(recipient, extractTransferMessage(raw));
        String merchant = extractMerchant(raw);
        if (merchant.length() > 0) return merchant;
        if (raw.contains("二维码收款")) return "二维码付款";
        if (raw.contains("支付成功")) return "支付消费";
        return "自动识别记账";
    }

    private String buildTransferNote(String recipient, String message) {
        recipient = recipient == null ? "" : recipient.trim();
        message = message == null ? "" : message.trim();
        return "微信转账" + (recipient.length() > 0 ? " - " + recipient : "") + (message.length() > 0 ? "（" + message + "）" : "");
    }

    private String extractMerchant(String raw) {
        if (raw == null) return "";
        String[] known = {"肯德基", "KFC", "麦当劳", "美团", "饿了么", "瑞幸", "星巴克", "蜜雪", "喜茶", "霸王茶姬", "茶百道", "古茗", "滴滴", "淘宝", "京东", "拼多多"};
        for (String k : known) if (raw.contains(k)) return k;
        String[] keys = {"商品说明", "商品名称", "商户名称", "商户", "收款方备注", "收款方", "对方"};
        for (String key : keys) {
            String v = extractAfterKey(raw, key, 32);
            if (v.length() > 0) return v;
        }
        return "";
    }

    private String extractAfterKey(String raw, String key, int maxLen) {
        int i = raw.indexOf(key);
        if (i < 0) return "";
        String sub = raw.substring(Math.min(raw.length(), i + key.length())).replaceFirst("^[：: ]+", "").trim();
        String[] stops = {"交易状态", "支付成功", "转账时间", "转账单号", "支付方式", "金额", "账单", "订单", "查看", "申请", "全部"};
        int cut = sub.length();
        for (String stop : stops) {
            int p = sub.indexOf(stop);
            if (p > 0 && p < cut) cut = p;
        }
        sub = sub.substring(0, Math.min(cut, sub.length())).trim();
        if (sub.length() > maxLen) sub = sub.substring(0, maxLen);
        return cleanName(sub);
    }

    private String extractTransferTarget(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("转账给\\s*(.{1,36}?)(?:微信号|转账金额|[¥￥]|头像|$)").matcher(text);
        if (m.find()) return cleanName(m.group(1));
        m = Pattern.compile("待(.{1,24}?)确认收款").matcher(text);
        if (m.find()) return cleanName(m.group(1));
        return "";
    }

    private String extractRecipient(String t) {
        return extractTransferTarget(t);
    }

    private String extractWechatId(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("微信号[:：]?\\s*([A-Za-z0-9_\\-]{4,32})").matcher(text);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    private String extractTransferMessage(String text) {
        if (text == null) return "";
        if (text.contains("你好")) return "你好";
        Matcher m = Pattern.compile("(?:留言|备注)[:：]?\\s*(.{1,28}?)(?:修改|转账|$)").matcher(text);
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

    private boolean isWeChatPaymentSuccess(String t) {
        return (t.contains("支付成功") || t.contains("转账成功") || t.contains("请收款")) && extractAmount(t) > 0;
    }

    private boolean isBillDetail(String t) {
        if (t == null || t.length() < 8) return false;
        boolean money = extractAmount(t) > 0;
        boolean detail = t.contains("支付成功") || t.contains("转账成功") || t.contains("当前状态") || t.contains("转账时间") || t.contains("转账单号") || t.contains("支付方式") || t.contains("账单") || t.contains("订单") || t.contains("二维码收款") || t.contains("收款方备注") || t.contains("商品说明") || t.contains("商户");
        return money && detail;
    }

    private boolean looksLikeChatPage(String t) {
        if (t == null) return false;
        boolean chatWords = t.contains("发送") || t.contains("按住 说话") || t.contains("语音") || t.contains("表情") || t.contains("红包") || t.contains("转账") || t.contains("收款") || t.contains("[转账] 请收款");
        boolean notSuccessPage = !t.contains("支付成功") && !t.contains("转账成功") && !t.contains("完成");
        return chatWords && notSuccessPage;
    }

    private String inferPayKind(String t) {
        if (t == null) return "微信转账";
        if (t.contains("红包") || t.contains("微信红包")) return "微信红包";
        if (t.contains("转账") || t.contains("确认收款") || t.contains("收款")) return "微信转账";
        return "微信支付";
    }

    private void showProbeOverlay(String pkg, String text) {
        LinearLayout card = baseOverlayCard();
        card.addView(overlayText("灵犀检测到微信账单页面", 15, Color.rgb(25, 28, 45), true));
        card.addView(overlayText("点按钮强制读取并自动判断分类", 12, Color.rgb(100, 105, 125), false));
        TextView preview = overlayText(shorten(text, 90), 11, Color.rgb(120, 124, 145), false);
        preview.setGravity(Gravity.LEFT);
        card.addView(preview);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(8), 0, 0);
        Button close = overlayButton("关闭", Color.argb(90, 255, 255, 255), Color.rgb(70, 74, 95));
        Button scan = overlayButton("识别当前页", Color.rgb(52, 199, 89), Color.WHITE);
        close.setOnClickListener(v -> removeGlassOverlay());
        scan.setOnClickListener(v -> manualRecognizeCurrentPage(pkg));
        actions.addView(close, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView gap = new TextView(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
        actions.addView(scan, new LinearLayout.LayoutParams(0, dp(44), 1));
        card.addView(actions);
        showBottomOverlay(card, 16000);
    }

    private void showDraftGlassOverlay(double amount, String note, String category) {
        LinearLayout card = baseOverlayCard();
        card.addView(overlayText("已识别转账页面", 17, Color.rgb(25, 28, 45), true));
        card.addView(overlayText("¥" + String.format(Locale.CHINA, "%.2f", amount), 28, Color.rgb(20, 120, 72), true));
        card.addView(overlayText(note + " · " + category, 14, Color.rgb(80, 84, 105), false));
        card.addView(overlayText("付款成功后会弹出底部确认保存", 12, Color.rgb(115, 120, 140), false));
        Button ok = overlayButton("知道了", Color.argb(120, 255, 255, 255), Color.rgb(70, 74, 95));
        ok.setOnClickListener(v -> removeGlassOverlay());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(44));
        blp.setMargins(0, dp(10), 0, 0);
        card.addView(ok, blp);
        showBottomOverlay(card, 8000);
    }

    private void showConfirmGlassOverlay(EntryCandidate c) {
        LinearLayout card = baseOverlayCard();
        card.addView(overlayText("确认记账", 17, Color.rgb(25, 28, 45), true));
        card.addView(overlayText("¥" + String.format(Locale.CHINA, "%.2f", c.amount), 30, Color.rgb(20, 120, 72), true));
        card.addView(overlayText(MainActivity.iconForCategory(c.category) + " " + c.category + " · " + c.account, 14, Color.rgb(80, 84, 105), true));
        card.addView(overlayText(c.note == null || c.note.trim().isEmpty() ? "自动识别记账" : c.note.trim(), 13, Color.rgb(100, 105, 125), false));
        card.addView(overlayText("底部确认，保存后停留当前页面", 12, Color.rgb(115, 120, 140), false));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, 0);
        Button cancel = overlayButton("稍后", Color.argb(90, 255, 255, 255), Color.rgb(70, 74, 95));
        Button confirm = overlayButton("保存", Color.rgb(52, 199, 89), Color.WHITE);
        cancel.setOnClickListener(v -> removeGlassOverlay());
        confirm.setOnClickListener(v -> {
            saveDirect(c);
            removeGlassOverlay();
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView gap = new TextView(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        actions.addView(confirm, new LinearLayout.LayoutParams(0, dp(46), 1));
        card.addView(actions);
        showBottomOverlay(card, 22000);
    }

    private void saveDirect(EntryCandidate c) {
        MainActivity.Entry entry = new MainActivity.Entry();
        entry.amount = c.amount;
        entry.type = c.type == null ? "支出" : c.type;
        entry.category = c.category == null ? "其他" : c.category;
        entry.account = c.account == null ? "微信" : c.account;
        entry.note = c.note == null ? "自动识别记账" : c.note;
        entry.source = c.source == null ? "无障碍底部确认" : c.source;
        entry.time = System.currentTimeMillis();
        new MainActivity.LedgerDb(this).insert(entry);
        QuickNotificationHelper.show(this);
        Toast.makeText(this, "已保存入账", Toast.LENGTH_SHORT).show();
    }

    private void showDebugTextOverlay(String message) {
        LinearLayout card = baseOverlayCard();
        TextView title = overlayText("灵犀调试信息", 16, Color.rgb(25, 28, 45), true);
        TextView msg = overlayText(message, 11, Color.rgb(90, 95, 115), false);
        msg.setGravity(Gravity.LEFT);
        Button close = overlayButton("关闭", Color.argb(120, 255, 255, 255), Color.rgb(70, 74, 95));
        close.setOnClickListener(v -> removeGlassOverlay());
        card.addView(title);
        card.addView(msg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(44));
        blp.setMargins(0, dp(10), 0, 0);
        card.addView(close, blp);
        showBottomOverlay(card, 20000);
    }

    private LinearLayout baseOverlayCard() {
        removeGlassOverlay();
        overlayManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(glassBg(Color.argb(198, 255, 255, 255), dp(28)));
        card.setElevation(dp(14));
        return card;
    }

    private void showBottomOverlay(View card, long autoDismissMs) {
        if (overlayManager == null) overlayManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (overlayManager == null) return;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(OVERLAY_WIDTH_DP),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(28);
        attachDrag(card, lp);
        overlayView = card;
        try {
            overlayManager.addView(overlayView, lp);
            if (autoDismissMs > 0) new Handler(Looper.getMainLooper()).postDelayed(this::removeGlassOverlay, autoDismissMs);
        } catch (Exception ignored) { overlayView = null; }
    }

    private void attachDrag(View card, WindowManager.LayoutParams lp) {
        card.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                overlayDownX = event.getRawX(); overlayDownY = event.getRawY(); overlayStartX = lp.x; overlayStartY = lp.y; return false;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                lp.x = overlayStartX + (int) (event.getRawX() - overlayDownX);
                lp.y = Math.max(0, overlayStartY - (int) (event.getRawY() - overlayDownY));
                try { overlayManager.updateViewLayout(card, lp); } catch (Exception ignored) { }
                return true;
            }
            return false;
        });
    }

    private void removeGlassOverlay() {
        if (overlayManager != null && overlayView != null) {
            try { overlayManager.removeView(overlayView); } catch (Exception ignored) { }
        }
        overlayView = null;
    }

    private TextView overlayText(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value == null ? "" : value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button overlayButton(String value, int bg, int fg) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(14);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(glassBg(bg, dp(22)));
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
            return false;
        });
        return b;
    }

    private GradientDrawable glassBg(int color, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.argb(238, 255, 255, 255), color, Color.argb(105, 255, 255, 255)});
        g.setCornerRadius(radius);
        g.setStroke(dp(1), Color.argb(205, 255, 255, 255));
        return g;
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

    private String cleanName(String s) {
        if (s == null) return "";
        return s.replace("待", "")
                .replace("确认收款", "")
                .replace("微信号", "")
                .replace("转账给", "")
                .replace("转账金额", "")
                .replace("微信", "")
                .replace("支付成功", "")
                .replace("转账成功", "")
                .replace("收款方备注", "")
                .replace("商品说明", "")
                .replace("¥", "")
                .replace("￥", "")
                .replaceAll("L[0-9A-Za-z_\\-]{4,32}", "")
                .replaceAll("[0-9.]+", "")
                .replaceAll("[：:，,。；;\\n\\r]", "")
                .trim();
    }

    private boolean isUiWord(String s) {
        return s.equals("返回") || s.equals("更多") || s.equals("发送") || s.equals("语音") || s.equals("表情") || s.equals("完成") || s.equals("支付成功") || s.equals("转账成功") || s.equals("聊天信息") || s.equals("微信") || s.equals("按住说话") || s.equals("转账") || s.equals("红包") || s.equals("修改") || s.equals("头像") || s.equals("微信号");
    }

    private String bestName(String a, String b) {
        a = cleanName(a);
        b = cleanName(b);
        return a.length() > 0 ? a : b;
    }

    private double toDouble(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0D; } }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void readNode(AccessibilityNodeInfo node, StringBuilder sb, Set<Integer> visited, int depth) {
        if (node == null || depth > 14 || sb.length() > MAX_LEN) return;
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

    private String normalize(String s) { return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim(); }
    private String shorten(String s, int n) { if (s == null) return ""; s = normalize(s); return s.length() > n ? s.substring(0, n) + "…" : s; }

    private static class EntryCandidate {
        String raw;
        String account;
        String type;
        String category;
        String note;
        String source;
        double amount;
    }
}
