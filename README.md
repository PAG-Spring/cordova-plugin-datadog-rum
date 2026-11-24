
# Datadog RUM + Session Replay for OutSystems (Android first)

## How it works
OutSystems mobile screens are web pages inside a Cordova WebView, so two SDKs cooperate:

| Layer | Does | Where |
|---|---|---|
| Native Android SDK | owns the session, uploads data, records native replay | `DatadogRum.java` (started before the first page loads) |
| Browser SDK (`window.DD_RUM`) | records screens, clicks, requests, long tasks, web replay and forwards them to native through the WebView bridge | loaded as a Script in OutSystems |
| `datadogRum.js` | the only API OutSystems calls | `window.plugins.datadogRum.*` |

Native init MUST happen in `pluginInitialize()` (before page load): Android only injects the JS bridge into pages loaded after `WebViewTracking.enable`. That is why the keys now live in plugin variables, not in the OutSystems JS node.

## Step 1 - Replace the files in your GitHub repo
Replace `plugin.xml`, `src/android/DatadogRum.java`, `www/datadogRum.js` with the versions in this folder (keep `ios/` for now, see "iOS").
In `plugin.xml` replace every `X.Y.Z` with the latest Datadog Android SDK version (same on all three lines):
https://search.maven.org/artifact/com.datadoghq/dd-sdk-android-rum
Commit, then create a tag, e.g. `v0.2.0`.

## Step 2 - Extensibility Configurations (the plugin module, `DatadogRUM_MobilePlugin`, and/or the main app)
```json
{
  "plugin": {
    "url": "https://github.com/PAG-Spring/cordova-plugin-datadog-rum.git#v0.2.0",
    "variables": [
      { "name": "DD_APPLICATION_ID", "value": "<your RUM application id>" },
      { "name": "DD_CLIENT_TOKEN", "value": "<your client token>" },
      { "name": "DD_ENV", "value": "dev" },
      { "name": "DD_SERVICE", "value": "<your-service-name>" },
      { "name": "DD_SITE", "value": "EU1" },
      { "name": "DD_SESSION_SAMPLE_RATE", "value": "100" },
      { "name": "DD_REPLAY_SAMPLE_RATE", "value": "20" },
      { "name": "DD_WEBVIEW_HOSTS", "value": "localhost,<your-outsystems-server-hostname>" }
    ]
  }
}
```
Use a different JSON per environment (Dev/Test/Prod) so `DD_ENV` and keys differ. Use the RUM application created as type **Android** in Datadog.

## Step 3 - Add the Browser SDK to OutSystems
1. Get `datadog-rum.js` from the "CDN sync" tab of https://docs.datadoghq.com/real_user_monitoring/application_monitoring/browser/setup.md (EU site) and download it.
2. Service Studio, main app module: Interface tab > Scripts > add the file (it becomes a Script).
3. Add that Script to the Layout block's **Required Scripts** so it is loaded on every screen.

## Step 4 - Initialise (one place only)
In the plugin module, edit client action `DatadogRUM_InitSDK` -> JavaScript node `InitSdkJS` and replace the code with:
```js
window.plugins.datadogRum.init({
    // service/version are optional overrides, e.g. version: "1.0.0"
    // allowedTracingUrls: ["https://<your-backend-host>"] // distributed tracing, later
});
```
(The three input parameters are no longer needed; you may remove them.)
In the main app call `DatadogRUM_InitSDK` once, from `OnApplicationReady`. Remove any old init so you don't get duplicate sessions.

## Step 5 - Use it
| Need | Where | JavaScript node |
|---|---|---|
| Readable screen names | Each Screen `OnReady` | `window.plugins.datadogRum.setViewName("$parameters.ScreenName");` (add an input, or hardcode the name) |
| Block render time | Block `OnInitialize` and `OnRendered` | `window.plugins.datadogRum.startBlock("MyBlock");` / `window.plugins.datadogRum.endBlock("MyBlock");` |
| Who is the user | After login | `window.plugins.datadogRum.setUser({id: "$parameters.Id"});` (use an internal id, not name/email, for a bank) |
| Business events | Anywhere | `window.plugins.datadogRum.addAction("payment_submitted", {amount_band: "<100"});` |
| Errors | Global exception handler | `window.plugins.datadogRum.addError("$parameters.Message");` |
| User requests | nothing to do | XHR/fetch from server actions are captured as resources automatically |

In JavaScript nodes use OutSystems input parameters (the `$parameters.X` syntax) rather than hardcoding values.

## Step 6 - Build and verify
1. Generate a new mobile package (extensibility changes require it) and install on an Android device.
2. `adb logcat -s DatadogRum` should print "Datadog native SDK started".
3. In Chrome open `chrome://inspect`, inspect the app, and run `window.DatadogEventBridge` (should exist) and `location.hostname` (this value must be in `DD_WEBVIEW_HOSTS`).
4. Datadog > RUM > Sessions: the session should list web views, requests and a replay combining native and web.

## Extending later
- Web feature: add a method in `www/datadogRum.js`.
- Native feature: add a `case` in `execute()` and a method in `DatadogRum.java`, plus a method in `datadogRum.js` calling `native('yourAction', [args])`.
- Bump the tag in the extensibility URL each time (OutSystems caches the plugin by URL).

## iOS
Same design: initialise in the plugin's native `pluginInitialize`, then `WebViewTracking.enable(webView:hosts:)` on the WKWebView, with the same plugin variables and the same JS. Needs Swift file + podspecs in `plugin.xml`.


