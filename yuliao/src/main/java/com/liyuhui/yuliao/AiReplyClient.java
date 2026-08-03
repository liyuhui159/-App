package com.liyuhui.yuliao;

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
import java.util.regex.Pattern;

final class AiReplyClient {
    private static final Pattern PREFIX = Pattern.compile("^(?:回复\\s*\\d+|方案\\s*\\d+|\\d+)[：:、.\\)\\-\\s]*");
    private static final Pattern BULLET = Pattern.compile("^[•·*\\-—]+\\s*");

    private AiReplyClient() {
    }

    static List<String> generate(String endpoint, String apiKey, String model, String source,
                                 String scene, String persona, String length, int count,
                                 String extra) throws Exception {
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        boolean responsesApi = normalizedEndpoint.toLowerCase(Locale.ROOT).contains("/responses");
        String system = "你是中文聊天回复助手。请先分析对方语气、关系距离、情绪和潜台词，再生成可以直接发送的自然回复。"
                + "回复要像真人聊天，避免油腻、说教、机械客套、虚构事实和人身攻击。"
                + "严格服从用户选择的人设与场景，只输出候选回复，不解释分析过程。";
        String prompt = "对方发来的内容：\\n" + source
                + "\\n\\n聊天场景：" + scene
                + "\\n回复人设：" + persona
                + "\\n回复长度：" + length
                + "\\n生成数量：" + count + "条"
                + (extra.isEmpty() ? "" : "\\n补充要求：" + extra)
                + "\\n\\n请生成风格有差异的" + count + "条中文回复。"
                + "每条独占一行，严格使用‘回复1：内容’、‘回复2：内容’格式。"
                + "不要使用 Markdown，不添加开场说明，不重复对方原话。";

        JSONObject request = new JSONObject();
        request.put("model", model);
        if (responsesApi) {
            request.put("instructions", system);
            request.put("input", prompt);
            request.put("max_output_tokens", 1600);
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
        JSONObject first = choices.optJSONObject(0);
        if (first == null) return "";
        JSONObject message = first.optJSONObject("message");
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