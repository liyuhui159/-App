package com.liyuhui.yuliao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * 轻量离线回复引擎。
 *
 * 语料为项目内原创整理，不直接打包网络数据集原文。分类设计参考公开中文对话
 * 数据集常见的意图、场景、情绪与人格维度，保证 APK 可离线使用且体积可控。
 */
final class OfflineReplyEngine {
    private OfflineReplyEngine() {
    }

    private static final class IntentPack {
        final String name;
        final String[] keywords;
        final String[] replies;

        IntentPack(String name, String[] keywords, String[] replies) {
            this.name = name;
            this.keywords = keywords;
            this.replies = replies;
        }
    }

    private static IntentPack pack(String name, String keywords, String... replies) {
        return new IntentPack(name, keywords.split("\\|"), replies);
    }

    private static final List<IntentPack> PACKS = Arrays.asList(
            pack("问候", "你好|哈喽|嗨|在吗|早上好|早安|晚上好|好久不见",
                    "在呢，刚好被你逮个正着。",
                    "你好呀，今天这场聊天准备从哪儿开场？",
                    "好久不见，你这一出现，聊天框都精神了。",
                    "收到你的招呼，今天状态不错。",
                    "在的，你说，我认真听着。",
                    "来啦，今天有什么新鲜事？",
                    "这么正式地打招呼，我也得认真回一个：你好。",
                    "我在，信号满格，随时可以聊天。"),
            pack("在做什么", "干嘛|干什么|做什么|忙什么|在干嘛|在忙吗|有空吗",
                    "刚在处理一点小事，现在可以分你一点注意力。",
                    "本来没忙什么，你一问，突然有聊天任务了。",
                    "正在努力把今天过得像样一点，你呢？",
                    "忙里偷闲中，刚好看到你的消息。",
                    "在和生活斗智斗勇，目前暂时平手。",
                    "刚忙完一小段，现在可以聊会儿。",
                    "正在发呆，被你精准打断了。",
                    "没干什么大事，倒是挺想听听你在忙什么。"),
            pack("夸奖", "好看|漂亮|可爱|厉害|优秀|真棒|帅|聪明|有气质|有才",
                    "你这么会夸，我差点就信了。",
                    "谢谢，今天的好心情算你贡献了一份。",
                    "你眼光不错，这点我就不谦虚了。",
                    "这句话我先收藏，心情不好时拿出来复习。",
                    "被你这么一说，我得努力配得上这句夸奖。",
                    "夸得很专业，下次可以继续保持。",
                    "突然被认可，心里还挺暖的。",
                    "你负责夸，我负责开心，分工很合理。"),
            pack("感谢", "谢谢|多谢|感谢|辛苦了|麻烦你了",
                    "不用客气，能帮上忙就好。",
                    "小事一件，别把感谢说得这么隆重。",
                    "收到你的谢谢，今天的成就感到账了。",
                    "不客气，下次有事也可以直接说。",
                    "举手之劳，你顺利就行。",
                    "客气啦，我们这关系不用走流程。",
                    "好意收到，感谢的话可以兑换成下次的奶茶。",
                    "别客气，互相搭把手很正常。"),
            pack("道歉", "对不起|抱歉|不好意思|我错了|别生气|原谅我",
                    "没关系，愿意说开就已经很好了。",
                    "我收到你的道歉了，这件事我们慢慢翻篇。",
                    "知道你不是故意的，下次注意就好。",
                    "先不急着自责，把事情处理好更重要。",
                    "可以原谅，但我要保留假装严肃三分钟的权利。",
                    "没事，别让这点小插曲占用太多心情。",
                    "道歉有效，友情系统正在恢复正常。",
                    "我在意的是态度，你愿意解释就够了。"),
            pack("邀约", "一起|出来玩|吃饭|看电影|喝奶茶|见面|约吗|有时间出来|周末",
                    "可以呀，把时间地点发来，我看看怎么安排。",
                    "这个提议有吸引力，我先给它一个积极评价。",
                    "你负责发起，我负责认真考虑并大概率答应。",
                    "听起来不错，具体什么时候？",
                    "行啊，终于有人来拯救我的无聊日程了。",
                    "可以安排，但好吃的部分不能敷衍。",
                    "我有兴趣，先把计划说详细一点。",
                    "没问题，等你把时间定下来。"),
            pack("想念", "想你|想我吗|有没有想我|好想你|思念",
                    "有一点，本来想低调，结果被你问出来了。",
                    "想过，尤其是看到有趣的事情时。",
                    "你这么一问，想念程度又被手动调高了。",
                    "当然有，不然怎么会这么快回你。",
                    "有啊，只是我比较擅长把想念藏在正常聊天里。",
                    "刚好也想到你，这算不算双向定位成功？",
                    "想你这件事没有打卡，但确实发生过。",
                    "有些人不用天天出现，也会偶尔跑进脑海里。"),
            pack("晚安", "晚安|睡觉了|睡了|困了|我要睡|早点睡",
                    "晚安，今天的烦恼先暂停营业。",
                    "去睡吧，明天醒来再继续可爱。",
                    "晚安，祝你一觉睡到闹钟都不好意思响。",
                    "好梦，剩下的事交给明天处理。",
                    "收到，批准你进入省电模式。",
                    "早点休息，别让手机赢过你的睡意。",
                    "晚安，愿你梦里没有工作和未读消息。",
                    "睡吧，明天见的时候记得满血回来。"),
            pack("忙碌", "很忙|忙死了|没空|加班|工作好多|事情很多|晚点回",
                    "你先忙，不用急着回，忙完再说。",
                    "收到，你先处理正事，我不在消息框里催你。",
                    "辛苦了，等你有空我们再慢慢聊。",
                    "先把手上的事解决，聊天可以暂停但不会过期。",
                    "去忙吧，我批准这条消息延迟回复。",
                    "注意别忙到忘了喝水，其他的晚点再说。",
                    "理解，等你的空闲时间重新上线。",
                    "没关系，我把话题先放这儿，不会跑。"),
            pack("无聊", "无聊|没意思|好闲|不知道干嘛|好闷",
                    "那我们来给无聊制造一点工作量。",
                    "无聊已经被发现，是否启动聊天救援？",
                    "正好，我这里缺一个陪聊合伙人。",
                    "给你三个选项：聊天、讲八卦、一起想吃什么。",
                    "无聊的时候找我，算你找对部门了。",
                    "要不要交换一件今天最离谱的小事？",
                    "那就别让今天这么平淡，随便挑个话题开聊。",
                    "我可以陪你聊会儿，费用是分享一个有趣故事。"),
            pack("疲惫", "累|好累|累死|疲惫|没精神|困死|撑不住",
                    "辛苦了，先让自己喘口气，别硬撑。",
                    "今天已经很努力了，休息不是偷懒。",
                    "先喝口水坐一会儿，世界可以等你缓缓。",
                    "听起来确实累，给自己一点恢复时间吧。",
                    "电量见底就别开省电焦虑，直接充会儿电。",
                    "抱抱你，今天剩下的任务能简化就简化。",
                    "先照顾好自己，事情可以分轻重慢慢来。",
                    "累的时候不用表现得很坚强，我在听。"),
            pack("难过", "难过|不开心|心情不好|想哭|委屈|烦死了|郁闷",
                    "听起来你今天挺不好受的，愿意的话可以跟我说说。",
                    "先别急着逼自己开心，难过也需要一点时间。",
                    "我在，你可以慢慢讲，不用组织得很完美。",
                    "委屈先放我这里一点，别一个人全扛着。",
                    "今天可以不坚强，先把情绪安顿好。",
                    "我不急着讲大道理，先陪你把这阵情绪熬过去。",
                    "发生什么了？我认真听，不随便评价。",
                    "给你一个隔空拥抱，等你愿意时再说。"),
            pack("生气", "生气|气死了|火大|烦人|讨厌|不想理|气炸了",
                    "先把火气放一放，别让别人的问题罚你难受。",
                    "听起来确实很气，你先说，我不打断。",
                    "这事换我也会不舒服，先把经过理清楚。",
                    "可以生气，但先别在最上头的时候做决定。",
                    "气归气，记得给自己留一点冷静的空间。",
                    "谁惹你了？我先申请加入吐槽席。",
                    "先深呼吸，等情绪降一点再决定怎么处理。",
                    "你有理由不高兴，我陪你把这件事捋顺。"),
            pack("拒绝", "不行|不要|不想|算了|拒绝|没兴趣|不方便",
                    "明白，按你舒服的方式来就好。",
                    "没关系，不勉强，直接说清楚反而轻松。",
                    "收到，这个选项我们就先划掉。",
                    "可以理解，每个人都有自己不想做的事。",
                    "行，那就不安排，别有压力。",
                    "拒绝有效，我不会启动强行劝说程序。",
                    "没问题，我们换一个更合适的方案。",
                    "尊重你的决定，不需要为拒绝感到抱歉。"),
            pack("吃饭", "吃了吗|吃饭|早餐|午饭|晚饭|饿了|好吃|火锅|奶茶",
                    "还在研究吃什么，这大概是每天最难的选择题。",
                    "吃饭这件事值得认真，你有什么推荐？",
                    "一说到吃，我的聊天积极性立刻提高了。",
                    "还没，你这条消息成功提醒了我的胃。",
                    "吃了，不过听到好吃的还是可以再讨论一下。",
                    "今天吃得怎么样，有没有值得我羡慕的菜？",
                    "饿了就先去吃，别拿意志力和胃硬碰硬。",
                    "这个话题很危险，容易聊着聊着就下单。"),
            pack("工作学习", "上班|工作|学习|考试|作业|论文|开会|老板|老师|项目",
                    "听起来任务不少，先挑最重要的一件解决。",
                    "稳住，一件一件来，别让待办事项集体吓你。",
                    "辛苦了，完成一点也算向前推进。",
                    "先开个头，很多时候最难的只是第一步。",
                    "工作可以卷，心态别跟着打结。",
                    "给自己定个小目标，完成后记得休息一下。",
                    "这事确实费脑子，需要我陪你梳理思路吗？",
                    "先把能控制的部分做好，剩下的别提前焦虑。"),
            pack("关系试探", "喜欢我吗|爱我吗|什么关系|对象|男朋友|女朋友|谈恋爱|单身",
                    "这个问题有点超纲，不过我愿意认真回答。",
                    "关系要看相处，不如先看看我们聊天的默契。",
                    "你问得这么直接，我差点没准备好表情管理。",
                    "有些答案不用急着定义，可以慢慢相处出来。",
                    "我对你当然有好感，不然不会这么认真回。",
                    "这个问题值得当面聊，文字容易把语气弄丢。",
                    "你是在认真问，还是在偷偷测试我的反应？",
                    "先允许我保留一点悬念，毕竟故事才刚展开。"),
            pack("天气", "下雨|天气|好冷|好热|降温|太阳|刮风",
                    "这种天气最适合把出门计划改成舒服一点的版本。",
                    "记得带伞，别让天气临时给你加剧情。",
                    "冷就多穿点，风度可以偶尔给温度让路。",
                    "热成这样，出门都需要一点勇气。",
                    "天气负责变化，你负责照顾好自己。",
                    "听起来外面不太友好，出门注意安全。",
                    "这种天气很适合找个舒服的地方慢慢聊天。",
                    "记得看温度，不要只凭窗外的阳光做判断。"),
            pack("玩笑互动", "哈哈|笑死|笑疯|逗你|开玩笑|骗你的|哈哈哈",
                    "你先笑，我负责保持一脸无辜。",
                    "很好，今天的快乐指标终于完成了一项。",
                    "你笑得这么大声，我都怀疑自己很有喜剧天赋。",
                    "行，这局算你逗到我了。",
                    "我差点认真了，还好理智及时上线。",
                    "这个玩笑通过审核，可以保留。",
                    "哈哈，看来我们的聊天频道很合拍。",
                    "继续，我已经搬好小板凳准备听下一段。"),
            pack("冷淡简短", "哦|嗯|行吧|随便|再说|不知道|没事",
                    "感觉你兴致不太高，是累了还是有心事？",
                    "好，那我先不追问，你想聊时再找我。",
                    "收到，我给你留点空间。",
                    "这个回复很省字，但信息量好像不小。",
                    "行，那我们先暂停，别勉强聊天。",
                    "是不是我没接对话题？可以换一个。",
                    "没关系，状态不好时不用硬聊。",
                    "好，我在这儿，你什么时候想说都行。"),
            pack("通用", "",
                    "这句话我收到了，听起来还挺有故事。",
                    "你继续说，我正在认真跟上这个话题。",
                    "这个角度有点意思，我想听听你的想法。",
                    "懂了，那你现在更希望我听你说，还是一起想办法？",
                    "我大概明白你的意思了，细节方便再讲一点吗？",
                    "好，我接住这个话题了。",
                    "听起来不只是表面这么简单。",
                    "可以，我们就从这句话继续聊下去。")
    );

