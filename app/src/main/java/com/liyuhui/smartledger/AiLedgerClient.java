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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** OpenAI-compatible merchant classifier. Amount, account and transaction time stay local. */
public final class AiLedgerClient {
    public static final String PREFS = "ai_ledger_settings";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
    public static final String KEY_ENDPOINT = "endpoint";
    public static final String KEY_MODEL = "model";
    public static final String KEY_MIN_CONFIDENCE = "min_confidence";

    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses";
    public static final String DEFAULT_MODEL = "gpt-5-mini";
    public static final float DEFAULT_MIN_CONFIDENCE = 0.68f;

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_RAW_CHARS = 1800;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AiLedgerClient() { }

    public interface CategoryCallback {
        void onSuccess(CategoryResult result);
        void onFailure(String message);
    }

    public static final class CategoryResult {
        public final String category;
        public final String merchant;
        public final double confidence;
        public final String reason;

        CategoryResult(String category, String merchant, double confidence, String reason) {
            this.category = category;
            this.merchant = merchant;
            this.confidence = confidence;
            this.reason = reason;
        }
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
        SharedPreferences p = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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
        String safeEndpoint = endpoint == null ? "" : endpoint.trim();
        String safeModel = model == null ? "" : model.trim();
        if (safeEndpoint.isEmpty()) safeEndpoint = DEFAULT_ENDPOINT;
        if (safeModel.isEmpty()) safeModel = DEFAULT_MODEL;
        minConfidence = Math.max(0.1f, Math.min(1f, minConfidence));
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putBoolean(KEY_NOTIFICATION_ENABLED, notificationEnabled)
                .putString(KEY_ENDPOINT, safeEndpoint)
                .putString(KEY_MODEL, safeModel)
                .putFloat(KEY_MIN_CONFIDENCE, minConfidence)
                .apply();
        if (newApiKey != null && !newApiKey.trim().isEmpty()) {
            SecureApiKeyStore.save(context, newApiKey.trim());
        }
    }

    public static void clearApiKey(Context context) {
        SecureApiKeyStore.clear(context);
    }

    public static boolean canUseAi(Context context) {
        Config c = loadConfig(context);
        return c.enabled && c.hasApiKey && c.endpoint != null && !c.endpoint.trim().isEmpty()
                && c.model != null && !c.model.trim().isEmpty();
    }

