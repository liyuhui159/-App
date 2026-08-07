package com.liyuhui.smartledger;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts transaction facts locally; AI is deliberately used only for merchant category classification. */
public final class PaymentNotificationParser {
    private static final Pattern SYMBOL_AMOUNT = Pattern.compile("(?:¥|￥)\\s*([-+]?\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern YUAN_AMOUNT = Pattern.compile("([-+]?\\d+(?:\\.\\d{1,2})?)\\s*元");
    private static final Pattern LABELED_AMOUNT = Pattern.compile(
            "(?:支付金额|付款金额|消费金额|扣款金额|交易金额|收款金额|到账金额|退款金额|金额)\\s*[:：]?\\s*([-+]?\\d+(?:\\.\\d{1,2})?)");

    private PaymentNotificationParser() { }

    public static MainActivity.Entry parse(String packageName, String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        double amount = extractAmount(raw.replace(",", ""));
        if (amount <= 0 || amount >= 100000000d) return null;

        MainActivity.Entry entry = new MainActivity.Entry();
        entry.amount = amount;
        entry.type = inferType(raw);
        entry.account = accountFromPackage(packageName, raw);
        String merchant = MerchantCategoryStore.extractMerchant(raw);
        entry.note = merchant.isEmpty() ? compact(raw, 96) : merchant;
        entry.category = LedgerCategories.inferStrong(merchant + " " + raw, entry.type);
        entry.source = "自动记账";
        entry.time = System.currentTimeMillis();
        return entry;
    }

    public static boolean looksLikePayment(String raw) {
        if (raw == null || raw.trim().isEmpty()) return false;
        String s = raw.toLowerCase(Locale.ROOT);
        if (s.contains("bytes/s") || s.contains("kib/s") || s.contains("mib/s")
                || s.contains("vpn") || s.contains("验证码") || s.contains("登录确认")
                || s.contains("已连接") || s.contains("电量")) return false;
        boolean transactionWord = has(s, "支付", "付款", "消费", "扣款", "交易成功", "收款", "到账", "退款", "转账", "红包");
        return transactionWord && extractAmount(raw.replace(",", "")) > 0;
    }

    private static double extractAmount(String raw) {
        double value = firstValid(LABELED_AMOUNT.matcher(raw));
        if (value > 0) return value;
        value = firstValid(SYMBOL_AMOUNT.matcher(raw));
        if (value > 0) return value;
        return firstValid(YUAN_AMOUNT.matcher(raw));
    }

    private static double firstValid(Matcher matcher) {
        while (matcher.find()) {
            try {
                double value = Math.abs(Double.parseDouble(matcher.group(1)));
                if (value > 0 && value < 100000000d) return value;
            } catch (Exception ignored) { }
        }
        return 0d;
    }

    private static String inferType(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        if (has(s, "退款成功", "退款到账", "已退款", "原路退回", "收入", "工资到账", "奖金到账")) return "收入";
        if (has(s, "向你付款", "收款到账", "收款成功", "已收款", "转入", "到账")) {
            if (!has(s, "付款", "消费", "扣款", "支付给", "向商户")) return "收入";
        }
        return "支出";
    }

    private static String accountFromPackage(String packageName, String raw) {
        String p = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        if (p.contains("tencent.mm") || raw.contains("微信")) return "微信";
        if (p.contains("alipay") || raw.contains("支付宝") || raw.contains("花呗")) return "支付宝";
        if (raw.contains("信用卡")) return "信用卡";
        if (p.contains("bank") || p.contains("cmb") || p.contains("icbc") || p.contains("ccb")
                || p.contains("abc") || p.contains("boc") || raw.contains("银行")) return "银行卡";
        return "其他";
    }

    private static boolean has(String text, String... keys) {
        for (String key : keys) if (text.contains(key.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static String compact(String value, int max) {
        String s = value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
