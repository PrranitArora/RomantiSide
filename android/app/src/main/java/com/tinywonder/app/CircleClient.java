package com.tinywonder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Optional Circle transport. Credentials never cross the JavaScript bridge. */
final class CircleClient {
    interface Callback { void result(String type, String text); }
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(
        1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(12));
    private final Context context;
    private final SharedPreferences credentials;

    CircleClient(Context context) {
        this.context = context.getApplicationContext();
        credentials = this.context.getSharedPreferences("tiny_wonder_circle", Context.MODE_PRIVATE);
    }

    String status() {
        JSONObject status = new JSONObject();
        try {
            boolean joined = !credentials.getString("token", "").isEmpty();
            status.put("joined", joined).put("code", joined ? credentials.getString("code", "") : "")
                .put("displayName", joined ? credentials.getString("displayName", "") : "");
        } catch (JSONException ignored) { }
        return status.toString();
    }

    void request(String action, String payloadJson, Callback callback) {
        if (payloadJson != null && payloadJson.length() > 1_000_000) {
            callback.result("circleError", "This Circle request is too large. Try syncing fewer records."); return;
        }
        try {
            WORKER.execute(() -> {
                try {
                    JSONObject payload = new JSONObject(payloadJson == null || payloadJson.isEmpty() ? "{}" : payloadJson);
                    JSONObject response = perform(action, payload);
                    response.put("action", action);
                    // Never pass the server's profile response or authentication fields to JS.
                    response.remove("token");
                    callback.result("circle", response.toString());
                } catch (CircleException error) {
                    callback.result("circleError", error.getMessage());
                } catch (java.net.SocketTimeoutException error) {
                    callback.result("circleError", "The Circle server took too long to respond. Please try again.");
                } catch (IOException error) {
                    callback.result("circleError", "Could not reach your Circle server. The demo needs its local server and USB connection.");
                } catch (JSONException | IllegalArgumentException error) {
                    callback.result("circleError", "Circle could not read that request or server response. Please try again.");
                } catch (RuntimeException error) {
                    callback.result("circleError", "Circle could not complete that action. Please try again.");
                }
            });
        } catch (RejectedExecutionException error) {
            callback.result("circleError", "Circle is finishing earlier requests. Please wait a moment.");
        }
    }

