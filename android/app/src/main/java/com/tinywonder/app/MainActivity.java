package com.tinywonder.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION = 81;
    private static final int VOICE_PERMISSION = 82;
    private static final int NOTIFICATION_PERMISSION = 83;
    private static final int CONTACT_PICKER = 84;
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private WebView webView;
    private PermissionRequest cameraRequest;
    private SpeechRecognizer speech;
    private boolean pageReady;
    private boolean listening;
    private boolean pickingContact;
    private CircleClient circle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable voiceTimeout = () -> { if (speech != null && listening) speech.stopListening(); };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        circle = new CircleClient(this);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ReminderScheduler.createChannel(this);
        ReminderScheduler.scheduleDaily(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(255, 249, 237));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setGeolocationEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        WebView.setWebContentsDebuggingEnabled(false);
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse local = trusted(request.getUrl()) ? assetLoader.shouldInterceptRequest(request.getUrl()) : null;
                return local != null ? local : new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", null, new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (trusted(request.getUrl()) && request.getUrl().getPath() != null && request.getUrl().getPath().startsWith("/assets/")) return false;
                String scheme = request.getUrl().getScheme();
                if (request.isForMainFrame() && request.hasGesture() && ("https".equals(scheme) || "http".equals(scheme) || "mailto".equals(scheme))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl())); }
                    catch (android.content.ActivityNotFoundException ignored) { }
                }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = trusted(Uri.parse(url));
                if (pageReady) emit("refresh", null);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!trusted(request.getOrigin()) || !Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        request.deny(); return;
                    }
                    if (cameraRequest != null) cameraRequest.deny();
                    cameraRequest = request;
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) grantCamera();
                    else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (cameraRequest == request) cameraRequest = null;
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "Native");
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(Color.rgb(255, 249, 237));
        container.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // Inset the native parent, not WebView's internal content; WebView padding is
        // not a reliable CSS viewport inset on current Android WebView releases.
        container.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                return WindowInsets.CONSUMED;
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            }
        });
        setContentView(container);
        container.requestApplyInsets();
        webView.loadUrl(ORIGIN + "/assets/index.html");
    }

    private static boolean trusted(Uri uri) {
        return "https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private void grantCamera() {
        if (cameraRequest != null) {
            cameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            cameraRequest = null;
        }
    }

    private void emit(String type, String text) {
        JSONObject event = new JSONObject();
        try { event.put("type", type); if (text != null) event.put("text", text); }
        catch (JSONException ignored) { }
        runOnUiThread(() -> {
            if (webView != null && pageReady) webView.evaluateJavascript("window.onNativeEvent && window.onNativeEvent(" + event + ");", null);
        });
    }

    private void startSpeech() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, VOICE_PERMISSION); return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            emit("voiceError", "Speech recognition is unavailable on this device. You can type your check-in instead."); return;
        }
        if (speech != null) { speech.cancel(); speech.destroy(); }
        speech = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this) : SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { emit("voiceStarted", null); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int error) {
                listening = false; handler.removeCallbacks(voiceTimeout);
                emit("voiceError", error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ? "No words heard. Try again, or type a few words instead."
                    : "Speech recognition stopped (" + error + "). You can try again or type your check-in.");
            }
            @Override public void onResults(Bundle results) {
                listening = false; handler.removeCallbacks(voiceTimeout);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) emit("voice", matches.get(0));
                else emit("voiceError", "No words heard. You can type your check-in instead.");
            }
            @Override public void onPartialResults(Bundle results) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L);
        listening = true;
        try {
            speech.startListening(intent);
            handler.postDelayed(voiceTimeout, 120_000);
        } catch (RuntimeException error) {
            listening = false;
            emit("voiceError", "Speech recognition could not start. You can type your check-in instead.");
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == CAMERA_PERMISSION) {
            if (granted) grantCamera();
            else if (cameraRequest != null) { cameraRequest.deny(); cameraRequest = null; }
        } else if (code == VOICE_PERMISSION) {
            if (granted) startSpeech();
            else emit("voiceError", "Microphone access was not granted. Text check-ins still work.");
        } else if (code == NOTIFICATION_PERMISSION) {
            emit("notificationPermission", null);
            if (granted) ReminderScheduler.scheduleDaily(this);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CONTACT_PICKER) return;
        pickingContact = false;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            emit("contactCancelled", null); return;
        }
        Uri selected = data.getData();
        if (!"content".equals(selected.getScheme())) { emit("contactError", "That contact could not be read."); return; }
        // ACTION_PICK grants access to the chosen row. Read only its display name;
        // never query the address book or request, retain, or send a phone number.
        try (Cursor cursor = getContentResolver().query(selected,
            new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    name = name.trim();
                    emit("contact", name.length() > 100 ? name.substring(0, 100) : name);
                    return;
                }
            }
            emit("contactError", "That contact has no display name. You can enter a nickname yourself.");
        } catch (RuntimeException error) {
            emit("contactError", "That contact could not be read. You can enter a nickname yourself.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        if (pageReady) emit("refresh", null);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (pageReady) emit("refresh", null);
    }

    @Override protected void onPause() {
        if (speech != null && listening) { speech.cancel(); listening = false; handler.removeCallbacks(voiceTimeout); }
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (speech != null) { speech.cancel(); speech.destroy(); speech = null; }
        if (cameraRequest != null) { cameraRequest.deny(); cameraRequest = null; }
        if (webView != null) { webView.removeJavascriptInterface("Native"); webView.destroy(); webView = null; }
        super.onDestroy();
    }

    public final class NativeBridge {
        @JavascriptInterface public String getState() { return StateStore.read(MainActivity.this).toString(); }
        @JavascriptInterface public void saveState(String state) { StateStore.save(MainActivity.this, state); }
        @JavascriptInterface public String notificationStatus() {
            JSONObject status = new JSONObject();
            try {
                status.put("permission", ReminderScheduler.canNotify(MainActivity.this));
                status.put("enabled", StateStore.enabled(MainActivity.this));
            } catch (JSONException ignored) { }
            return status.toString();
        }
        @JavascriptInterface public void configureReminders(boolean enabled, int checkInHour, int questHour) {
            StateStore.edit(MainActivity.this, state -> {
                JSONObject prefs = state.optJSONObject("preferences");
                if (prefs == null) { prefs = new JSONObject(); state.put("preferences", prefs); }
                prefs.put("reminders", enabled);
                prefs.put("checkInHour", Math.min(20, Math.max(8, checkInHour)));
                prefs.put("questHour", Math.min(20, Math.max(8, questHour)));
            });
            if (enabled) ReminderScheduler.scheduleDaily(MainActivity.this);
            else ReminderScheduler.cancel(MainActivity.this);
        }
        @JavascriptInterface public void requestNotifications() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
                else emit("notificationPermission", null);
            });
        }
        @JavascriptInterface public void sendTestNotification() {
            runOnUiThread(() -> {
                if (StateStore.enabled(MainActivity.this) && ReminderScheduler.canNotify(MainActivity.this)) {
                    boolean sent = ReminderScheduler.showQuest(MainActivity.this, null);
                    emit(sent ? "notificationTest" : "notificationError", sent ? "A side quest was sent to your notification shade." : ReminderScheduler.quietNow() ? "Quiet hours are 9 pm to 8 am. Try a test during the day." : "You have completed or skipped today's quests. Rest is welcome.");
                } else emit("notificationError", "Enable reminders and allow notifications first.");
            });
        }
        @JavascriptInterface public void sendTestCheckInNotification() {
            runOnUiThread(() -> {
                if (!StateStore.enabled(MainActivity.this) || !ReminderScheduler.canNotify(MainActivity.this)) {
                    emit("notificationError", "Enable reminders and allow notifications first."); return;
                }
                boolean sent = ReminderScheduler.showCheckIn(MainActivity.this);
                emit(sent ? "notificationTest" : "notificationError", sent ? "A check-in was sent. Expand it and tap Reply to try an inline reflection." : "Quiet hours are 9 pm to 8 am. Try a test during the day.");
            });
        }
        @JavascriptInterface public void startVoice() { runOnUiThread(MainActivity.this::startSpeech); }
        @JavascriptInterface public void pickContact() {
            runOnUiThread(() -> {
                if (pickingContact) return;
                try {
                    pickingContact = true;
                    startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), CONTACT_PICKER);
                } catch (RuntimeException error) {
                    pickingContact = false;
                    emit("contactError", "No contact picker is available. You can enter a nickname yourself.");
                }
            });
        }
        @JavascriptInterface public String getCircleStatus() { return circle.status(); }
        @JavascriptInterface public void circleRequest(String action, String payloadJson) {
            circle.request(action, payloadJson, MainActivity.this::emit);
        }
        @JavascriptInterface public void stopVoice() {
            runOnUiThread(() -> { if (speech != null && listening) speech.stopListening(); });
        }
        @JavascriptInterface public void deleteData() {
            ReminderScheduler.cancel(MainActivity.this);
            StateStore.clear(MainActivity.this);
            runOnUiThread(() -> {
                if (speech != null) speech.cancel();
                if (webView != null) { webView.clearCache(true); android.webkit.WebStorage.getInstance().deleteAllData(); }
            });
        }
    }
}
