package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.scd.android.res.Res
import com.scd.android.res.cancel
import com.scd.android.res.import_g_login_hint
import com.scd.android.res.import_g_login_title
import com.scd.android.res.import_sp_login_hint
import com.scd.android.res.import_sp_login_title
import com.scd.android.res.import_ym_login_hint
import com.scd.android.res.import_ym_login_title
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.*
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontTextStyleFootnote
import platform.UIKit.UILabel
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.NSLayoutConstraint
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataRecord
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject


@OptIn(ExperimentalForeignApi::class)
class WebLoginSheet(
    private val title: String,
    private val hint: String,
    private val cancelText: String,
    private val onCancel: () -> Unit,
) {
    var onPage: (String?) -> Unit = {}

    var onNavigate: (String) -> Boolean = { false }

    var onMessage: (String) -> Unit = {}

    var web: WKWebView? = null
        private set
    private var nav: UINavigationController? = null
    private var closed = false
    private var hasScript = false
    private val navDelegate = NavDelegate(this)
    private val messages = MessageHandler(this)

    fun open(url: String, userAgent: String? = null, script: String? = null) {
        val root = IosFiles.root ?: return onCancel()
        val config = WKWebViewConfiguration()
        if (script != null) {
            hasScript = true
            config.userContentController.addUserScript(
                WKUserScript(source = script, injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart, forMainFrameOnly = false),
            )
            config.userContentController.addScriptMessageHandler(messages, name = HANDLER)
        }
        val view = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config)
        userAgent?.let { view.customUserAgent = it }
        view.navigationDelegate = navDelegate
        web = view

        val label = UILabel()
        label.text = hint
        label.numberOfLines = 0
        label.font = UIFont.preferredFontForTextStyle(UIFontTextStyleFootnote)
        label.adjustsFontForContentSizeCategory = true
        label.textColor = UIColor.secondaryLabelColor

        val vc = UIViewController(nibName = null, bundle = null)
        vc.title = title
        val host = vc.view
        host.backgroundColor = UIColor.systemBackgroundColor
        label.translatesAutoresizingMaskIntoConstraints = false
        view.translatesAutoresizingMaskIntoConstraints = false
        host.addSubview(label)
        host.addSubview(view)
        val guide = host.safeAreaLayoutGuide
        NSLayoutConstraint.activateConstraints(
            listOf(
                label.topAnchor.constraintEqualToAnchor(guide.topAnchor, constant = 8.0),
                label.leadingAnchor.constraintEqualToAnchor(guide.leadingAnchor, constant = 16.0),
                label.trailingAnchor.constraintEqualToAnchor(guide.trailingAnchor, constant = -16.0),
                view.topAnchor.constraintEqualToAnchor(label.bottomAnchor, constant = 8.0),
                view.leadingAnchor.constraintEqualToAnchor(host.leadingAnchor),
                view.trailingAnchor.constraintEqualToAnchor(host.trailingAnchor),
                view.bottomAnchor.constraintEqualToAnchor(host.bottomAnchor),
            ),
        )
        vc.navigationItem.leftBarButtonItem = UIBarButtonItem(
            primaryAction = UIAction.actionWithTitle(cancelText, image = null, identifier = null) { _ ->
                close()
                onCancel()
            },
        )
        val navigation = UINavigationController(rootViewController = vc)
        navigation.modalInPresentation = true
        nav = navigation
        root.presentViewController(navigation, animated = true, completion = null)
        load(url)
    }

    fun load(url: String) {
        val u = NSURL.URLWithString(url) ?: return
        web?.loadRequest(NSURLRequest.requestWithURL(u))
    }

    fun close() {
        if (closed) return
        closed = true
        web?.stopLoading()
        web?.navigationDelegate = null
        if (hasScript) web?.configuration?.userContentController?.removeScriptMessageHandlerForName(HANDLER)
        nav?.dismissViewControllerAnimated(true, completion = null)
        nav = null
        web = null
    }

    val isClosed: Boolean get() = closed

    fun cookies(domain: String, onResult: (Map<String, String>) -> Unit) {
        val store = web?.configuration?.websiteDataStore?.httpCookieStore ?: return onResult(emptyMap())
        store.getAllCookies { list ->
            val out = LinkedHashMap<String, String>()
            list?.forEach { c ->
                val cookie = c as? NSHTTPCookie ?: return@forEach
                if (cookie.domain.trimStart('.').endsWith(domain)) out[cookie.name] = cookie.value
            }
            onResult(out)
        }
    }

    private class NavDelegate(private val owner: WebLoginSheet) : NSObject(), WKNavigationDelegateProtocol {
        @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGE_ON_OVERRIDE")
        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            if (!owner.closed) owner.onPage(webView.URL?.absoluteString)
        }

        @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGE_ON_OVERRIDE")
        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationAction: WKNavigationAction,
            decisionHandler: (WKNavigationActionPolicy) -> Unit,
        ) {
            val url = decidePolicyForNavigationAction.request.URL?.absoluteString
            val stop = !owner.closed && url != null && owner.onNavigate(url)
            decisionHandler(if (stop) WKNavigationActionPolicy.WKNavigationActionPolicyCancel else WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
    }

    private class MessageHandler(private val owner: WebLoginSheet) : NSObject(), WKScriptMessageHandlerProtocol {
        override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
            val body = didReceiveScriptMessage.body as? String ?: return
            if (!owner.closed) owner.onMessage(body)
        }
    }

    companion object {
        const val HANDLER = "soundcloud"

        fun forgetSites(vararg names: String) {
            val store = WKWebsiteDataStore.defaultDataStore()
            val types = WKWebsiteDataStore.allWebsiteDataTypes()
            store.fetchDataRecordsOfTypes(types) { records ->
                val match = records.orEmpty().filter { r ->
                    val name = (r as? WKWebsiteDataRecord)?.displayName ?: return@filter false
                    names.any { name.contains(it, ignoreCase = true) }
                }
                if (match.isNotEmpty()) store.removeDataOfTypes(types, forDataRecords = match, completionHandler = {})
            }
        }
    }
}