    static List<String> generate(String source, String scene, String persona,
                                 String length, int count) {
        String normalized = normalize(source);
        IntentPack selected = selectPack(normalized);
        List<String> bases = new ArrayList<>(Arrays.asList(selected.replies));
        Collections.shuffle(bases, new Random((source + scene + persona).hashCode()));

        Set<String> unique = new LinkedHashSet<>();
        int attempts = 0;
        while (unique.size() < count && attempts < count * 5) {
            String base = bases.get(attempts % bases.size());
            String reply = applyPersona(base, persona, attempts);
            reply = applyScene(reply, scene, selected.name, attempts);
            reply = applyLength(reply, length, bases, attempts);
            unique.add(clean(reply));
            attempts++;
        }
        return new ArrayList<>(unique);
    }

    private static IntentPack selectPack(String source) {
        IntentPack best = PACKS.get(PACKS.size() - 1);
        int bestScore = 0;
        for (IntentPack pack : PACKS) {
            int score = 0;
            for (String keyword : pack.keywords) {
                if (!keyword.isEmpty() && source.contains(keyword)) {
                    score += Math.max(2, keyword.length());
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = pack;
            }
        }
        return best;
    }

    private static String applyPersona(String base, String persona, int index) {
        switch (persona) {
            case "高情商温柔":
                return index % 3 == 0 ? "我能理解你的感受。" + base : base;
            case "轻松俏皮":
                return index % 2 == 0 ? base + "🙂" : "报告，" + base;
            case "克制礼貌":
                return base.replace("哈哈，", "").replace("呀", "").replace("啦", "");
            case "毒舌但不伤人":
                return index % 2 == 0 ? "你这句话挺会给人出题，不过，" + lowerFirst(base) : base + "，勉强算你有点道理。";
            case "真诚直接":
                return index % 2 == 0 ? "认真说，" + lowerFirst(base) : base;
            case "幽默风趣":
            default:
                return index % 3 == 0 ? base + "，本聊天系统已正式受理。" : base;
        }
    }

    private static String applyScene(String reply, String scene, String intent, int index) {
        if ("暧昧互动".equals(scene) && index % 3 == 1 && !"拒绝".equals(intent)) {
            return reply + " 你再多说一点，我可能会更认真。";
        }
        if ("朋友互怼".equals(scene) && index % 3 == 1) {
            return reply + " 先声明，我这是友情范围内的吐槽。";
        }
        if ("破冰开场".equals(scene) && index % 2 == 0) {
            return reply + " 顺便问一句，你今天遇到最有意思的事是什么？";
        }
        if ("缓和气氛".equals(scene) && index % 2 == 0) {
            return "我们先别把气氛弄得太紧张。" + reply;
        }
        if ("职场沟通".equals(scene)) {
            return reply.replace("你呀", "你").replace("🙂", "");
        }
        return reply;
    }

    private static String applyLength(String reply, String length, List<String> bases, int index) {
        if ("短句".equals(length)) {
            int punctuation = firstPunctuation(reply);
            if (punctuation > 5 && punctuation < 24) return reply.substring(0, punctuation + 1);
            return reply.length() > 28 ? reply.substring(0, 28) + "……" : reply;
        }
        if ("一段话".equals(length)) {
            String next = bases.get((index + 3) % bases.size());
            if (!reply.contains(next)) return reply + " " + next;
        }
        return reply;
    }

    private static int firstPunctuation(String value) {
        int best = -1;
        for (char mark : new char[]{'。', '！', '？', '，'}) {
            int index = value.indexOf(mark);
            if (index >= 0 && (best < 0 || index < best)) best = index;
        }
        return best;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？、；：‘’“”（）【】]+", "");
    }

    private static String lowerFirst(String value) {
        if (value.isEmpty()) return value;
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private static String clean(String value) {
        return value.replace("。。", "。")
                .replace("，，", "，")
                .replace("。 ，", "，")
                .trim();
    }
}