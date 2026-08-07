package com.liyuhui.smartledger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** OpenAI-compatible AI classifier with strict validation and local-rule fallback support. */
public final class AiLedgerClient {
    public static final String PREFS = "ai_ledger_settings";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
    public static final String KEY_ENDPOINT = "endpoint";
    public static final String KEY_MODEL = "model";
    public static final String KEY_MIN_CONFIDENCE = "min_confidence";

    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses";
    public static final String DEFAULT_MODEL = "gpt-5-mini";
    public static final float DEFAULT_MIN_CONFIDENCE = 0.62f;

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_INPUT_CHARS = 6000;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AiLedgerClient() { }

    public interface Callback {
        void onSuccess(List<MainActivity.Entry> entries, String modelText);
        void onFailure(String message);
    }

    public static final class Config {
        public boolean enabled;
        public boolean notificationEnabled;
        public String endpoint;
        public String model;
        public float minConfidence;
        public boolean hasApiKey;
    }

    public static Config loadConfig(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Config c = new Config();
        c.enabled = p.getBoolean(KEY_ENABLED, true);
        c.notificationEnabled = p.getBoolean(KEY_NOTIFICATION_ENABLED, true);
        c.endpoint = p.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT);
        c.model = p.getString(KEY_MODEL, DEFAULT_MODEL);
        c.minConfidence = p.getFloat(KEY_MIN_CONFIDENCE, DEFAULT_MIN_CONFIDENCE);
        c.hasApiKey = SecureApiKeyStore.hasKey(context);
        return c;
    }

    public static void saveConfig(Context context, boolean enabled, boolean notificationEnabled,
                                  String endpoint, String model, float minConfidence,
                                  String newApiKey) throws Exception {
        endpoint = endpoint == null ? "" : endpoint.trim();
        model = model == null ? "" : model.trim();
        if (endpoint.isEmpty()) endpoint = DEFAULT_ENDPOINT;
        if (model.isEmpty()) model = DEFAULT_MODEL;
        minConfidence = Math.max(0.1f, Math.min(1f, minConfidence));
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_NOTIFICATION_ENABLED, notificationEnabled)
                .putString(KEY_ENDPOINT, endpoint)
                .putString(KEY_MODEL, model)
                .putFloat(KEY_MIN_CONFIDENCE, minConfidence)
                .apply();
        if (newApiKey != null && !newApiKey.trim().isEmpty()) SecureApiKeyStore.save(context, newApiKey);
    }

    public static void clearApiKey(Context context) {
        SecureApiKeyStore.clear(context);
    }

    public static boolean canUseAi(Context context) {
        Config c = loadConfig(context);
        return c.enabled && c.hasApiKey && c.endpoint != null && !c.endpoint.trim().isEmpty()
                && c.model != null && !c.model.trim().isEmpty();
    }

    public static void parseAsync(Context context, String raw, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                Config config = loadConfig(app);
                if (!config.enabled) throw new IllegalStateException("AI 功能未开启");
                String apiKey = SecureApiKeyStore.load(app);
                if (apiKey.isEmpty()) throw new IllegalStateException("尚未填写 API Key");
                if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("账单文字不能为空");
                String safeRaw = raw.trim();
                if (safeRaw.length() > MAX_INPUT_CHARS) safeRaw = safeRaw.substring(0, MAX_INPUT_CHARS);
                String modelText = request(config, apiKey, safeRaw);
                List<MainActivity.Entry> entries = parseModelJson(modelText, config.minConfidence);
                MAIN.post(() -> callback.onSuccess(entries, modelText));
            } catch (Exception e) {
                String message = readableError(e);
                MAIN.post(() -> callback.onFailure(message));
            }
        });
    }

    private static String request(Config config, String apiKey, String raw) throws Exception {
        URL url = new URL(config.endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);

        JSONObject body = buildRequestBody(config, raw);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(bytes);
        }

        int code = connection.getResponseCode();
        String response = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("接口返回 HTTP " + code + "：" + shorten(response, 360));
        }
        if (response == null || response.trim().isEmpty()) throw new IllegalStateException("接口返回内容为空");
        return extractModelText(new JSONObject(response), config.endpoint);
    }

    private static JSONObject buildRequestBody(Config config, String raw) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", config.model);
        String instruction = buildPrompt(raw);
        if (config.endpoint.toLowerCase(Locale.ROOT).contains("/responses")) {
            body.put("input", instruction);
        } else {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemInstruction()));
            messages.put(new JSONObject().put("role", "user").put("content", userInstruction(raw)));
            body.put("messages", messages);
        }
        return body;
    }

    private static String buildPrompt(String raw) {
        return systemInstruction() + "\n\n" + userInstruction(raw);
    }

    private static String systemInstruction() {
        return "你是个人记账账单识别器。只输出严格 JSON，不要 Markdown、解释或代码块。"
                + "判断文本是否包含真实已发生或明确待确认的资金交易，排除网速、验证码、广告、余额展示、物流和普通聊天。"
                + "输出格式必须是：{\"entries\":[{\"is_bill\":true,\"amount\":18.5,\"type\":\"支出\","
                + "\"category\":\"餐饮\",\"account\":\"微信\",\"note\":\"午餐\","
                + "\"date\":\"2026-08-07\",\"confidence\":0.95,\"reason\":\"支付成功\"}]}。"
                + "没有账单时输出 {\"entries\":[]}。"
                + "type 只能是收入或支出；category 只能是餐饮、交通、购物、住房、学习、医疗、娱乐、通讯、人情、工资、退款、其他；"
                + "account 只能是微信、支付宝、银行卡、信用卡、现金、其他。金额必须是正数。"
                + "转账和红包需结合上下文判断收入或支出，不要仅凭出现‘收款’二字就判为收入。"
                + "同一笔交易的标题、正文、时间、订单号不要拆成多笔。";
    }

    private static String userInstruction(String raw) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        return "今天日期：" + today + "。请识别以下账单文本：\n" + raw;
    }

    private static String extractModelText(JSONObject response, String endpoint) throws Exception {
        String direct = response.optString("output_text", "");
        if (!direct.isEmpty()) return direct;

        JSONArray output = response.optJSONArray("output");
        if (output != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part == null) continue;
                    String text = part.optString("text", "");
                    if (!text.isEmpty()) sb.append(text);
                }
            }
            if (sb.length() > 0) return sb.toString();
        }

        JSONArray choices = response.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.optJSONObject(0);
            if (choice != null) {
                JSONObject message = choice.optJSONObject("message");
                if (message != null) {
                    Object content = message.opt("content");
                    if (content instanceof String) return (String) content;
                    if (content instanceof JSONArray) {
                        StringBuilder sb = new StringBuilder();
                        JSONArray parts = (JSONArray) content;
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.optJSONObject(i);
                            if (part != null) sb.append(part.optString("text", ""));
                        }
                        if (sb.length() > 0) return sb.toString();
                    }
                }
                String text = choice.optString("text", "");
                if (!text.isEmpty()) return text;
            }
        }
        throw new IllegalStateException("无法从接口响应中读取模型文本，请检查接口地址是否兼容 Responses 或 Chat Completions：" + endpoint);
    }

    private static List<MainActivity.Entry> parseModelJson(String text, float minConfidence) throws Exception {
        String json = cleanJson(text);
        JSONArray array;
        if (json.startsWith("[")) {
            array = new JSONArray(json);
        } else {
            JSONObject root = new JSONObject(json);
            array = root.optJSONArray("entries");
            if (array == null) array = new JSONArray();
        }

        List<MainActivity.Entry> entries = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null || !o.optBoolean("is_bill", true)) continue;
            double confidence = number(o.opt("confidence"), 1d);
            if (confidence < minConfidence) continue;
            double amount = Math.abs(number(o.opt("amount"), 0d));
            if (amount <= 0 || amount >= 100000000d) continue;

            MainActivity.Entry e = new MainActivity.Entry();
            e.amount = amount;
            e.type = "收入".equals(o.optString("type")) ? "收入" : "支出";
            e.category = normalizeCategory(o.optString("category", "其他"));
            e.account = normalizeAccount(o.optString("account", "其他"));
            e.note = shorten(o.optString("note", "AI识别账单").trim(), 100);
            if (e.note.isEmpty()) e.note = "AI识别账单";
            e.time = parseDate(o.optString("date", ""));
            e.source = "AI智能解析";
            entries.add(e);
        }
        return entries;
    }

    private static String cleanJson(String text) {
        if (text == null) return "{}";
        String s = text.trim();
        s = s.replace("```json", "").replace("```JSON", "").replace("```", "").trim();
        int objectStart = s.indexOf('{');
        int arrayStart = s.indexOf('[');
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            int end = s.lastIndexOf(']');
            if (end > arrayStart) return s.substring(arrayStart, end + 1);
        }
        if (objectStart >= 0) {
            int end = s.lastIndexOf('}');
            if (end > objectStart) return s.substring(objectStart, end + 1);
        }
        return s;
    }

    private static String normalizeCategory(String value) {
        for (String c : MainActivity.CATEGORIES) if (c.equals(value)) return c;
        return "其他";
    }

    private static String normalizeAccount(String value) {
        for (String a : MainActivity.ACCOUNTS) if (a.equals(value)) return a;
        return "其他";
    }

    private static long parseDate(String value) {
        if (value != null && !value.trim().isEmpty()) {
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
                f.setLenient(false);
                Date d = f.parse(value.trim());
                if (d != null) return d.getTime();
            } catch (Exception ignored) { }
        }
        return System.currentTimeMillis();
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String readableError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
        if (message.contains("Unable to resolve host")) return "无法连接接口，请检查网络或接口地址";
        if (message.contains("timeout") || message.contains("timed out")) return "接口请求超时，请稍后重试";
        return message;
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }
}
