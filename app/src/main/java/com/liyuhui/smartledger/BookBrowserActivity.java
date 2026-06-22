package com.liyuhui.smartledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BookBrowserActivity extends Activity {
    private static final int BG = Color.rgb(244, 247, 251);
    private static final int PRIMARY = Color.rgb(91, 95, 239);
    private static final int PRIMARY_DARK = Color.rgb(34, 36, 77);
    private static final int ACCENT = Color.rgb(0, 194, 168);
    private static final int DANGER = Color.rgb(245, 91, 91);
    private static final String PREFS = "book_browser_settings";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_BOOK_TITLE = "book_title";
    private static final String KEY_LAST_URL = "last_url";
    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";
    private static final String DEFAULT_URL = "https://z-library.mn/?ts=0546";
    private static final int PAGE_TEXT_LIMIT = 12000;

    private final List<BrowserTab> tabs = new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout tabStrip;
    private FrameLayout webHolder;
    private TextView statusText;
    private TextView resultText;
    private EditText bookInput;
    private EditText urlInput;
    private int currentIndex = -1;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        WebView.setWebContentsDebuggingEnabled(Build.VERSION.SDK_INT >= 19);
        buildUi();
        addTab(prefs.getString(KEY_LAST_URL, DEFAULT_URL));
    }

    @Override protected void onDestroy() {
        for (BrowserTab tab : tabs) tab.webView.destroy();
        tabs.clear();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        BrowserTab tab = currentTab();
        if (tab != null && tab.webView.canGoBack()) {
            tab.webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(12));
        header.setBackground(gradient(PRIMARY, Color.rgb(109, 82, 232)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        header.addView(text("AI搜书浏览器", 24, Color.WHITE, true));
        header.addView(text("多窗口打开网页，读取当前页文字，用AI判断是否匹配书名", 13, Color.argb(230, 255, 255, 255), false));

        bookInput = input("要判断的书名，例如：机械设计基础", false);
        bookInput.setSingleLine(true);
        bookInput.setText(prefs.getString(KEY_BOOK_TITLE, ""));
        header.addView(bookInput, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout urlRow = new LinearLayout(this);
        urlRow.setOrientation(LinearLayout.HORIZONTAL);
        urlRow.setGravity(Gravity.CENTER_VERTICAL);
        urlRow.setPadding(0, dp(8), 0, 0);
        header.addView(urlRow);
        urlInput = input("网址", false);
        urlInput.setSingleLine(true);
        urlInput.setText(prefs.getString(KEY_LAST_URL, DEFAULT_URL));
        urlRow.addView(urlInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(urlRow, 8);
        Button open = pill("新窗口", Color.argb(235, 255, 255, 255), PRIMARY);
        open.setOnClickListener(v -> addTab(urlInput.getText().toString().trim()));
        urlRow.addView(open, new LinearLayout.LayoutParams(dp(94), dp(46)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, 0);
        header.addView(actions);
        Button analyze = pill("AI判断当前页", Color.argb(235, 255, 255, 255), PRIMARY);
        analyze.setOnClickListener(v -> analyzeCurrentPage());
        actions.addView(analyze, new LinearLayout.LayoutParams(0, dp(44), 1));
        addGap(actions, 8);
        Button refresh = pill("刷新", Color.argb(70, 255, 255, 255), Color.WHITE);
        refresh.setOnClickListener(v -> { BrowserTab tab = currentTab(); if (tab != null) tab.webView.reload(); });
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(44), 1));
        addGap(actions, 8);
        Button settings = pill("API设置", Color.argb(70, 255, 255, 255), Color.WHITE);
        settings.setOnClickListener(v -> showApiDialog());
        actions.addView(settings, new LinearLayout.LayoutParams(0, dp(44), 1));

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(10), dp(8), dp(10), dp(6));
        tabScroll.addView(tabStrip);
        root.addView(tabScroll, new LinearLayout.LayoutParams(-1, -2));

        statusText = text("准备打开网页", 12, Color.rgb(90, 96, 120), false);
        statusText.setPadding(dp(14), 0, dp(14), dp(5));
        root.addView(statusText);

        webHolder = new FrameLayout(this);
        root.addView(webHolder, new LinearLayout.LayoutParams(-1, 0, 1));

        resultText = text("AI判断结果会显示在这里。", 13, PRIMARY_DARK, false);
        resultText.setPadding(dp(14), dp(10), dp(14), dp(10));
        resultText.setBackgroundColor(Color.WHITE);
        root.addView(resultText, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addTab(String rawUrl) {
        String url = normalizeUrl(rawUrl);
        if (url.length() == 0) {
            toast("请输入网址");
            return;
        }
        WebView webView = createWebView();
        BrowserTab tab = new BrowserTab(webView, url);
        tabs.add(tab);
        switchTo(tabs.size() - 1);
        webView.loadUrl(url);
        prefs.edit().putString(KEY_LAST_URL, url).apply();
    }

    private void addExistingTab(WebView webView, String url) {
        BrowserTab tab = new BrowserTab(webView, url == null ? "about:blank" : url);
        tabs.add(tab);
        switchTo(tabs.size() - 1);
    }

    private WebView createWebView() {
        WebView webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= 21) settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
            @Override public void onPageFinished(WebView view, String url) {
                BrowserTab tab = findTab(view);
                if (tab != null) {
                    tab.url = url == null ? tab.url : url;
                    prefs.edit().putString(KEY_LAST_URL, tab.url).apply();
                    updateTabs();
                }
                statusText.setText("加载完成：" + shorten(url, 70));
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String title) {
                BrowserTab tab = findTab(view);
                if (tab != null && title != null && title.trim().length() > 0) {
                    tab.title = title.trim();
                    updateTabs();
                }
            }
            @Override public void onProgressChanged(WebView view, int newProgress) {
                statusText.setText("加载进度 " + newProgress + "%");
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView child = createWebView();
                addExistingTab(child, "about:blank");
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(child);
                resultMsg.sendToTarget();
                return true;
            }
        });
        return webView;
    }

    private void switchTo(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentIndex = index;
        webHolder.removeAllViews();
        BrowserTab tab = tabs.get(index);
        webHolder.addView(tab.webView);
        urlInput.setText(tab.url);
        resultText.setText(tab.lastResult.length() > 0 ? tab.lastResult : "AI判断结果会显示在这里。");
        updateTabs();
    }

    private void closeCurrentTab() {
        if (tabs.isEmpty() || currentIndex < 0) return;
        BrowserTab removed = tabs.remove(currentIndex);
        webHolder.removeView(removed.webView);
        removed.webView.destroy();
        if (tabs.isEmpty()) {
            currentIndex = -1;
            addTab(DEFAULT_URL);
            return;
        }
        switchTo(Math.min(currentIndex, tabs.size() - 1));
    }

    private void updateTabs() {
        tabStrip.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10), 0, dp(4), 0);
            item.setBackground(round(i == currentIndex ? PRIMARY : Color.WHITE, dp(18)));
            final int index = i;
            TextView title = text((i + 1) + " " + shorten(tab.displayTitle(), 14), 13, i == currentIndex ? Color.WHITE : PRIMARY_DARK, true);
            title.setOnClickListener(v -> switchTo(index));
            item.addView(title, new LinearLayout.LayoutParams(dp(116), dp(38)));
            TextView close = text("×", 18, i == currentIndex ? Color.WHITE : DANGER, true);
            close.setGravity(Gravity.CENTER);
            close.setOnClickListener(v -> { switchTo(index); closeCurrentTab(); });
            item.addView(close, new LinearLayout.LayoutParams(dp(32), dp(38)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
            lp.setMargins(0, 0, dp(8), 0);
            tabStrip.addView(item, lp);
        }
    }

    private void analyzeCurrentPage() {
        BrowserTab tab = currentTab();
        if (tab == null) {
            toast("没有打开的网页窗口");
            return;
        }
        String bookTitle = bookInput.getText().toString().trim();
        if (bookTitle.length() == 0) {
            toast("先输入要判断的书名");
            return;
        }
        prefs.edit().putString(KEY_BOOK_TITLE, bookTitle).apply();
        if (!isNetworkAvailable()) {
            showLocalResult(tab, bookTitle, "当前无网络，先用本地关键词做临时判断。");
            return;
        }
        String apiKey = prefs.getString(KEY_API_KEY, "");
        if (apiKey.trim().length() == 0) {
            showApiDialog();
            toast("请先填写 API Key");
            return;
        }
        statusText.setText("正在读取当前网页文字...");
        tab.webView.evaluateJavascript("(function(){return document.body ? document.body.innerText : document.documentElement.innerText;})()", value -> {
            String pageText = decodeJsString(value);
            if (pageText.trim().length() < 8) {
                showLocalResult(tab, bookTitle, "没有读到足够的网页文字，可能网页未加载完成或内容在图片中。");
                return;
            }
            statusText.setText("已读取 " + pageText.length() + " 字，正在请求 AI 判断...");
            callAiJudge(tab, bookTitle, tab.url, pageText);
        });
    }

    private void callAiJudge(BrowserTab tab, String bookTitle, String pageUrl, String pageText) {
        String apiUrl = prefs.getString(KEY_API_URL, DEFAULT_API_URL).trim();
        String apiKey = prefs.getString(KEY_API_KEY, "").trim();
        String model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).trim();
        if (model.length() == 0) model = DEFAULT_MODEL;
        final String finalModel = model;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", finalModel);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", "你是图书搜索结果判断器。只判断网页内容是否与用户给定书名为同一本书、同一版本或明显相关结果。必须只返回JSON，不要Markdown。JSON格式：{\"is_match\":true/false,\"confidence\":0-100,\"matched_title\":\"\",\"reason\":\"\"}"));
                String user = "目标书名：" + bookTitle + "\n当前网址：" + pageUrl + "\n网页文本：\n" + limit(pageText, PAGE_TEXT_LIMIT);
                messages.put(new JSONObject().put("role", "user").put("content", user));
                body.put("messages", messages);
                body.put("temperature", 0);

                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) { os.write(out); }
                int code = conn.getResponseCode();
                String raw = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
                if (code < 200 || code >= 300) throw new IllegalStateException("API错误 " + code + "：" + raw);
                AiResult result = parseAiResponse(raw);
                runOnUiThread(() -> showAiResult(tab, bookTitle, result));
            } catch (Exception e) {
                runOnUiThread(() -> showLocalResult(tab, bookTitle, "AI请求失败：" + e.getMessage() + "\n已退回本地关键词判断。"));
            }
        }).start();
    }

    private AiResult parseAiResponse(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        String content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
        JSONObject json = extractJsonObject(content);
        AiResult result = new AiResult();
        result.match = json.optBoolean("is_match", false);
        result.confidence = json.optInt("confidence", result.match ? 80 : 20);
        result.matchedTitle = json.optString("matched_title", "");
        result.reason = json.optString("reason", content);
        return result;
    }

    private JSONObject extractJsonObject(String content) throws Exception {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) return new JSONObject(content.substring(start, end + 1));
        return new JSONObject(content);
    }

    private void showAiResult(BrowserTab tab, String bookTitle, AiResult result) {
        String text = (result.match ? "符合" : "不符合")
                + "  置信度：" + result.confidence + "%\n"
                + "目标书名：" + bookTitle + "\n"
                + (result.matchedTitle.length() > 0 ? "网页命中：" + result.matchedTitle + "\n" : "")
                + "原因：" + result.reason;
        tab.lastResult = text;
        if (tab == currentTab()) resultText.setText(text);
        statusText.setText("AI判断完成");
    }

    private void showLocalResult(BrowserTab tab, String bookTitle, String prefix) {
        tab.webView.evaluateJavascript("(function(){return document.body ? document.body.innerText : '';})()", value -> {
            String pageText = decodeJsString(value);
            boolean contains = normalizeForCompare(pageText).contains(normalizeForCompare(bookTitle));
            String text = prefix + "\n本地判断：" + (contains ? "可能符合" : "暂未发现完整书名")
                    + "\n目标书名：" + bookTitle;
            tab.lastResult = text;
            if (tab == currentTab()) resultText.setText(text);
            statusText.setText("本地判断完成");
        });
    }

    private void showApiDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), 0);
        EditText apiUrl = input("API URL", false);
        apiUrl.setSingleLine(true);
        apiUrl.setText(prefs.getString(KEY_API_URL, DEFAULT_API_URL));
        EditText model = input("模型名", false);
        model.setSingleLine(true);
        model.setText(prefs.getString(KEY_MODEL, DEFAULT_MODEL));
        EditText apiKey = input("API Key", false);
        apiKey.setSingleLine(true);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKey.setText(prefs.getString(KEY_API_KEY, ""));
        box.addView(label("OpenAI-compatible Chat Completions URL"));
        box.addView(apiUrl, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(label("模型"));
        box.addView(model, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(label("API Key（只保存在本机）"));
        box.addView(apiKey, new LinearLayout.LayoutParams(-1, dp(48)));
        new AlertDialog.Builder(this)
                .setTitle("AI API 设置")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.edit()
                            .putString(KEY_API_URL, apiUrl.getText().toString().trim())
                            .putString(KEY_MODEL, model.getText().toString().trim())
                            .putString(KEY_API_KEY, apiKey.getText().toString().trim())
                            .apply();
                    toast("API设置已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private BrowserTab currentTab() { return currentIndex >= 0 && currentIndex < tabs.size() ? tabs.get(currentIndex) : null; }
    private BrowserTab findTab(WebView webView) { for (BrowserTab tab : tabs) if (tab.webView == webView) return tab; return null; }

    private String normalizeUrl(String raw) {
        if (raw == null) return "";
        String url = raw.trim();
        if (url.length() == 0) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://") && !url.startsWith("about:")) url = "https://" + url;
        return url;
    }

    private String decodeJsString(String value) {
        if (value == null || "null".equals(value)) return "";
        try { return new JSONArray("[" + value + "]").getString(0); } catch (Exception e) { return value; }
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) { return true; }
    }

    private String normalizeForCompare(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。、《》：；！￥（）【】]+", "");
    }

    private String limit(String value, int max) { return value == null || value.length() <= max ? (value == null ? "" : value) : value.substring(0, max); }
    private String shorten(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(0, max) + "..."; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private TextView label(String s) { TextView t = text(s, 13, Color.rgb(80, 84, 105), true); t.setPadding(0, dp(10), 0, dp(5)); return t; }
    private EditText input(String hint, boolean number) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(14); e.setSingleLine(false); e.setPadding(dp(12), 0, dp(12), 0); e.setTextColor(PRIMARY_DARK); e.setHintTextColor(Color.rgb(135, 140, 160)); e.setBackground(round(Color.argb(235, 255, 255, 255), dp(18))); e.setInputType(number ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL) : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); return e; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s == null ? "" : s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private Button pill(String value, int bg, int fg) { Button b = new Button(this); b.setAllCaps(false); b.setText(value); b.setTextSize(13); b.setTextColor(fg); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round(bg, dp(22))); b.setElevation(dp(4)); b.setOnTouchListener((v, e) -> { if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).start(); else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(90).start(); return false; }); return b; }
    private GradientDrawable round(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); return g; }
    private GradientDrawable gradient(int start, int end) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end}); return g; }
    private void addGap(LinearLayout row, int dp) { TextView gap = new TextView(this); row.addView(gap, new LinearLayout.LayoutParams(dp(dp), 1)); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private static class BrowserTab {
        final WebView webView;
        String url;
        String title = "新窗口";
        String lastResult = "";
        BrowserTab(WebView webView, String url) { this.webView = webView; this.url = url; }
        String displayTitle() { return title == null || title.trim().length() == 0 ? url : title; }
    }

    private static class AiResult {
        boolean match;
        int confidence;
        String matchedTitle = "";
        String reason = "";
    }
}
