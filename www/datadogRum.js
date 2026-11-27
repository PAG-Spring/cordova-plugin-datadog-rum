
/* Datadog facade for OutSystems.
 * OutSystems only ever calls window.plugins.datadogRum.<method>().
 * - Native calls go through cordova exec (see DatadogRum.java).
 * - Web (UI / requests / replay) calls go to the Browser SDK (window.DD_RUM).
 * Every method is safe: it never throws into OutSystems logic.
 */
var exec = require('cordova/exec');
var SERVICE = 'DatadogRum';

var SITE_MAP = {
  US1: 'datadoghq.com', US3: 'us3.datadoghq.com', US5: 'us5.datadoghq.com',
  EU1: 'datadoghq.eu', AP1: 'ap1.datadoghq.com'
};

function native(action, args) {
  return new Promise(function (resolve, reject) {
    exec(resolve, reject, SERVICE, action, args || []);
  });
}

function rum() { return window.DD_RUM; }

function safe(fn) {
  try { return fn(); } catch (e) { console.warn('[DatadogRum]', e); }
}

var blockTimers = {};

var DatadogRum = {

  /* Call ONCE at app start (after the Browser SDK script has loaded).
   * options (all optional): service, version, defaultPrivacyLevel, allowedTracingUrls, ...
   * Anything in options overrides the defaults below. */
  init: function (options) {
      options = options || {};
  
      return native('getConfig').then(function (nc) {
  
          if (!rum() || typeof rum().init !== "function") {
              return false;
          }
  
          if (!nc || !nc.applicationId || !nc.clientToken) {
              return false;
          }
  
          var cfg = {
              applicationId: nc.applicationId,
              clientToken: nc.clientToken,
              site: SITE_MAP[nc.site] || 'datadoghq.eu',
              env: nc.env,
              service: nc.service,
              sessionSampleRate: Number(nc.sessionSampleRate != null ? nc.sessionSampleRate : 100),
              sessionReplaySampleRate: Number(nc.replaySampleRate != null ? nc.replaySampleRate : 20),
              trackUserInteractions: true,
              trackResources: true,
              trackLongTasks: true,
              defaultPrivacyLevel: 'mask'
          };
  
          Object.keys(options).forEach(function (k) {
              if (options[k] !== undefined &&
                  options[k] !== null &&
                  options[k] !== '') {
  
                  cfg[k] = options[k];
              }
          });
  
          rum().init(cfg);
  
          return !!rum().getInternalContext();
  
      }).catch(function () {
          return false;
      });
  },

  /* ---------- user / consent (native + web) ---------- */
  setUser: function (user) { // {id, name, email}
    safe(function () { rum() && rum().setUser(user); });
    return native('setUser', [user]).catch(function () {});
  },
  clearUser: function () {
    safe(function () { rum() && rum().clearUser(); });
    return native('setUser', [{}]).catch(function () {});
  },
  setTrackingConsent: function (consent) { // 'granted' | 'not_granted' | 'pending'
    safe(function () { rum() && rum().setTrackingConsent(consent); });
    return native('setTrackingConsent', [consent]).catch(function () {});
  },

  /* ---------- screens ---------- */
  // Views are tracked automatically from the URL. Call this in a Screen OnReady to give the view a readable name.
  setViewName: function (name) { safe(function () { rum() && rum().setViewName(name); }); },

  /* ---------- blocks (UI components) ---------- */
  // Block OnInitialize -> startBlock('Name'); Block OnRendered -> endBlock('Name')
  startBlock: function (name) { blockTimers[name] = performance.now(); },
  endBlock: function (name, context) {
    var t0 = blockTimers[name];
    if (t0 === undefined) return;
    delete blockTimers[name];
    var ctx = Object.assign({ block: name, duration_ms: Math.round(performance.now() - t0) }, context || {});
    safe(function () { rum() && rum().addAction('block_rendered:' + name, ctx); });
  },

  /* ---------- generic custom events ---------- */
  addAction: function (name, context) { safe(function () { rum() && rum().addAction(name, context || {}); }); },
  addTiming: function (name) { safe(function () { rum() && rum().addTiming(name); }); },
  addError: function (message, context) {
    safe(function () { rum() && rum().addError(new Error(message), context || {}); });
  },
  setGlobalContextProperty: function (key, value) {
    safe(function () { rum() && rum().setGlobalContextProperty(key, value); });
  }

  // ADD FUTURE FUNCTIONS HERE. Native-only: add an action in DatadogRum.java + native('action', [args]).
};

module.exports = DatadogRum;

