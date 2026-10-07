package com.safwan.dmonly;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.instagram.com/direct/inbox/";
    private static final int PICK_FILE = 1;

    // Hosts that stay inside the app (login, 2FA, media). Everything else opens in the browser.
    private static final String[] IN_APP_HOSTS = {"instagram.com", "facebook.com", "meta.com", "fbcdn.net", "cdninstagram.com"};

    private WebView web;
    private ValueCallback<Uri[]> filePick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        // Android 15 draws edge to edge; keep Instagram clear of the status bar and keyboard.
        if (Build.VERSION.SDK_INT >= 35) {
            web.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return insets;
            });
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(chromeUserAgent(s.getUserAgentString()));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        String cage = readAsset("cage.js");
        boolean documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (documentStart) {
            WebViewCompat.addDocumentStartJavaScript(web, cage, Collections.singleton("https://*.instagram.com"));
        }

        // cage.js reports the unread count from the page title while the app is alive.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "DMOnlyNative", Collections.singleton("https://*.instagram.com"),
                    (view, message, origin, isMainFrame, reply) -> {
                        try {
                            Notifier.unread(this, Integer.parseInt(message.getData()));
                        } catch (NumberFormatException ignored) {
                        }
                    });
        }
        scheduleUnreadCheck();
        requestNotificationPermission();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                // Older WebViews lack document-start scripts; inject as early as we can.
                if (!documentStart && isInstagram(url)) view.evaluateJavascript(cage, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!documentStart && isInstagram(url)) view.evaluateJavascript(cage, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (inApp(uri.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePick != null) filePick.onReceiveValue(null);
                filePick = callback;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    filePick = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(HOME);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE && filePick != null) {
            filePick.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePick = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Notifier.foreground = true;
        Notifier.clear(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Notifier.foreground = false;
        CookieManager.getInstance().flush();
    }

    // Look like regular Chrome so Instagram serves its normal mobile site and login.
    static String chromeUserAgent(String webViewUserAgent) {
        return webViewUserAgent.replace("; wv", "").replaceAll("Version/\\S+ ", "");
    }

    // Background check for when the app is closed (UnreadWorker). 15 minutes is Android's minimum.
    private void scheduleUnreadCheck() {
        PeriodicWorkRequest check = new PeriodicWorkRequest.Builder(UnreadWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("unread", ExistingPeriodicWorkPolicy.KEEP, check);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
    }

    private static boolean isInstagram(String url) {
        String host = url == null ? null : Uri.parse(url).getHost();
        return host != null && (host.equals("instagram.com") || host.endsWith(".instagram.com"));
    }

    private static boolean inApp(String host) {
        if (host == null) return true;
        for (String h : IN_APP_HOSTS) {
            if (host.equals(h) || host.endsWith("." + h)) return true;
        }
        return false;
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException("missing asset " + name, e);
        }
    }
}
