
package com.company.datadog;

import android.app.Application;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;

import com.datadog.android.Datadog;
import com.datadog.android.DatadogSite;
import com.datadog.android.core.configuration.Configuration;
import com.datadog.android.privacy.TrackingConsent;
import com.datadog.android.rum.Rum;
import com.datadog.android.rum.RumConfiguration;
import com.datadog.android.sessionreplay.SessionReplay;
import com.datadog.android.sessionreplay.SessionReplayConfiguration;
import com.datadog.android.webview.WebViewTracking;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Datadog bridge.
 *
 * DESIGN
 * - The native SDK is started in pluginInitialize(), i.e. BEFORE the first page loads, using
 * values from config.xml (set via plugin variables). Android only exposes the JS event bridge
 * to pages loaded AFTER WebViewTracking.enable(), so starting from a JS call would be too late.
 * - The Browser SDK (in the WebView) records UI/requests/replay of the OutSystems screens and
 * forwards them through the bridge into the native session.
 * - JS->native calls go through execute(): add a new action = add a case + a method.
 */
public class DatadogRum extends CordovaPlugin {

    private static final String TAG = "DatadogRum";
    private static final String NOT_SET = "NOT_SET";

    private boolean started = false;
    private final JSONObject publicConfig = new JSONObject();

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void pluginInitialize() {
        try {
            String applicationId = pref("DD_APPLICATION_ID", NOT_SET);
            String clientToken = pref("DD_CLIENT_TOKEN", NOT_SET);
            if (NOT_SET.equals(applicationId) || NOT_SET.equals(clientToken)) {
                Log.w(TAG, "DD_APPLICATION_ID / DD_CLIENT_TOKEN not set - Datadog not started");
                return;
            }
            String env = pref("DD_ENV", "dev");
            String service = pref("DD_SERVICE", "outsystems-app");
            String site = pref("DD_SITE", "EU1");
            float sessionRate = parseFloat(pref("DD_SESSION_SAMPLE_RATE", "100"), 100f);
            float replayRate = parseFloat(pref("DD_REPLAY_SAMPLE_RATE", "20"), 20f);

            Application app = cordova.getActivity().getApplication();

            Configuration configuration = new Configuration.Builder(clientToken, env, "")
                    .useSite(parseSite(site))
                    .build();
            Datadog.initialize(app, configuration, TrackingConsent.GRANTED);

            Rum.enable(new RumConfiguration.Builder(applicationId)
                    .setServiceName(service)
                    .setSessionSampleRate(sessionRate)
                    .build());

            SessionReplay.enable(new SessionReplayConfiguration.Builder(replayRate).build());

            enableWebViewTracking(parseHosts(pref("DD_WEBVIEW_HOSTS", "localhost")));

            publicConfig.put("applicationId", applicationId);
            publicConfig.put("clientToken", clientToken);
            publicConfig.put("env", env);
            publicConfig.put("service", service);
            publicConfig.put("site", site);
            started = true;
            Log.i(TAG, "Datadog native SDK started");
        } catch (Exception e) {
            Log.e(TAG, "Datadog start failed", e);
        }
    }

    // ------------------------------------------------------------------ JS -> native

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext cb) {
        try {
            switch (action) {
                case "getConfig": return getConfig(cb);
                case "setUser": return setUser(args, cb);
                case "setTrackingConsent": return setTrackingConsent(args, cb);
                // ADD NEW ACTIONS HERE, e.g. case "addAttribute": return addAttribute(args, cb);
                default: return false; // unknown action
            }
        } catch (Exception e) {
            cb.error(e.getMessage());
            return true;
        }
    }

    private boolean getConfig(CallbackContext cb) throws Exception {
        JSONObject out = new JSONObject(publicConfig.toString());
        out.put("started", started);
        cb.success(out);
        return true;
    }

    private boolean setUser(JSONArray args, CallbackContext cb) throws Exception {
        if (!started) { cb.error("Datadog not started"); return true; }
        JSONObject u = args.optJSONObject(0);
        if (u == null) u = new JSONObject();
        Datadog.setUserInfo(
                u.optString("id", null),
                u.optString("name", null),
                u.optString("email", null),
                new HashMap<String, Object>());
        cb.success();
        return true;
    }

    private boolean setTrackingConsent(JSONArray args, CallbackContext cb) throws Exception {
        if (!started) { cb.error("Datadog not started"); return true; }
        String v = args.optString(0, "granted");
        TrackingConsent consent =
                "not_granted".equals(v) ? TrackingConsent.NOT_GRANTED
              : "pending".equals(v) ? TrackingConsent.PENDING
              : TrackingConsent.GRANTED;
        Datadog.setTrackingConsent(consent);
        cb.success();
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private void enableWebViewTracking(List<String> hosts) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "WebViewTracking must be enabled on the main thread");
            return;
        }
        View v = webView.getView();
        if (v instanceof WebView) {
            WebViewTracking.enable((WebView) v, hosts);
        } else {
            Log.w(TAG, "Cordova view is not an android.webkit.WebView - web tracking skipped");
        }
    }

    private String pref(String name, String def) {
        String v = preferences.getString(name, def);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    private static float parseFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    private static List<String> parseHosts(String csv) {
        List<String> out = new ArrayList<>();
        for (String h : csv.split(",")) {
            if (!h.trim().isEmpty()) out.add(h.trim());
        }
        return out;
    }

    private static DatadogSite parseSite(String s) {
        switch (s.toUpperCase()) {
            case "US1": return DatadogSite.US1;
            case "US3": return DatadogSite.US3;
            case "US5": return DatadogSite.US5;
            case "AP1": return DatadogSite.AP1;
            default: return DatadogSite.EU1;
        }
    }
}


