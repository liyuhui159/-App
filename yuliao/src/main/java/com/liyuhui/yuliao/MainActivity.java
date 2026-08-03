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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(246, 247, 251);
    private static final int PRIMARY = Color.rgb(109, 74, 255);
    private static final int PRIMARY_DARK = Color.rgb(44, 37, 85);
    private static final int MUTED = Color.rgb(112, 113, 130);
    private static final int BORDER = Color.rgb(226, 227, 235);
    private static final int SUCCESS = Color.rgb(24, 166, 122);

    private static final String PREFS = "yuliao_settings";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses";
    private static final String DEFAULT_MODEL = "gpt-5-mini";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText messageInput;
    private EditText customRequirement;
    private Spinner sceneSpinner;
    private Spinner personaSpinner;
    private Spinner lengthSpinner;
    private Spinner countSpinner;
    private Button generateButton;
    private TextView statusText;
    private LinearLayout resultContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(20), dp(16), dp(18));
        header.setBackground(gradient(PRIMARY, Color.rgb(139, 94, 255), 0));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        titleBox.addView(text("语聊", 29, Color.WHITE, true));
        titleBox.addView(text("把难回的话，变成自然又有趣的回复", 14, Color.argb(225, 255, 255, 255), false));

        Button settings = compactButton("API 设置", Color.argb(55, 255, 255, 255), Color.WHITE);
        settings.setOnClickListener(v -> showSettingsDialog());
        header.addView(settings, new LinearLayout.LayoutParams(dp(92), dp(42)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(14), dp(14), dp(32));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout inputCard = card();
        page.addView(inputCard);
        inputCard.addView(sectionTitle("对方发来的内容"));
        inputCard.addView(text("复制聊天内容后打开语聊，或从其他 App 直接分享文字到语聊。", 13, MUTED, false));

        messageInput = input("粘贴一句话或一段聊天内容……", 6);
        messageInput.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(150));
        inputLp.setMargins(0, dp(10), 0, dp(10));
        inputCard.addView(messageInput, inputLp);

        LinearLayout inputActions = row();
        Button paste = compactButton("粘贴剪贴板", Color.rgb(238, 234, 255), PRIMARY);
        paste.setOnClickListener(v -> pasteClipboard(true));
        inputActions.addView(paste, weightedButtonParams());
        addGap(inputActions, 10);
        Button clear = compactButton("清空", Color.rgb(242, 243, 247), PRIMARY_DARK);
        clear.setOnClickListener(v -> {
            messageInput.setText("");
            resultContainer.removeAllViews();
            statusText.setText("");
        });
        inputActions.addView(clear, weightedButtonParams());
        inputCard.addView(inputActions);

        LinearLayout configCard = card();
        LinearLayout.LayoutParams configLp = new LinearLayout.LayoutParams(-1, -2);
        configLp.setMargins(0, dp(12), 0, 0);
        page.addView(configCard, configLp);
        configCard.addView(sectionTitle("回复设定"));

        configCard.addView(label("聊天场景"));
        sceneSpinner = spinner(new String[]{"日常聊天", "暧昧互动", "朋友互怼", "破冰开场", "缓和气氛", "职场沟通"});
        configCard.addView(sceneSpinner);

        configCard.addView(label("回复人设"));
        personaSpinner = spinner(new String[]{"幽默风趣", "高情商温柔", "轻松俏皮", "克制礼貌", "毒舌但不伤人", "真诚直接"});
        configCard.addView(personaSpinner);

        LinearLayout selectRow = row();
        LinearLayout lengthBox = verticalBox();
        lengthBox.addView(label("回复长度"));
        lengthSpinner = spinner(new String[]{"短句", "正常", "一段话"});
        lengthBox.addView(lengthSpinner);
        selectRow.addView(lengthBox, new LinearLayout.LayoutParams(0, -2, 1));
        addGap(selectRow, 10);
        LinearLayout countBox = verticalBox();
        countBox.addView(label("生成数量"));
        countSpinner = spinner(new String[]{"4 条", "6 条", "8 条"});
        countSpinner.setSelection(1);
        countBox.addView(countSpinner);
        selectRow.addView(countBox, new LinearLayout.LayoutParams(0, -2, 1));
        configCard.addView(selectRow);

        configCard.addView(label("补充要求（可选）"));
        customRequirement = input("例如：别太油腻、像熟人聊天、带一点反问……", 2);
        configCard.addView(customRequirement);

        generateButton = compactButton("生成多种回复", PRIMARY, Color.WHITE);
        generateButton.setTextSize(17);
        generateButton.setTypeface(Typeface.DEFAULT_BOLD);
        generateButton.setOnClickListener(v -> generateReplies());
        LinearLayout.LayoutParams generateLp = new LinearLayout.LayoutParams(-1, dp(54));
        generateLp.setMargins(0, dp(14), 0, 0);
        configCard.addView(generateButton, generateLp);

        statusText = text("", 13, MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, dp(12), 0, 0);
        page.addView(statusText, statusLp);

        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(resultContainer, new LinearLayout.LayoutParams(-1, -2));

        TextView privacy = text("提示：API Key 仅在你的手机本地保存，不会写入 GitHub。准备公开发布时，建议改用自有后端中转 API。", 12, MUTED, false);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
        privacyLp.setMargins(dp(8), dp(18), dp(8), 0);
        page.addView(privacy, privacyLp);
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

    private void generateReplies() {
        String source = messageInput.getText().toString().trim();
        if (source.isEmpty()) {
            toast("请先粘贴对方发来的内容");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String endpoint = prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT).trim();
        String apiKey = prefs.getString(KEY_API_KEY, "").trim();
        String model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).trim();
        if (apiKey.isEmpty()) {
            toast("请先填写 API Key");
            showSettingsDialog();
            return;
        }
        if (endpoint.isEmpty() || model.isEmpty()) {
            toast("API 地址和模型名称不能为空");
            showSettingsDialog();
            return;
        }

        String scene = sceneSpinner.getSelectedItem().toString();
        String persona = personaSpinner.getSelectedItem().toString();
        String length = lengthSpinner.getSelectedItem().toString();
        int count = Integer.parseInt(countSpinner.getSelectedItem().toString().substring(0, 1));
        String extra = customRequirement.getText().toString().trim();

        setLoading(true, "正在分析语气并生成回复……");
        resultContainer.removeAllViews();

        executor.execute(() -> {
            try {
                List<String> replies = AiClient.generate(endpoint, apiKey, model, source, scene, persona, length, count, extra);
                runOnUiThread(() -> {
                    setLoading(false, "已生成 " + replies.size() + " 条回复，点一下即可复制");
                    renderReplies(replies);
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> setLoading(false, "生成失败：" + message));
            }
        });
    }

    private void renderReplies(List<String> replies) {
        resultContainer.removeAllViews();
        for (int i = 0; i < replies.size(); i++) {
            addReplyCard(i + 1, replies.get(i));
        }
    }

    private void addReplyCard(int index, String reply) {
        LinearLayout card = card();
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, dp(12), 0, 0);
        resultContainer.addView(card, cardLp);

        TextView tag = text("回复方案 " + index, 13, PRIMARY, true);
        card.addView(tag);
        TextView body = text(reply, 17, PRIMARY_DARK, false);
        body.setLineSpacing(0, 1.18f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.setMargins(0, dp(8), 0, dp(12));
        card.addView(body, bodyLp);

        LinearLayout actions = row();
        Button copy = compactButton("复制", Color.rgb(238, 234, 255), PRIMARY);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("语聊回复", reply));
            toast("已复制，可返回聊天窗口粘贴");
        });
        actions.addView(copy, weightedButtonParams());
        addGap(actions, 8);

        Button share = compactButton("分享", Color.rgb(239, 248, 245), SUCCESS);
        share.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, reply);
            startActivity(Intent.createChooser(shareIntent, "发送回复"));
        });
        actions.addView(share, weightedButtonParams());
        addGap(actions, 8);

        Button continueButton = compactButton("继续优化", Color.rgb(242, 243, 247), PRIMARY_DARK);
        continueButton.setOnClickListener(v -> {
            messageInput.setText(reply);
            messageInput.setSelection(messageInput.length());
            customRequirement.requestFocus();
            toast("已放回输入框，可补充要求后再次生成");
        });
        actions.addView(continueButton, weightedButtonParams());
        card.addView(actions);
    }

    private void showSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout box = verticalBox();
        box.setPadding(dp(18), dp(8), dp(18), dp(4));
        box.addView(text("AI 接口设置", 21, PRIMARY_DARK, true));
        box.addView(text("支持 OpenAI Responses API，也兼容常见的 Chat Completions 接口。", 13, MUTED, false));

        box.addView(label("API 地址"));
        EditText endpoint = input("https://api.openai.com/v1/responses", 1);
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

        TextView warning = text("不要把 API Key 直接写进代码或提交到 GitHub。此设置只保存在当前设备的 App 私有数据中。", 12, Color.rgb(166, 93, 20), false);
        LinearLayout.LayoutParams warningLp = new LinearLayout.LayoutParams(-1, -2);
        warningLp.setMargins(0, dp(10), 0, 0);
        box.addView(warning, warningLp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new AlertDialog.Builder(this)
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    prefs.edit()
                            .putString(KEY_ENDPOINT, endpoint.getText().toString().trim())
                            .putString(KEY_API_KEY, key.getText().toString().trim())
                            .putString(KEY_MODEL, model.getText().toString().trim())
                            .apply();
                    toast("API 设置已保存");
                })
                .show();
    }

    private void setLoading(boolean loading, String message) {
        generateButton.setEnabled(!loading);
        generateButton.setText(loading ? "生成中……" : "生成多种回复");
        generateButton.setAlpha(loading ? 0.65f : 1f);
        statusText.setText(message);
        statusText.setTextColor(loading ? PRIMARY : (message.startsWith("生成失败") ? Color.rgb(210, 65, 65) : SUCCESS));
    }

    private LinearLayout card() {
        LinearLayout card = verticalBox();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), BORDER);
        card.setBackground(bg);
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
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

    private Button compactButton(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rounded(background, background, 14));
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        return new LinearLayout.LayoutParams(0, dp(44), 1);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout verticalBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
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

    private GradientDrawable gradient(int start, int end, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static final class AiClient {
        private static final Pattern PREFIX = Pattern.compile("^(?:回复\\s*\\d+|方案\\s*\\d+|\\d+)[：:、.\\)\\-\\s]*");
        private static final Pattern BULLET = Pattern.compile("^[•·*\\-—]+\\s*");

        static List<String> generate(String endpoint, String apiKey, String model, String source,
                                     String scene, String persona, String length, int count, String extra) throws Exception {
            String normalizedEndpoint = normalizeEndpoint(endpoint);
            boolean responsesApi = normalizedEndpoint.toLowerCase(Locale.ROOT).contains("/responses");
            String system = "你是中文聊天回复助手。你的回复必须自然、像真人聊天、不过度油腻，不编造事实，不进行人身攻击。"
                    + "根据用户给出的场景和人设生成可直接发送的回复。只输出回复本身，不解释思路。";
            String prompt = "对方发来的内容：\\n" + source
                    + "\\n\\n聊天场景：" + scene
                    + "\\n回复人设：" + persona
                    + "\\n回复长度：" + length
                    + "\\n生成数量：" + count + "条"
                    + (extra.isEmpty() ? "" : "\\n补充要求：" + extra)
                    + "\\n\\n请生成风格有差异的" + count + "条中文回复。每条必须独占一行，并严格使用‘回复1：内容’、‘回复2：内容’的格式。"
                    + "不要使用 Markdown，不要添加开场说明，不要重复对方原话。";

            JSONObject request = new JSONObject();
            request.put("model", model);
            if (responsesApi) {
                request.put("instructions", system);
                request.put("input", prompt);
                request.put("max_output_tokens", 1400);
                request.put("store", false);
            } else {
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", system));
                messages.put(new JSONObject().put("role", "user").put("content", prompt));
                request.put("messages", messages);
            }

            String responseBody = postJson(normalizedEndpoint, apiKey, request.toString());
            String generated = responsesApi ? extractResponsesText(responseBody) : extractChatText(responseBody);
            List<String> replies = parseReplies(generated, count);
            if (replies.isEmpty()) throw new IOException("接口返回了空内容");
            return replies;
        }

        private static String normalizeEndpoint(String endpoint) {
            String value = endpoint.trim();
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            if (value.endsWith("/v1")) return value + "/responses";
            return value;
        }

        private static String postJson(String endpoint, String apiKey, String json) throws IOException, JSONException {
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(90000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Accept", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            connection.disconnect();
            if (code < 200 || code >= 300) {
                String error = extractError(body);
                throw new IOException("HTTP " + code + (error.isEmpty() ? "" : "：" + error));
            }
            return body;
        }

        private static String readAll(InputStream input) throws IOException {
            if (input == null) return "";
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) builder.append(line).append('\n');
            }
            return builder.toString();
        }

        private static String extractError(String body) {
            try {
                JSONObject root = new JSONObject(body);
                JSONObject error = root.optJSONObject("error");
                if (error != null) return error.optString("message", body);
            } catch (Exception ignored) {
            }
            return body.length() > 300 ? body.substring(0, 300) : body;
        }

        private static String extractResponsesText(String body) throws JSONException {
            JSONObject root = new JSONObject(body);
            String direct = root.optString("output_text", "");
            if (!direct.isEmpty()) return direct;
            StringBuilder text = new StringBuilder();
            JSONArray output = root.optJSONArray("output");
            if (output != null) {
                for (int i = 0; i < output.length(); i++) {
                    JSONObject item = output.optJSONObject(i);
                    if (item == null) continue;
                    JSONArray content = item.optJSONArray("content");
                    if (content == null) continue;
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject part = content.optJSONObject(j);
                        if (part == null) continue;
                        String value = part.optString("text", "");
                        if (!value.isEmpty()) text.append(value).append('\n');
                    }
                }
            }
            return text.toString().trim();
        }

        private static String extractChatText(String body) throws JSONException {
            JSONObject root = new JSONObject(body);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return "";
            JSONObject message = choices.optJSONObject(0).optJSONObject("message");
            if (message == null) return "";
            Object content = message.opt("content");
            if (content instanceof String) return (String) content;
            if (content instanceof JSONArray) {
                StringBuilder text = new StringBuilder();
                JSONArray parts = (JSONArray) content;
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part != null) text.append(part.optString("text", "")).append('\n');
                }
                return text.toString().trim();
            }
            return "";
        }

        private static List<String> parseReplies(String raw, int expected) {
            List<String> replies = new ArrayList<>();
            if (raw == null) return replies;
            String cleaned = raw.replace("```json", "").replace("```", "").trim();

            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                try {
                    JSONArray array = new JSONArray(cleaned);
                    for (int i = 0; i < array.length() && replies.size() < expected; i++) {
                        String value = array.optString(i, "").trim();
                        if (!value.isEmpty()) replies.add(value);
                    }
                    if (!replies.isEmpty()) return replies;
                } catch (Exception ignored) {
                }
            }

            String[] lines = cleaned.split("\\r?\\n");
            for (String line : lines) {
                String value = line.trim();
                if (value.isEmpty()) continue;
                value = BULLET.matcher(value).replaceFirst("");
                value = PREFIX.matcher(value).replaceFirst("").trim();
                if (value.startsWith("**") && value.endsWith("**") && value.length() > 4) {
                    value = value.substring(2, value.length() - 2).trim();
                }
                if (!value.isEmpty() && !value.equals("回复") && !value.startsWith("以下是")) {
                    replies.add(value);
                    if (replies.size() >= expected) break;
                }
            }

            if (replies.isEmpty() && !cleaned.isEmpty()) replies.add(cleaned);
            return replies;
        }
    }
}