object SpotifyAuth {
    const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
    const val HOME_URL = "https://open.spotify.com/"
    const val DESKTOP_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15"

    private val json = Json { ignoreUnknownKeys = true }

    val HOOK = """
        (function () {
          if (window.__soundcloudHooked) return;
          window.__soundcloudHooked = true;
          function post(o) { try { window.webkit.messageHandlers.soundcloud.postMessage(JSON.stringify(o)); } catch (e) {} }
          function host(u) { try { return new URL(u, location.href); } catch (e) { return null; } }
          function isToken(u) {
            var x = host(u);
            return !!x && x.hostname === 'open.spotify.com' && (x.pathname.indexOf('/api/token') === 0 || x.pathname.indexOf('/get_access_token') === 0);
          }
          function isApi(u) { var x = host(u); return !!x && (x.hostname === 'api.spotify.com' || x.hostname === 'api-partner.spotify.com'); }
          function bearer(h) {
            if (!h) return null;
            var v = null;
            try {
              if (typeof Headers !== 'undefined' && h instanceof Headers) v = h.get('authorization');
              else if (Array.isArray(h)) h.forEach(function (p) { if (String(p[0]).toLowerCase() === 'authorization') v = p[1]; });
              else for (var k in h) if (k.toLowerCase() === 'authorization') v = h[k];
            } catch (e) {}
            v = v ? String(v) : '';
            return v.indexOf('Bearer ') === 0 ? v.substring(7).trim() : null;
          }
          function tokenJson(t) {
            try {
              var o = JSON.parse(t);
              if (o && o.accessToken) post({ kind: 'token', token: o.accessToken, until: o.accessTokenExpirationTimestampMs || 0, anonymous: o.isAnonymous !== false });
            } catch (e) {}
          }
          var f = window.fetch;
          if (f) {
            window.fetch = function (input, init) {
              var url = typeof input === 'string' ? input : (input && input.url) || String(input || '');
              if (isApi(url)) {
                var b = bearer(init && init.headers) || (input && input.headers ? bearer(input.headers) : null);
                if (b) post({ kind: 'bearer', token: b });
              }
              var p = f.apply(this, arguments);
              if (isToken(url)) p.then(function (r) { r.clone().text().then(tokenJson); }).catch(function () {});
              return p;
            };
          }
          var open = XMLHttpRequest.prototype.open;
          var setHeader = XMLHttpRequest.prototype.setRequestHeader;
          var send = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function (m, u) { this.__soundcloudUrl = String(u || ''); return open.apply(this, arguments); };
          XMLHttpRequest.prototype.setRequestHeader = function (k, v) {
            if (String(k).toLowerCase() === 'authorization' && isApi(this.__soundcloudUrl || '') && String(v).indexOf('Bearer ') === 0) {
              post({ kind: 'bearer', token: String(v).substring(7).trim() });
            }
            return setHeader.apply(this, arguments);
          };
          XMLHttpRequest.prototype.send = function () {
            var x = this;
            if (isToken(x.__soundcloudUrl || '')) x.addEventListener('load', function () { tokenJson(x.responseText); });
            return send.apply(this, arguments);
          };
        })();
    """.trimIndent()

