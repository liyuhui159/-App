package com.liyuhui.yuliao;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DualModeActivity extends Activity {
    private static final int BG = Color.rgb(246, 247, 251);
    private static final int PRIMARY = Color.rgb(109, 74, 255);
    private static final int PRIMARY_DARK = Color.rgb(44, 37, 85);
    private static final int MUTED = Color.rgb(112, 113, 130);
    private static final int BORDER = Color.rgb(226, 227, 235);
    private static final int SUCCESS = Color.rgb(24, 166, 122);
    private static final int OFFLINE = Color.rgb(37, 153, 110);

    private static final String PREFS = "yuliao_settings";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses";
    private static final String DEFAULT_MODEL = "gpt-5-mini";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private boolean onlineMode;
    private Button offlineModeButton;
    private Button onlineModeButton;
    private Button generateButton;
    private Button settingsButton;
    private TextView modeDescription;
    private TextView statusText;
    private EditText messageInput;
    private EditText customRequirement;
    private Spinner sceneSpinner;
    private Spinner personaSpinner;
    private Spinner lengthSpinner;
    private Spinner countSpinner;
    private LinearLayout resultContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        setOnlineMode(false);
        consumeIncomingText(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeIncomingText(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (messageInput != null && messageInput.getText().toString().trim().isEmpty()) {
            pasteClipboard(false);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = row();
        header.setPadding(dp(20), dp(20), dp(14), dp(18));
        header.setBackground(gradient(PRIMARY, Color.rgb(139, 94, 255)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleBox = vertical();
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        titleBox.addView(text("语聊", 29, Color.WHITE, true));
        titleBox.addView(text("离线语录 + 在线 AI 双模式回复助手", 14,
                Color.argb(225, 255, 255, 255), false));

        settingsButton = button("API 设置", Color.argb(55, 255, 255, 255), Color.WHITE);
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(92), dp(42)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical();
        page.setPadding(dp(14), dp(14), dp(14), dp(32));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout modeCard = card();
        page.addView(modeCard);
        modeCard.addView(sectionTitle("选择回复方式"));

        LinearLayout modeRow = row();
        modeRow.setPadding(0, dp(10), 0, 0);
        offlineModeButton = button("离线语录", OFFLINE, Color.WHITE);
        onlineModeButton = button("在线 AI", Color.rgb(237, 238, 245), PRIMARY_DARK);
        offlineModeButton.setOnClickListener(v -> setOnlineMode(false));
        onlineModeButton.setOnClickListener(v -> setOnlineMode(true));
        modeRow.addView(offlineModeButton, weighted(dp(48)));
        addGap(modeRow, 10);
        modeRow.addView(onlineModeButton, weighted(dp(48)));
        modeCard.addView(modeRow);

        modeDescription = text("", 13, MUTED, false);
        modeDescription.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams modeDescLp = new LinearLayout.LayoutParams(-1, -2);
        modeDescLp.setMargins(0, dp(10), 0, 0);
        modeCard.addView(modeDescription, modeDescLp);

        LinearLayout inputCard = card();
        LinearLayout.LayoutParams inputCardLp = new LinearLayout.LayoutParams(-1, -2);
        inputCardLp.setMargins(0, dp(12), 0, 0);
        page.addView(inputCard, inputCardLp);
        inputCard.addView(sectionTitle("对方发来的内容"));
        inputCard.addView(text("复制一句话或一段聊天记录，也可以从其他 App 分享文字到语聊。",
                13, MUTED, false));

        messageInput = input("粘贴对方发来的话……", 6);
        messageInput.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(-1, dp(150));
        messageLp.setMargins(0, dp(10), 0, dp(10));
        inputCard.addView(messageInput, messageLp);

        LinearLayout inputActions = row();
        Button paste = button("粘贴剪贴板", Color.rgb(238, 234, 255), PRIMARY);
        paste.setOnClickListener(v -> pasteClipboard(true));
        inputActions.addView(paste, weighted(dp(44)));
        addGap(inputActions, 10);
        Button clear = button("清空", Color.rgb(242, 243, 247), PRIMARY_DARK);
        clear.setOnClickListener(v -> {
            messageInput.setText("");
            customRequirement.setText("");
            resultContainer.removeAllViews();
            statusText.setText("");
        });
        inputActions.addView(clear, weighted(dp(44)));
        inputCard.addView(inputActions);

        LinearLayout configCard = card();
        LinearLayout.LayoutParams configLp = new LinearLayout.LayoutParams(-1, -2);
        configLp.setMargins(0, dp(12), 0, 0);
        page.addView(configCard, configLp);
        configCard.addView(sectionTitle("回复设定"));

        configCard.addView(label("聊天场景"));
        sceneSpinner = spinner(new String[]{
                "日常聊天", "暧昧互动", "朋友互怼", "破冰开场", "缓和气氛", "职场沟通"
        });
        configCard.addView(sceneSpinner);

        configCard.addView(label("回复人设"));
        personaSpinner = spinner(new String[]{
                "幽默风趣", "高情商温柔", "轻松俏皮", "克制礼貌", "毒舌但不伤人", "真诚直接"
        });
        configCard.addView(personaSpinner);

        LinearLayout selectionRow = row();
        LinearLayout lengthBox = vertical();
        lengthBox.addView(label("回复长度"));
        lengthSpinner = spinner(new String[]{"短句", "正常", "一段话"});
        lengthSpinner.setSelection(1);
        lengthBox.addView(lengthSpinner);
        selectionRow.addView(lengthBox, new LinearLayout.LayoutParams(0, -2, 1));
        addGap(selectionRow, 10);

        LinearLayout countBox = vertical();
        countBox.addView(label("生成数量"));
        countSpinner = spinner(new String[]{"4 条", "6 条", "8 条"});
        countSpinner.setSelection(1);
        countBox.addView(countSpinner);
        selectionRow.addView(countBox, new LinearLayout.LayoutParams(0, -2, 1));
        configCard.addView(selectionRow);

        configCard.addView(label("在线补充要求（离线模式不使用）"));
        customRequirement = input("例如：不要太油腻、像熟人聊天、带一点反问……", 2);
        configCard.addView(customRequirement);

        generateButton = button("从离线语录匹配回复", OFFLINE, Color.WHITE);
        generateButton.setTextSize(17);
        generateButton.setTypeface(Typeface.DEFAULT_BOLD);
        generateButton.setOnClickListener(v -> generateReplies());
        LinearLayout.LayoutParams generateLp = new LinearLayout.LayoutParams(-1, dp(56));
        generateLp.setMargins(0, dp(14), 0, 0);
        configCard.addView(generateButton, generateLp);

        statusText = text("", 13, MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, dp(12), 0, 0);
        page.addView(statusText, statusLp);

        resultContainer = vertical();
        page.addView(resultContainer, new LinearLayout.LayoutParams(-1, -2));

        TextView note = text(
                "离线语录为项目内清洗整理的原创精选模板，不连接网络；在线模式才会把输入内容发送到你配置的 AI 接口。",
                12, MUTED, false);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(dp(8), dp(18), dp(8), 0);
        page.addView(note, noteLp);
    }

    private void setOnlineMode(boolean online) {
        onlineMode = online;
        if (online) {
            offlineModeButton.setBackground(rounded(Color.rgb(237, 238, 245), Color.rgb(237, 238, 245), 14));
            offlineModeButton.setTextColor(PRIMARY_DARK);
            onlineModeButton.setBackground(rounded(PRIMARY, PRIMARY, 14));
            onlineModeButton.setTextColor(Color.WHITE);
            modeDescription.setText("在线 AI：分析原话的语气、情绪、关系和潜台词，再根据场景、人设、长度与补充要求实时生成。需要联网和 API Key。");
            customRequirement.setEnabled(true);
            customRequirement.setAlpha(1f);
            generateButton.setText("AI 分析并生成回复");
            generateButton.setBackground(rounded(PRIMARY, PRIMARY, 14));
            settingsButton.setAlpha(1f);
        } else {
            offlineModeButton.setBackground(rounded(OFFLINE, OFFLINE, 14));
            offlineModeButton.setTextColor(Color.WHITE);
            onlineModeButton.setBackground(rounded(Color.rgb(237, 238, 245), Color.rgb(237, 238, 245), 14));
            onlineModeButton.setTextColor(PRIMARY_DARK);
            modeDescription.setText("离线语录：无需网络和 API Key。根据关键词识别问候、邀约、夸奖、道歉、忙碌、冷场、情绪等意图，再按场景和人设组合多条回复。");
            customRequirement.setEnabled(false);
            customRequirement.setAlpha(0.5f);
            generateButton.setText("从离线语录匹配回复");
            generateButton.setBackground(rounded(OFFLINE, OFFLINE, 14));
            settingsButton.setAlpha(0.75f);
        }
        statusText.setText("");
        if (resultContainer != null) resultContainer.removeAllViews();
    }

    private void generateReplies() {
        String source = messageInput.getText().toString().trim();
        if (source.isEmpty()) {
            toast("请先粘贴对方发来的内容");
            return;
        }

        String scene = sceneSpinner.getSelectedItem().toString();
        String persona = personaSpinner.getSelectedItem().toString();
        String length = lengthSpinner.getSelectedItem().toString();
        int count = Integer.parseInt(countSpinner.getSelectedItem().toString().substring(0, 1));
        resultContainer.removeAllViews();

        if (!onlineMode) {
            List<String> replies = OfflineReplyEngine.generate(source, scene, persona, length, count);
            statusText.setTextColor(OFFLINE);
            statusText.setText("离线匹配完成：已生成 " + replies.size() + " 条，可直接复制");
            renderReplies(replies, "离线语录");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String endpoint = prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT).trim();
        String apiKey = prefs.getString(KEY_API_KEY, "").trim();
        String model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).trim();
        if (apiKey.isEmpty()) {
            toast("在线模式需要先填写 API Key");
            showSettingsDialog();
            return;
        }
        if (endpoint.isEmpty() || model.isEmpty()) {
            toast("API 地址和模型名称不能为空");
            showSettingsDialog();
            return;
        }

        String extra = customRequirement.getText().toString().trim();
        setLoading(true, "AI 正在分析语气、情绪和潜台词……");
        executor.execute(() -> {
            try {
                List<String> replies = AiReplyClient.generate(
                        endpoint, apiKey, model, source, scene, persona, length, count, extra);
                runOnUiThread(() -> {
                    setLoading(false, "在线生成完成：已生成 " + replies.size() + " 条");
                    renderReplies(replies, "在线 AI");
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> setLoading(false, "生成失败：" + message));
            }
        });
    }

    private void renderReplies(List<String> replies, String sourceLabel) {
        resultContainer.removeAllViews();
        for (int i = 0; i < replies.size(); i++) {
            addReplyCard(i + 1, replies.get(i), sourceLabel);
        }
    }

    private void addReplyCard(int index, String reply, String sourceLabel) {
        LinearLayout replyCard = card();
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, dp(12), 0, 0);
        resultContainer.addView(replyCard, cardLp);

        LinearLayout titleRow = row();
        titleRow.addView(text("回复方案 " + index, 13, PRIMARY, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView modeTag = text(sourceLabel, 12, onlineMode ? PRIMARY : OFFLINE, true);
        modeTag.setPadding(dp(9), dp(4), dp(9), dp(4));
        modeTag.setBackground(rounded(
                onlineMode ? Color.rgb(238, 234, 255) : Color.rgb(231, 247, 240),
                onlineMode ? Color.rgb(238, 234, 255) : Color.rgb(231, 247, 240), 12));
        titleRow.addView(modeTag);
        replyCard.addView(titleRow);

        TextView body = text(reply, 17, PRIMARY_DARK, false);
        body.setLineSpacing(0, 1.18f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.setMargins(0, dp(9), 0, dp(12));
        replyCard.addView(body, bodyLp);

        LinearLayout actions = row();
        Button copy = button("复制", Color.rgb(238, 234, 255), PRIMARY);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("语聊回复", reply));
            }
            toast("已复制，可返回聊天窗口粘贴");
        });
        actions.addView(copy, weighted(dp(44)));
        addGap(actions, 8);

        Button share = button("分享", Color.rgb(231, 247, 240), SUCCESS);
        share.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, reply);
            startActivity(Intent.createChooser(shareIntent, "发送回复"));
        });
        actions.addView(share, weighted(dp(44)));
        addGap(actions, 8);

        Button refine = button(onlineMode ? "继续优化" : "转在线优化",
                Color.rgb(242, 243, 247), PRIMARY_DARK);
        refine.setOnClickListener(v -> {
            messageInput.setText(reply);
            messageInput.setSelection(messageInput.length());
            if (!onlineMode) setOnlineMode(true);
            customRequirement.requestFocus();
            toast("已放回输入框，可补充要求后在线优化");
        });
        actions.addView(refine, weighted(dp(44)));
        replyCard.addView(actions);
    }

    private void setLoading(boolean loading, String message) {
        generateButton.setEnabled(!loading);
        generateButton.setAlpha(loading ? 0.65f : 1f);
        generateButton.setText(loading ? "AI 生成中……" : "AI 分析并生成回复");
        statusText.setText(message);
        statusText.setTextColor(message.startsWith("生成失败") ? Color.rgb(210, 65, 65) : SUCCESS);
    }

    private void consumeIncomingText(Intent intent) {
        if (intent == null || messageInput == null) return;
        CharSequence incoming = null;
        if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            incoming = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        } else if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            incoming = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        }
        if (incoming != null && !incoming.toString().trim().isEmpty()) {
            messageInput.setText(incoming.toString().trim());
            messageInput.setSelection(messageInput.length());
        }
    }

    private void pasteClipboard(boolean showToast) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            if (showToast) toast("剪贴板里没有文字");
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            if (showToast) toast("剪贴板里没有文字");
            return;
        }
        CharSequence value = clip.getItemAt(0).coerceToText(this);
        if (value == null || value.toString().trim().isEmpty()) {
            if (showToast) toast("剪贴板里没有可用文字");
            return;
        }
        messageInput.setText(value.toString().trim());
        messageInput.setSelection(messageInput.length());
        if (showToast) toast("已粘贴");
    }

    private void showSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout box = vertical();
        box.setPadding(dp(18), dp(8), dp(18), dp(4));
        box.addView(text("在线 AI 接口设置", 21, PRIMARY_DARK, true));
        box.addView(text("仅在线模式使用。支持 OpenAI Responses API，也兼容常见 Chat Completions 接口。",
                13, MUTED, false));

        box.addView(label("API 地址"));
        EditText endpoint = input(DEFAULT_ENDPOINT, 1);
        endpoint.setText(prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT));
        box.addView(endpoint);

        box.addView(label("API Key"));
        EditText key = input("sk-……", 1);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(prefs.getString(KEY_API_KEY, ""));
        box.addView(key);

        box.addView(label("模型名称"));
        EditText model = input(DEFAULT_MODEL, 1);
        model.setText(prefs.getString(KEY_MODEL, DEFAULT_MODEL));
        box.addView(model);

        TextView warning = text(
                "API Key 只保存在当前设备的 App 私有数据中，不要把 Key 写进代码或提交到 GitHub。",
                12, Color.rgb(166, 93, 20), false);
        LinearLayout.LayoutParams warningLp = new LinearLayout.LayoutParams(-1, -2);
        warningLp.setMargins(0, dp(10), 0, 0);
        box.addView(warning, warningLp);

        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.addView(box);
        new AlertDialog.Builder(this)
                .setView(dialogScroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    prefs.edit()
                            .putString(KEY_ENDPOINT, endpoint.getText().toString().trim())
                            .putString(KEY_API_KEY, key.getText().toString().trim())
                            .putString(KEY_MODEL, model.getText().toString().trim())
                            .apply();
                    toast("在线 API 设置已保存");
                })
                .show();
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(Color.WHITE, BORDER, 18));
        card.setElevation(dp(2));
        return card;
    }

    private EditText input(String hint, int minLines) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(15);
        input.setTextColor(PRIMARY_DARK);
        input.setHintTextColor(Color.rgb(155, 156, 170));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setMinLines(minLines);
        input.setBackground(rounded(Color.rgb(250, 250, 253), BORDER, 12));
        return input;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(rounded(Color.rgb(250, 250, 253), BORDER, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, 0, 0, dp(6));
        spinner.setLayoutParams(lp);
        return spinner;
    }

    private TextView sectionTitle(String value) {
        return text(value, 18, PRIMARY_DARK, true);
    }

    private TextView label(String value) {
        TextView label = text(value, 13, MUTED, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, dp(6));
        label.setLayoutParams(lp);
        return label;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rounded(background, background, 14));
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout vertical() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }

    private LinearLayout.LayoutParams weighted(int height) {
        return new LinearLayout.LayoutParams(0, height, 1);
    }

    private void addGap(LinearLayout parent, int widthDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(dp(widthDp), 1));
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end) {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}