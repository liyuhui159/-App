package com.liyuhui.smartledger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Remembers a user's merchant-category corrections so repeat purchases no longer need AI. */
public final class MerchantCategoryStore {
    private static final String PREFS = "merchant_category_memory_v1";
    private static final Pattern NAMED_MERCHANT = Pattern.compile(
            "(?:商户|商家|收款方|收款商户|付款给|向)\\s*[:：]?\\s*([^\\n，,。；;]{2,48})");

    private MerchantCategoryStore() { }

    public static String find(Context context, String merchant) {
        String key = normalizeKey(merchant);
        if (key.isEmpty()) return "";
        String value = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key, "");
        if (value == null || value.isEmpty()) return "";
        int split = value.indexOf('|');
        String category = split >= 0 ? value.substring(0, split) : value;
        return LedgerCategories.normalize(category);
    }

    public static void remember(Context context, String merchant, String category) {
        String key = normalizeKey(merchant);
        category = LedgerCategories.normalize(category);
        if (key.isEmpty() || "其他".equals(category)) return;
        String safeMerchant = compact(merchant, 48).replace('|', ' ');
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(key, category + "|" + safeMerchant).apply();
    }

    public static int size(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getAll().size();
    }

    public static void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }

    public static String summary(Context context, int limit) {
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, ?> item : context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll().entrySet()) {
            String value = String.valueOf(item.getValue());
            int split = value.indexOf('|');
            String category = split >= 0 ? value.substring(0, split) : value;
            String merchant = split >= 0 ? value.substring(split + 1) : item.getKey();
            if (result.length() > 0) result.append('\n');
            result.append(LedgerCategories.icon(category)).append(' ')
                    .append(merchant).append(" → ").append(category);
            if (++count >= limit) break;
        }
        return result.toString();
    }

    /** Extracts the merchant/store text from common WeChat, Alipay and bank notifications. */
    public static String extractMerchant(String raw) {
        if (raw == null) return "";
        String text = raw.replace('\r', '\n').replaceAll("[\\t ]+", " ").trim();
        Matcher named = NAMED_MERCHANT.matcher(text);
        if (named.find()) return cleanCandidate(named.group(1));

        String[] lines = text.split("\\n+");
        String best = "";
        int bestScore = Integer.MIN_VALUE;
        for (String line : lines) {
            String candidate = cleanCandidate(line);
            if (candidate.length() < 2 || candidate.length() > 48) continue;
            int score = 0;
            if (containsMerchantHint(candidate)) score += 6;
            if (candidate.matches(".*[\\u4e00-\\u9fa5A-Za-z].*")) score += 2;
            if (candidate.matches(".*(?:¥|￥|\\d+(?:\\.\\d{1,2})?\\s*元).*")) score -= 3;
            if (isBoilerplate(candidate)) score -= 7;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= -1 ? best : "";
    }

    private static String cleanCandidate(String value) {
        if (value == null) return "";
        String s = value;
        s = s.replaceAll("(?:¥|￥)\\s*[-+]?\\d+(?:\\.\\d{1,2})?", " ");
        s = s.replaceAll("[-+]?\\d+(?:\\.\\d{1,2})?\\s*元", " ");
        s = s.replaceAll("订单号?[:：]?\\s*[A-Za-z0-9_-]+", " ");
        s = s.replaceAll("(?:微信支付|支付宝|支付成功|付款成功|扣款成功|消费成功|交易成功|收款到账|服务通知)", " ");
        s = s.replaceAll("[【】\\[\\]（）()<>《》]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("^[：:，,。；;\\-]+|[：:，,。；;\\-]+$", "").trim();
        return compact(s, 48);
    }

    private static boolean containsMerchantHint(String value) {
        String s = value.toLowerCase(Locale.ROOT);
        return s.contains("店") || s.contains("超市") || s.contains("餐") || s.contains("果")
                || s.contains("商贸") || s.contains("公司") || s.contains("便利")
                || s.contains("mall") || s.contains("market") || s.contains("coffee")
                || s.contains("restaurant") || s.contains("food");
    }

    private static boolean isBoilerplate(String value) {
        String s = value.toLowerCase(Locale.ROOT);
        return s.equals("微信支付") || s.equals("支付宝") || s.equals("支付成功")
                || s.equals("付款成功") || s.equals("服务通知") || s.equals("交易提醒")
                || s.contains("支付金额") || s.contains("付款金额") || s.contains("当前余额")
                || s.contains("查看详情") || s.contains("点击查看") || s.contains("验证码");
    }

    private static String normalizeKey(String merchant) {
        if (merchant == null) return "";
        String key = merchant.toLowerCase(Locale.ROOT)
                .replaceAll("(?:有限责任公司|股份有限公司|有限公司|分公司|旗舰店|专营店|门店|店铺)$", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
        return compact(key, 80);
    }

    private static String compact(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}