    class Token(val value: String, val until: Long, val anonymous: Boolean, val fromHeader: Boolean)

    fun parse(message: String): Token? = runCatching {
        val o = json.parseToJsonElement(message) as? JsonObject ?: return null
        val value = (o["token"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.length > 20 } ?: return null
        val header = (o["kind"] as? JsonPrimitive)?.contentOrNull == "bearer"
        val until = (o["until"] as? JsonPrimitive)?.doubleOrNull?.toLong()?.takeIf { it > nowMillis() } ?: (nowMillis() + 50 * 60_000L)
        val anonymous = !header && ((o["anonymous"] as? JsonPrimitive)?.booleanOrNull ?: true)
        Token(value, until, anonymous, header)
    }.getOrNull()

    suspend fun verify(token: String): Boolean = runCatching {
        Http.execute(
            HttpRequest("https://api.spotify.com/v1/me", headers = listOf("Authorization" to "Bearer $token"), profile = HttpProfile.API, forceNetwork = true),
        ).isSuccessful
    }.getOrDefault(false)

    fun forget() {
        Prefs.changeSpotifyToken("", 0L)
        Prefs.changeSpotifyRefresh("")
        WebLoginSheet.forgetSites("spotify")
    }
}

object GoogleAuth {
    const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2F"
    const val MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1"

    fun signedIn(cookies: Map<String, String>): Boolean = cookies.containsKey("SAPISID") || cookies.containsKey("__Secure-3PAPISID")

    fun forget() = WebLoginSheet.forgetSites("youtube", "google")
}

object YandexAuth {
    private const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
    const val AUTH_URL = "https://oauth.yandex.ru/authorize?response_type=token&client_id=$CLIENT_ID&force_confirm=yes"

    fun forget() = WebLoginSheet.forgetSites("yandex")

