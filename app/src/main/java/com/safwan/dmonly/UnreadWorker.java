package com.safwan.dmonly;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// Runs about every 15 minutes while the app is closed. Asks Instagram's web
// API for the DM badge count using the session cookies the WebView saved.
public class UnreadWorker extends Worker {
    private static final String BADGE_URL = "https://www.instagram.com/api/v1/direct_v2/get_badge_count/?no_raven=1";
    // Public app id the instagram.com web client sends with every API call.
    private static final String WEB_APP_ID = "936619743392459";

    public UnreadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (Notifier.foreground) return Result.success();

        String cookies = CookieManager.getInstance().getCookie("https://www.instagram.com");
        if (cookies == null || !cookies.contains("sessionid=")) return Result.success(); // logged out

        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BADGE_URL).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Cookie", cookies);
            c.setRequestProperty("User-Agent", MainActivity.chromeUserAgent(WebSettings.getDefaultUserAgent(getApplicationContext())));
            c.setRequestProperty("X-IG-App-ID", WEB_APP_ID);
            c.setRequestProperty("X-CSRFToken", cookie(cookies, "csrftoken"));
            c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            c.setRequestProperty("Referer", "https://www.instagram.com/direct/inbox/");
            c.setRequestProperty("Accept", "application/json");

            // Anything but 200 (logged out, rate limited, endpoint moved): try again next period.
            if (c.getResponseCode() != 200) return Result.success();
            int count = new JSONObject(read(c.getInputStream())).optInt("badge_count", -1);
            if (count >= 0) Notifier.unread(getApplicationContext(), count);
        } catch (IOException | JSONException ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
        return Result.success();
    }

    private static String cookie(String cookies, String name) {
        for (String part : cookies.split(";")) {
            String p = part.trim();
            if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
        }
        return "";
    }

    private static String read(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
