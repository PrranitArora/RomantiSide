package com.tinywonder.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Opt-in chat transport. Session credentials and conversation inputs stay in memory. */
final class ChatClient {
    interface Callback { void result(String type, String text); }
    private static final int MAX_BODY_CHARS = 20_000;
    private static final int MAX_BYTES = 65_536;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
        1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
    private final Object lock = new Object();
    private long generation;
    private boolean busy;
    private boolean closed;
    private String sessionToken = "";
    private long sessionExpiresAt;
    private HttpURLConnection activeConnection;

    ChatClient(Context context) {
        this.context = context.getApplicationContext();
        worker.allowCoreThreadTimeOut(true);
    }

    void request(String requestId, String payloadJson, Callback callback) {
        request("chat", requestId, payloadJson, callback);
    }

    void profileRequest(String requestId, String payloadJson, Callback callback) {
        request("profile", requestId, payloadJson, callback);
    }

    void questsRequest(String requestId, String payloadJson, Callback callback) {
        request("quests", requestId, payloadJson, callback);
    }

    private void request(String kind, String requestId, String payloadJson, Callback callback) {
        final long current;
        synchronized (lock) {
            if (closed) return;
            current = generation;
            if (busy) {
                deliver(current, requestId, "chatError", errorBody("A check-in is already being sent. Please wait for its reply."), callback);
                return;
            }
            busy = true;
        }
        try {
            worker.execute(() -> {
                try {
                    if (requestId == null || !requestId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))
                        throw new ChatException("This check-in could not be identified. Please reopen it and try again.");
                    JSONObject payload = "profile".equals(kind) ? sanitizeProfileRequest(payloadJson)
                        : "quests".equals(kind) ? sanitizeQuestsRequest(payloadJson) : sanitizeRequest(payloadJson);
                    String path = "chat".equals(kind) ? "/v1/chat" : "/v1/chat/" + kind;
                    ensureCurrent(current);
                    String token = ensureSession(current);
                    JSONObject response;
                    try {
                        response = call(path, payload, token, current);
                    } catch (UnauthorizedException expired) {
                        // A rejected session has not reached the model. Refresh once; never
                        // retry a timed-out or failed model request, which may already be billed.
                        clearSession(current);
                        response = call(path, payload, ensureSession(current), current);
                    }
                    JSONObject cleaned = "profile".equals(kind) ? sanitizeProfileResponse(response)
                        : "quests".equals(kind) ? sanitizeQuestsResponse(response) : sanitizeResponse(response);
                    deliver(current, requestId, kind, cleaned, callback);
                } catch (CancelledException ignored) {
                    // Closing a check-in, deleting data or destroying the activity invalidates
                    // every pending callback, including callbacks already on the main queue.
                } catch (UnauthorizedException error) {
                    deliver(current, requestId, "chatError", errorBody("The chat session expired. Please try again."), callback);
                } catch (ChatException error) {
                    deliver(current, requestId, "chatError", errorBody(error.getMessage()), callback);
                } catch (java.net.SocketTimeoutException error) {
                    deliver(current, requestId, "chatError", errorBody("The chat took too long to respond. Your message was not retried; you can try again."), callback);
                } catch (IOException error) {
                    deliver(current, requestId, "chatError", errorBody("Could not reach the chat service. Check your connection, or use a private check-in."), callback);
                } catch (JSONException | IllegalArgumentException error) {
                    deliver(current, requestId, "chatError", errorBody("The check-in request or reply could not be read. Please try again."), callback);
                } catch (RuntimeException error) {
                    deliver(current, requestId, "chatError", errorBody("Chat could not complete this check-in. You can use a private check-in instead."), callback);
                } finally {
                    synchronized (lock) { if (current == generation) busy = false; }
                }
            });
        } catch (RejectedExecutionException error) {
            synchronized (lock) { if (current == generation) busy = false; }
            deliver(current, requestId, "chatError", errorBody("Chat is finishing an earlier request. Please wait a moment."), callback);
        }
    }

    void cancel() { cancel(false); }

    void clear() { cancel(true); }

    private void cancel(boolean clearCredentials) {
        HttpURLConnection connection;
        synchronized (lock) {
            generation++;
            busy = false;
            if (clearCredentials) {
                sessionToken = "";
                sessionExpiresAt = 0;
            }
            worker.getQueue().clear();
            connection = activeConnection;
            activeConnection = null;
        }
        if (connection != null) connection.disconnect();
    }

    void close() {
        synchronized (lock) { closed = true; }
        clear();
        worker.shutdownNow();
    }

    private void ensureCurrent(long current) throws CancelledException {
        synchronized (lock) {
            if (closed || generation != current) throw new CancelledException();
        }
    }

    private void clearSession(long current) throws CancelledException {
        synchronized (lock) {
            ensureCurrent(current);
            sessionToken = "";
            sessionExpiresAt = 0;
        }
    }

    private String ensureSession(long current) throws IOException, JSONException, ChatException, CancelledException, UnauthorizedException {
        synchronized (lock) {
            ensureCurrent(current);
            if (!sessionToken.isEmpty() && sessionExpiresAt > System.currentTimeMillis() + 30_000) return sessionToken;
        }
        JSONObject created = call("/v1/chat/session", new JSONObject(), "", current);
        Object rawToken = created.opt("token"), rawExpiry = created.opt("expiresAt");
        if (!(rawToken instanceof String) || !((String) rawToken).matches("[A-Za-z0-9_-]{43}") || !(rawExpiry instanceof String))
            throw new ChatException("The chat service could not create a session. Please try again.");
        long expiresAt;
        try { expiresAt = Instant.parse((String) rawExpiry).toEpochMilli(); }
        catch (DateTimeParseException | ArithmeticException error) {
            throw new ChatException("The chat service returned an invalid session. Please try again.");
        }
        if (expiresAt <= System.currentTimeMillis()) throw new ChatException("The chat session has expired. Check your device clock and try again.");
        synchronized (lock) {
            ensureCurrent(current);
            sessionToken = (String) rawToken;
            sessionExpiresAt = expiresAt;
            return sessionToken;
        }
    }

    private static JSONObject sanitizeRequest(String text) throws JSONException, ChatException {
        if (text == null || text.length() > MAX_BODY_CHARS || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new ChatException("This check-in is too long. Please start a shorter conversation.");
        JSONObject supplied = new JSONObject(text);
        JSONArray messages = supplied.optJSONArray("messages");
        if (messages == null || messages.length() < 1 || messages.length() > 11 || messages.length() % 2 != 1)
            throw new ChatException("Keep check-ins to six short messages from you. Please start a new conversation.");
        JSONArray cleaned = new JSONArray();
        int total = 0;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            String role = i % 2 == 0 ? "user" : "assistant";
            if (message == null || !role.equals(message.opt("role")) || !(message.opt("content") instanceof String))
                throw new ChatException("This conversation could not be read. Please start a new check-in.");
            String content = message.getString("content").trim();
            total += content.length();
            if (content.isEmpty() || content.length() > 2000 || total > 12000)
                throw new ChatException("Use up to 2,000 characters per message and keep the conversation brief.");
            cleaned.put(new JSONObject().put("role", role).put("content", content));
        }
        // Only the user-approved conversation and ratings cross the boundary. No
        // local journal history, photos, Circle profile, device ID or contacts do.
        JSONObject result = new JSONObject().put("messages", cleaned)
            .put("mood", rating(supplied, "mood", 5)).put("energy", rating(supplied, "energy", 3));
        if (supplied.has("profile") && !supplied.isNull("profile")) result.put("profile", sanitizeProfile(supplied.optJSONObject("profile")));
        return result;
    }

    private static Object rating(JSONObject source, String key, int maximum) throws ChatException {
        Object value = source.opt(key);
        if (value == null || value == JSONObject.NULL) return JSONObject.NULL;
        if (!(value instanceof Number)) throw new ChatException("Choose a valid mood or energy rating, or leave it unset.");
        double number = ((Number) value).doubleValue();
        if (number < 1 || number > maximum || number != Math.floor(number))
            throw new ChatException("Choose a valid mood or energy rating, or leave it unset.");
        return (int) number;
    }

    private static JSONObject personalizationBody(String text) throws JSONException, ChatException {
        if (text == null || text.length() > 50_000 || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new ChatException("This preference request is too long. Please use a shorter reflection or less quest history.");
        return new JSONObject(text);
    }

    private static String plainText(JSONObject source, String key, int maximum, boolean allowEmpty) throws ChatException {
        if (source == null || !(source.opt(key) instanceof String)) throw new ChatException("The preference or activity details could not be read.");
        String text = java.text.Normalizer.normalize((String) source.opt(key), java.text.Normalizer.Form.NFKC).trim();
        if ((!allowEmpty && text.isEmpty()) || text.codePointCount(0, text.length()) > maximum)
            throw new ChatException("The preference or activity details exceed the supported length.");
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            if ((letter < 32 && letter != '\t' && letter != '\n' && letter != '\r')
                || (letter >= 127 && letter <= 159) || (letter >= 0x202A && letter <= 0x202E)
                || (letter >= 0x2066 && letter <= 0x2069))
                throw new ChatException("The preference or activity details contain unsupported characters.");
        }
        return text;
    }

    private static JSONArray preferenceList(JSONObject source, String key) throws JSONException, ChatException {
        JSONArray supplied = source.optJSONArray(key);
        if (supplied == null || supplied.length() > 12) throw new ChatException("Use up to twelve short items in each preference list.");
        JSONArray result = new JSONArray();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < supplied.length(); i++) {
            String item = plainText(new JSONObject().put("item", supplied.get(i)), "item", 120, false);
            if (seen.add(item)) result.put(item);
        }
        return result;
    }

    private static JSONObject sanitizeProfile(JSONObject supplied) throws JSONException, ChatException {
        if (supplied == null) throw new ChatException("Create or update your preference profile first.");
        return new JSONObject().put("summary", plainText(supplied, "summary", 1200, false))
            .put("preferences", preferenceList(supplied, "preferences")).put("avoid", preferenceList(supplied, "avoid"))
            .put("moodContext", plainText(supplied, "moodContext", 300, true))
            .put("energyStyle", plainText(supplied, "energyStyle", 120, true));
    }

    private static JSONObject sanitizeProfileRequest(String text) throws JSONException, ChatException {
        JSONObject supplied = personalizationBody(text);
        return new JSONObject().put("text", plainText(supplied, "text", 12000, false));
    }

    private static String activityKey(JSONObject source) throws ChatException {
        String key = plainText(source, "activityKey", 80, false);
        if (!key.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) throw new ChatException("An activity has an invalid identifier.");
        return key;
    }

    private static JSONObject sanitizeQuestsRequest(String text) throws JSONException, ChatException {
        JSONObject supplied = personalizationBody(text);
        JSONArray history = supplied.optJSONArray("history");
        if (history == null || history.length() > 100) throw new ChatException("Use up to 100 prior activities for a new quest request.");
        JSONArray cleaned = new JSONArray();
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            JSONObject prior = new JSONObject().put("title", plainText(item, "title", 80, false))
                .put("action", plainText(item, "action", 500, false));
            if (item.has("activityKey") && !item.isNull("activityKey") && !"".equals(item.opt("activityKey"))) prior.put("activityKey", activityKey(item));
            cleaned.put(prior);
        }
        return new JSONObject().put("profile", sanitizeProfile(supplied.optJSONObject("profile"))).put("history", cleaned)
            .put("mood", rating(supplied, "mood", 5)).put("energy", rating(supplied, "energy", 3));
    }

    private static JSONObject sanitizeProfileResponse(JSONObject response) throws JSONException, ChatException {
        return new JSONObject().put("profile", sanitizeProfile(response.optJSONObject("profile")))
            .put("systemPrompt", plainText(response, "systemPrompt", 16000, false));
    }

    private static int boundedInteger(JSONObject value, String key, int minimum, int maximum) throws ChatException {
        Object raw = value.opt(key);
        if (!(raw instanceof Number)) throw new ChatException("The activity response could not be read.");
        double number = ((Number) raw).doubleValue();
        if (number < minimum || number > maximum || number != Math.floor(number)) throw new ChatException("The activity response could not be read.");
        return (int) number;
    }

    private static JSONObject sanitizeQuestsResponse(JSONObject response) throws JSONException, ChatException {
        JSONArray quests = response.optJSONArray("quests");
        if (quests == null || quests.length() > 3 || !(response.opt("exhausted") instanceof Boolean))
            throw new ChatException("The suggested activities could not be read. Please try again.");
        JSONArray cleaned = new JSONArray();
        for (int i = 0; i < quests.length(); i++) {
            JSONObject supplied = quests.optJSONObject(i);
            String id = plainText(supplied, "id", 42, false);
            String mechanism = plainText(supplied, "mechanism", 20, false);
            String icon = plainText(supplied, "icon", 10, false);
            String color = plainText(supplied, "color", 10, false);
            String evidence = plainText(supplied, "evidence", 200, false);
            if (!id.matches("generated-[a-f0-9]{32}")
                || !Arrays.asList("savoring", "gratitude", "compassion", "strengths", "connection", "agency").contains(mechanism)
                || !Arrays.asList("sun", "heart", "cloud", "spark").contains(icon)
                || !Arrays.asList("peach", "pink", "lavender", "yellow").contains(color)
                || !(evidence.startsWith("https://doi.org/") || evidence.startsWith("https://www.frontiersin.org/")))
                throw new ChatException("An activity has unsupported details. Please try again.");
            cleaned.put(new JSONObject().put("id", id).put("title", plainText(supplied, "title", 80, false))
                .put("action", plainText(supplied, "action", 500, false)).put("mechanism", mechanism)
                .put("minutes", boundedInteger(supplied, "minutes", 1, 5)).put("activityKey", activityKey(supplied))
                .put("principle", plainText(supplied, "principle", 40, false)).put("icon", icon).put("color", color)
                .put("why", plainText(supplied, "why", 300, false)).put("evidence", evidence));
        }
        return new JSONObject().put("quests", cleaned).put("duplicatesFiltered", boundedInteger(response, "duplicatesFiltered", 0, 12))
            .put("exhausted", response.getBoolean("exhausted"));
    }

    private static JSONObject sanitizeResponse(JSONObject response) throws JSONException, ChatException {
        Object rawReply = response.opt("reply"), rawSummary = response.opt("summary"), urgent = response.opt("urgentSupport");
        if (!(rawReply instanceof String) || ((String) rawReply).trim().isEmpty() || ((String) rawReply).length() > 1800
            || !(rawSummary instanceof String) || ((String) rawSummary).length() > 400 || !(urgent instanceof Boolean))
            throw new ChatException("The chat service returned an incomplete reply. Please try again.");
        Object quest = response.opt("suggestedQuestId");
        if (quest == null) quest = JSONObject.NULL;
        if (quest != JSONObject.NULL && (!(quest instanceof String)
            || !Arrays.asList("light", "thanks", "gentle", "strength", "good", "hello", "outside", "future").contains(quest)))
            throw new ChatException("The chat service returned an invalid suggestion. Please try again.");
        boolean urgentSupport = (Boolean) urgent;
        return new JSONObject().put("reply", rawReply).put("summary", rawSummary)
            .put("suggestedMood", urgentSupport ? JSONObject.NULL : rating(response, "suggestedMood", 5))
            .put("suggestedEnergy", urgentSupport ? JSONObject.NULL : rating(response, "suggestedEnergy", 3))
            .put("suggestedQuestId", urgentSupport ? JSONObject.NULL : quest).put("urgentSupport", urgentSupport);
    }

    private String baseUrl() throws IOException, ChatException {
        String configured = context.getString(R.string.circle_api_base_url).trim();
        if (configured.isEmpty()) throw new ChatException("Chat needs an HTTPS service configured for this build. Private check-ins still work.");
        URL parsed = new URL(configured);
        boolean secure = "https".equals(parsed.getProtocol());
        boolean debugLoopback = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
            && "http".equals(parsed.getProtocol()) && "127.0.0.1".equals(parsed.getHost()) && parsed.getPort() == 8787;
        if ((!secure && !debugLoopback) || parsed.getHost().isEmpty() || parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getRef() != null)
            throw new ChatException("Chat requires HTTPS. Local HTTP is available only in the debug demo.");
        return configured.replaceAll("/+$", "");
    }

    private JSONObject call(String path, JSONObject body, String token, long current)
        throws IOException, JSONException, ChatException, CancelledException, UnauthorizedException {
        ensureCurrent(current);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new ChatException("This check-in is too long. Please start a shorter conversation.");
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl() + path).openConnection();
        synchronized (lock) { ensureCurrent(current); activeConnection = connection; }
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(25000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) { ensureCurrent(current); out.write(bytes); }
            int status = connection.getResponseCode();
            ensureCurrent(current);
            if (status == 401) throw new UnauthorizedException();
            if (status >= 300 && status < 400) throw new ChatException("Chat does not follow redirects. Check the configured service address.");
            // Never expose server error bodies, raw HTML, stack traces or credentials to JS.
            if (status == 429) throw new ChatException("Chat has reached its usage limit. Please try later, or use a private check-in.");
            if (status == 413) throw new ChatException("This check-in is too long. Please start a shorter conversation.");
            if (status == 503) throw new ChatException("Claude chat is currently unavailable. You can use a private check-in instead.");
            if (status < 200 || status >= 300) throw new ChatException("The chat service could not complete this check-in. Please try again later.");
            if (connection.getContentLengthLong() > MAX_BYTES) throw new ChatException("The chat service returned too much data.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    ensureCurrent(current);
                    if (output.size() + count > MAX_BYTES) throw new ChatException("The chat service returned too much data.");
                    output.write(buffer, 0, count);
                }
                ensureCurrent(current);
                return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            synchronized (lock) { if (activeConnection == connection) activeConnection = null; }
            connection.disconnect();
        }
    }

    private static JSONObject errorBody(String message) {
        JSONObject body = new JSONObject();
        try { body.put("message", message); } catch (JSONException ignored) { }
        return body;
    }

    private void deliver(long current, String requestId, String type, JSONObject body, Callback callback) {
        try { body.put("requestId", requestId == null ? "" : requestId.substring(0, Math.min(requestId.length(), 128))); }
        catch (JSONException ignored) { return; }
        main.post(() -> {
            synchronized (lock) {
                if (closed || current != generation) return;
                callback.result(type, body.toString());
            }
        });
    }

    private static final class ChatException extends Exception {
        ChatException(String message) { super(message); }
    }
    private static final class CancelledException extends Exception { }
    private static final class UnauthorizedException extends Exception { }
}