    private JSONObject perform(String action, JSONObject payload) throws IOException, JSONException, CircleException {
        String token = credentials.getString("token", "");
        String period = "all".equals(payload.optString("period")) ? "all" : "week";
        if ("join".equals(action)) {
            if (token.isEmpty()) {
                String name = java.text.Normalizer.normalize(payload.optString("displayName").trim(), java.text.Normalizer.Form.NFC);
                if (name.isEmpty() || name.codePointCount(0, name.length()) > 32
                    || name.getBytes(StandardCharsets.UTF_8).length > 128
                    || name.matches(".*[\\x00-\\x1F\\x7F-\\x9F\\u202A-\\u202E\\u2066-\\u2069].*"))
                    throw new CircleException("Choose a Circle name between 1 and 32 characters, without control characters.");
                JSONObject created = call("POST", "/v1/profile", new JSONObject().put("displayName", name), "");
                String newToken = created.optString("token"), code = created.optString("code"), displayName = created.optString("displayName");
                if (newToken.isEmpty() || newToken.length() > 4096 || code.isEmpty() || displayName.isEmpty()) throw new CircleException("The Circle server returned an incomplete profile. Please try again.");
                credentials.edit().putString("token", newToken).putString("code", code).putString("displayName", displayName).commit();
            }
            return new JSONObject(status());
        }
        if (token.isEmpty()) throw new CircleException("Join Circle before sharing activity or viewing friends.");
        if ("leave".equals(action)) {
            JSONObject deleted = call("DELETE", "/v1/profile", null, token);
            if (!deleted.optBoolean("deleted")) throw new CircleException("The server did not confirm deletion. Your Circle account is still connected.");
            credentials.edit().clear().commit();
            return new JSONObject().put("joined", false).put("deleted", true);
        }
        if ("sync".equals(action)) {
            JSONArray supplied = payload.optJSONArray("events");
            if (supplied == null) supplied = new JSONArray();
            if (supplied.length() > 10000) throw new CircleException("Please sync at most 10,000 completion records at a time.");
            JSONArray sanitized = new JSONArray();
            for (int i = 0; i < supplied.length(); i++) {
                JSONObject event = supplied.optJSONObject(i);
                if (event == null) throw new CircleException("A completion record could not be read.");
                String id = event.optString("id");
                Object rawAt = event.opt("at");
                if (!id.matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,127}") || !(rawAt instanceof Number)) throw new CircleException("A completion record has an invalid ID or date.");
                long at = event.optLong("at");
                if (at <= 0 || ((Number) rawAt).doubleValue() != (double) at) throw new CircleException("A completion record has an invalid date.");
                // The transport intentionally drops reflection text, mood, energy, contacts,
                // pictures and every field other than a stable completion ID and timestamp.
                sanitized.put(new JSONObject().put("id", id).put("at", at));
            }
            int accepted = 0;
            for (int start = 0; start < sanitized.length(); start += 500) {
                JSONArray batch = new JSONArray();
                for (int i = start; i < Math.min(start + 500, sanitized.length()); i++) batch.put(sanitized.getJSONObject(i));
                JSONObject result = call("POST", "/v1/events", new JSONObject().put("events", batch), token);
                accepted += result.optInt("accepted");
            }
            JSONObject board = call("GET", "/v1/leaderboard?period=" + period, null, token);
            board.put("accepted", accepted);
            return board;
        }
        if ("add".equals(action)) {
            String code = payload.optString("code").trim().toUpperCase(java.util.Locale.ROOT);
            if (!code.matches("[A-HJ-NP-Z2-9]{12}")) throw new CircleException("Enter your friend's 12-character Circle code.");
            JSONObject added = call("POST", "/v1/friends", new JSONObject().put("code", code), token);
            JSONObject board = call("GET", "/v1/leaderboard?period=" + period, null, token);
            if (added.has("friend")) board.put("friend", added.get("friend"));
            board.put("added", added.optBoolean("added"));
            return board;
        }
        if ("board".equals(action)) return call("GET", "/v1/leaderboard?period=" + period, null, token);
        throw new CircleException("That Circle action is not available.");
    }

    private String baseUrl() throws IOException, CircleException {
        String configured = context.getString(R.string.circle_api_base_url).trim();
        if (configured.isEmpty()) throw new CircleException("Circle needs an HTTPS server configured for this build.");
        URL parsed = new URL(configured);
        boolean secure = "https".equals(parsed.getProtocol());
        boolean debugLoopback = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
            && "http".equals(parsed.getProtocol()) && "127.0.0.1".equals(parsed.getHost()) && parsed.getPort() == 8787;
        if ((!secure && !debugLoopback) || parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getRef() != null)
            throw new CircleException("Circle requires HTTPS. Local HTTP is available only in the debug demo.");
        return configured.replaceAll("/+$", "");
    }

    private JSONObject call(String method, String path, JSONObject body, String token) throws IOException, JSONException, CircleException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl() + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) throw new CircleException("Circle does not follow server redirects. Check the configured server address.");
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            JSONObject response;
            if (stream == null) response = new JSONObject();
            else try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 262144) throw new CircleException("The Circle server returned too much data.");
                    output.write(buffer, 0, count);
                }
                String text = output.toString(StandardCharsets.UTF_8.name());
                response = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            }
            if (status < 200 || status >= 300) {
                String message = response.optString("message", "Circle request failed (" + status + "). Please try again.");
                throw new CircleException(message.length() > 180 ? message.substring(0, 180) : message);
            }
            return response;
        } finally { connection.disconnect(); }
    }

    private static final class CircleException extends Exception {
        CircleException(String message) { super(message); }
    }
}