    public static void classifyAsync(Context context, String merchant, String raw,
                                     String transactionType, CategoryCallback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                Config config = loadConfig(app);
                if (!config.enabled) throw new IllegalStateException("AI 分类未开启");
                String apiKey = SecureApiKeyStore.load(app);
                if (apiKey.isEmpty()) throw new IllegalStateException("尚未填写 API Key");
                String safeMerchant = merchant == null ? "" : merchant.trim();
                String safeRaw = raw == null ? "" : raw.trim();
                if (safeRaw.length() > MAX_RAW_CHARS) safeRaw = safeRaw.substring(0, MAX_RAW_CHARS);
                if (safeMerchant.isEmpty()) safeMerchant = MerchantCategoryStore.extractMerchant(safeRaw);
                if (safeMerchant.isEmpty() && safeRaw.isEmpty()) {
                    throw new IllegalArgumentException("没有可用于分类的商家信息");
                }
                String modelText = request(config, apiKey,
                        buildPrompt(safeMerchant, safeRaw, transactionType));
                CategoryResult result = parseCategory(modelText, config.minConfidence);
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                String message = readableError(e);
                MAIN.post(() -> callback.onFailure(message));
            }
        });
    }

    private static String request(Config config, String apiKey, String prompt) throws Exception {
        try {
            return performRequest(config, apiKey, prompt, true);
        } catch (HttpStatusException e) {
            if (e.code == 400 || e.code == 422) {
                return performRequest(config, apiKey, prompt, false);
            }
            throw e;
        }
    }

    private static String performRequest(Config config, String apiKey, String prompt,
                                         boolean structuredOutput) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(config.endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);

        JSONObject body = buildRequestBody(config, prompt, structuredOutput);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }

        int code = connection.getResponseCode();
        String response = readAll(code >= 200 && code < 300
                ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new HttpStatusException(code,
                    "接口返回 HTTP " + code + "：" + shorten(response, 360));
        }
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalStateException("接口返回内容为空");
        }
        return extractModelText(new JSONObject(response), config.endpoint);
    }

    private static JSONObject buildRequestBody(Config config, String prompt,
                                               boolean structuredOutput) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", config.model);
        boolean responses = config.endpoint.toLowerCase(Locale.ROOT).contains("/responses");
        if (responses) {
            body.put("input", prompt);
            if (structuredOutput) {
                body.put("text", new JSONObject().put("format", schemaFormat()));
            }
        } else {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system")
                    .put("content", systemInstruction()));
            messages.put(new JSONObject().put("role", "user")
                    .put("content", prompt));
            body.put("messages", messages);
            if (structuredOutput) {
                body.put("response_format", new JSONObject()
                        .put("type", "json_schema")
                        .put("json_schema", schemaPayload()));
            }
        }
        return body;
    }

    private static JSONObject schemaFormat() throws Exception {
        return new JSONObject()
                .put("type", "json_schema")
                .put("name", "merchant_category")
                .put("description", "Classify a payment merchant into one bookkeeping category")
                .put("strict", true)
                .put("schema", categorySchema());
    }

    private static JSONObject schemaPayload() throws Exception {
        return new JSONObject()
                .put("name", "merchant_category")
                .put("description", "Classify a payment merchant into one bookkeeping category")
                .put("strict", true)
                .put("schema", categorySchema());
    }

    private static JSONObject categorySchema() throws Exception {
        JSONArray categoryValues = new JSONArray();
        for (String category : LedgerCategories.ALL) categoryValues.put(category);
        JSONObject properties = new JSONObject()
                .put("category", new JSONObject().put("type", "string").put("enum", categoryValues))
                .put("merchant", new JSONObject().put("type", "string"))
                .put("confidence", new JSONObject().put("type", "number")
                        .put("minimum", 0).put("maximum", 1))
                .put("reason", new JSONObject().put("type", "string"));
        return new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", new JSONArray()
                        .put("category").put("merchant").put("confidence").put("reason"))
                .put("additionalProperties", false);
    }

    private static String buildPrompt(String merchant, String raw, String transactionType) {
        return systemInstruction() + "\n\n"
                + "收支类型：" + ("收入".equals(transactionType) ? "收入" : "支出") + "\n"
                + "提取到的商家名称：" + (merchant.isEmpty() ? "未明确" : merchant) + "\n"
                + "付款通知原文：\n" + raw;
    }

    private static String systemInstruction() {
        return "你是自动记账中的商家消费分类器。只判断消费品类，不要重新提取金额、账户或日期。"
                + "主要依据商家名称、店铺名称和业务类型分类。"
                + "分类只能从：餐饮、水果、生鲜、日用品、购物、交通、住房、医疗、学习、娱乐、通讯、人情、工资、退款、其他 中选择。"
                + "示例：百果园、鲜丰水果、水果店归水果；饭店、外卖、奶茶店归餐饮；菜市场、肉铺归生鲜；"
                + "便利店和普通超市优先归日用品；淘宝、服饰店、数码店归购物。"
                + "无法从商家信息可靠判断时必须返回其他并降低 confidence。"
                + "只输出 JSON，不要 Markdown 或额外解释。";
    }

    private static CategoryResult parseCategory(String modelText, float minConfidence) throws Exception {
        String json = cleanJson(modelText);
        JSONObject value = new JSONObject(json);
        if (!value.has("confidence")) {
            throw new IllegalStateException("AI 返回结果缺少 confidence，已拒绝自动入账");
        }
        double confidence = number(value.opt("confidence"), -1d);
        if (confidence < 0 || confidence > 1) {
            throw new IllegalStateException("AI 返回的 confidence 无效");
        }
        String category = value.optString("category", "").trim();
        if (!LedgerCategories.isAllowed(category)) {
            throw new IllegalStateException("AI 返回了不支持的消费分类");
        }
        if (confidence < minConfidence) {
            throw new IllegalStateException("AI 分类置信度不足（"
                    + String.format(Locale.CHINA, "%.0f%%", confidence * 100d) + "）");
        }
        String merchant = shorten(value.optString("merchant", "").trim(), 60);
        String reason = shorten(value.optString("reason", "").trim(), 100);
        return new CategoryResult(category, merchant, confidence, reason);
    }

    private static String extractModelText(JSONObject response, String endpoint) throws Exception {
        String direct = response.optString("output_text", "");
        if (!direct.isEmpty()) return direct;

        JSONArray output = response.optJSONArray("output");
        if (output != null) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j);
                    if (part == null) continue;
                    String piece = part.optString("text", "");
                    if (!piece.isEmpty()) text.append(piece);
                }
            }
            if (text.length() > 0) return text.toString();
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
                        StringBuilder text = new StringBuilder();
                        JSONArray parts = (JSONArray) content;
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.optJSONObject(i);
                            if (part != null) text.append(part.optString("text", ""));
                        }
                        if (text.length() > 0) return text.toString();
                    }
                }
                String plain = choice.optString("text", "");
                if (!plain.isEmpty()) return plain;
            }
        }
        throw new IllegalStateException("无法从接口响应中读取分类结果，请检查接口地址：" + endpoint);
    }

    private static String cleanJson(String text) {
        if (text == null) return "{}";
        String value = text.trim().replace("```json", "")
                .replace("```JSON", "").replace("```", "").trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : value;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static String readableError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        if (message.contains("Unable to resolve host")) return "无法连接接口，请检查网络或接口地址";
        if (message.toLowerCase(Locale.ROOT).contains("timeout")) return "接口请求超时，请稍后重试";
        return message;
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    private static final class HttpStatusException extends Exception {
        final int code;
        HttpStatusException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