    fun tokenFrom(url: String?): String? {
        if (url.isNullOrBlank() || !url.contains("access_token=")) return null
        val part = url.substringAfter('#', "").ifEmpty { url.substringAfter('?', "") }
        return part.split('&')
            .firstOrNull { it.startsWith("access_token=") }
            ?.substringAfter('=')
            ?.let(::percentDecode)
            ?.takeIf { it.length >= 20 }
    }
}

@Composable
fun SpotifyLoginDialog(refresh: Boolean, onDismiss: () -> Unit, onToken: (String, Long) -> Unit) {
    val title = stringResource(Res.string.import_sp_login_title)
    val hint = stringResource(Res.string.import_sp_login_hint)
    val cancel = stringResource(Res.string.cancel)
    val dismiss by rememberUpdatedState(onDismiss)
    val token by rememberUpdatedState(onToken)
    DisposableEffect(refresh) {
        val sheet = WebLoginSheet(title, hint, cancel) { dismiss() }
        var desktop = refresh
        var done = false
        val checked = HashSet<String>()
        fun accept(value: String, until: Long) {
            if (done || sheet.isClosed) return
            done = true
            Logs.add("import", "spotify token received")
            token(value, until)
        }
        sheet.onMessage = { msg ->
            val t = SpotifyAuth.parse(msg)
            when {
                t == null || done -> Unit
                !t.fromHeader && !t.anonymous -> accept(t.value, t.until)
                t.fromHeader && checked.add(t.value) -> AppScope.scope.launch {
                    val ok = SpotifyAuth.verify(t.value)
                    withContext(Dispatchers.Main) { if (ok) accept(t.value, t.until) }
                }
            }
        }
        sheet.onPage = { _ ->
            if (!desktop && !done) {
                sheet.cookies("spotify.com") { all ->
                    if (!desktop && !done && all.containsKey("sp_dc")) {
                        desktop = true
                        Logs.add("import", "spotify signed in, opening web player for the token")
                        sheet.web?.customUserAgent = SpotifyAuth.DESKTOP_UA
                        sheet.load(SpotifyAuth.HOME_URL)
                    }
                }
            }
        }
        sheet.open(
            if (refresh) SpotifyAuth.HOME_URL else SpotifyAuth.LOGIN_URL,
            userAgent = if (refresh) SpotifyAuth.DESKTOP_UA else null,
            script = SpotifyAuth.HOOK,
        )
        onDispose { sheet.close() }
    }
}

@Composable
fun GoogleLoginDialog(onDismiss: () -> Unit, onCookies: (String) -> Unit) {
    val title = stringResource(Res.string.import_g_login_title)
    val hint = stringResource(Res.string.import_g_login_hint)
    val cancel = stringResource(Res.string.cancel)
    val dismiss by rememberUpdatedState(onDismiss)
    val result by rememberUpdatedState(onCookies)
    DisposableEffect(Unit) {
        val sheet = WebLoginSheet(title, hint, cancel) { dismiss() }
        var done = false
        sheet.onPage = { url ->
            val host = url?.let { WebUrl.parse(it)?.host }.orEmpty()
            if (!done && host.endsWith("youtube.com")) {
                sheet.cookies("youtube.com") { all ->
                    if (!done && !sheet.isClosed && GoogleAuth.signedIn(all)) {
                        done = true
                        Logs.add("youtube", "google account linked")
                        result(all.entries.joinToString("; ") { "${it.key}=${it.value}" })
                    }
                }
            }
        }
        sheet.open(GoogleAuth.LOGIN_URL, userAgent = GoogleAuth.MOBILE_UA)
        onDispose { sheet.close() }
    }
}

@Composable
fun YandexLoginDialog(onDismiss: () -> Unit, onToken: (String) -> Unit) {
    val title = stringResource(Res.string.import_ym_login_title)
    val hint = stringResource(Res.string.import_ym_login_hint)
    val cancel = stringResource(Res.string.cancel)
    val dismiss by rememberUpdatedState(onDismiss)
    val result by rememberUpdatedState(onToken)
    DisposableEffect(Unit) {
        val sheet = WebLoginSheet(title, hint, cancel) { dismiss() }
        var done = false
        fun check(url: String?): Boolean {
            if (done) return true
            val t = YandexAuth.tokenFrom(url) ?: return false
            done = true
            Logs.add("import", "yandex token received")
            result(t)
            return true
        }
        sheet.onNavigate = { url -> check(url) }
        sheet.onPage = { url -> check(url) }
        sheet.open(YandexAuth.AUTH_URL)
        onDispose { sheet.close() }
    }
}
