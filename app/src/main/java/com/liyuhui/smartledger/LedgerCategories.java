package com.liyuhui.smartledger;

import java.util.Locale;

/** Shared category taxonomy used by merchant rules, AI validation and the clean home page. */
public final class LedgerCategories {
    public static final String[] ALL = {
            "餐饮", "水果", "生鲜", "日用品", "购物", "交通", "住房", "医疗",
            "学习", "娱乐", "通讯", "人情", "工资", "退款", "其他"
    };

    private LedgerCategories() { }

    public static boolean isAllowed(String category) {
        if (category == null) return false;
        for (String value : ALL) if (value.equals(category)) return true;
        return false;
    }

    public static String normalize(String category) {
        return isAllowed(category) ? category : "其他";
    }

    public static String icon(String category) {
        if ("餐饮".equals(category)) return "🍜";
        if ("水果".equals(category)) return "🍎";
        if ("生鲜".equals(category)) return "🥬";
        if ("日用品".equals(category)) return "🧻";
        if ("购物".equals(category)) return "🛍️";
        if ("交通".equals(category)) return "🚗";
        if ("住房".equals(category)) return "🏠";
        if ("医疗".equals(category)) return "💊";
        if ("学习".equals(category)) return "📚";
        if ("娱乐".equals(category)) return "🎮";
        if ("通讯".equals(category)) return "📱";
        if ("人情".equals(category)) return "🧧";
        if ("工资".equals(category)) return "💼";
        if ("退款".equals(category)) return "↩️";
        return "📌";
    }

    /** Returns a category only when the merchant wording is sufficiently explicit. */
    public static String inferStrong(String text, String type) {
        if (text == null) return "其他";
        String s = text.toLowerCase(Locale.ROOT);
        if ("收入".equals(type)) {
            if (has(s, "工资", "薪资", "薪酬", "奖金", "劳务费")) return "工资";
            if (has(s, "退款", "退回", "原路退回", "撤销")) return "退款";
            if (has(s, "红包", "转账", "收款")) return "人情";
            return "其他";
        }

        if (has(s, "水果店", "水果超市", "果业", "果园", "鲜果", "百果园", "鲜丰水果", "果多美", "切果", "榴莲", "苹果店", "香蕉店")) return "水果";
        if (has(s, "菜市场", "生鲜", "肉铺", "肉店", "海鲜", "水产", "蔬菜", "买菜", "农贸", "盒马鲜生", "叮咚买菜", "朴朴")) return "生鲜";
        if (has(s, "餐厅", "饭店", "饭馆", "面馆", "拉面", "米粉", "火锅", "烧烤", "小吃", "食堂", "外卖", "美团外卖", "饿了么", "肯德基", "麦当劳", "必胜客", "咖啡", "奶茶", "瑞幸", "星巴克", "蜜雪冰城", "喜茶", "茶百道", "古茗")) return "餐饮";
        if (has(s, "便利店", "超市", "生活馆", "日用品", "百货", "沃尔玛", "永辉", "华联", "家乐福", "屈臣氏", "名创优品")) return "日用品";
        if (has(s, "淘宝", "天猫", "京东", "拼多多", "抖音商城", "唯品会", "服饰", "鞋业", "数码", "电器", "手机店", "电脑店", "商场")) return "购物";
        if (has(s, "滴滴", "高德打车", "出租车", "地铁", "公交", "铁路", "火车票", "机票", "航空", "加油", "停车", "高速", "充电桩")) return "交通";
        if (has(s, "房租", "物业", "水费", "电费", "燃气", "租金", "公寓")) return "住房";
        if (has(s, "医院", "诊所", "药房", "药店", "体检", "挂号", "医保", "口腔")) return "医疗";
        if (has(s, "书店", "课程", "培训", "学费", "考试", "文具", "教育")) return "学习";
        if (has(s, "电影院", "影院", "ktv", "游戏", "网吧", "景区", "门票", "会员", "腾讯天游", "点券")) return "娱乐";
        if (has(s, "话费", "流量", "宽带", "中国移动", "中国联通", "中国电信")) return "通讯";
        if (has(s, "红包", "份子", "礼金", "转账给", "请客")) return "人情";
        return "其他";
    }

    private static boolean has(String text, String... keys) {
        for (String key : keys) if (text.contains(key.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
