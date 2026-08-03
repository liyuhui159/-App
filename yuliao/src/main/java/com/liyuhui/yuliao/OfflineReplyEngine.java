package com.liyuhui.yuliao;

import java.util.List;

/**
 * 离线回复引擎兼容入口。
 *
 * 实际检索、意图匹配、人设变体与场景组合由 LargeOfflineCorpus 完成。
 */
final class OfflineReplyEngine {
    private OfflineReplyEngine() {
    }

    static List<String> generate(String source, String scene, String persona,
                                 String length, int count) {
        return LargeOfflineCorpus.generate(source, scene, persona, length, count);
    }
}
