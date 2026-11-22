package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.scd.android.res.Res
import com.scd.android.res.cancel
import com.scd.android.res.import_ym_login_hint
import com.scd.android.res.import_ym_login_title
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectMake
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


    var web: WKWebView? = null
        private set
    private var nav: UINavigationController? = null
    private var closed = false
    private val navDelegate = NavDelegate(this)

    fun open(url: String) {
        val root = IosFiles.root ?: return onCancel()
        val config = WKWebViewConfiguration()
        val view = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config)
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
        nav?.dismissViewControllerAnimated(true, completion = null)
        nav = null
        web = null
    }


    private class NavDelegate(private val owner: WebLoginSheet) : NSObject(), WKNavigationDelegateProtocol {
        @kotlinx.cinterop.ObjCSignatureOverride
        @Suppress("PARAMETER_NAME_CHANGE_ON_OVERRIDE")
        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            if (!owner.closed) owner.onPage(webView.URL?.absoluteString)
        }

        @kotlinx.cinterop.ObjCSignatureOverride
        @Suppress("PARAMETER_NAME_CHANGE_ON_OVERRIDE")
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

    companion object {

        fun forgetSites(vararg names: String) {
            val store = WKWebsiteDataStore.defaultDataStore()
            val types = WKWebsiteDataStore.allWebsiteDataTypes()
            store.fetchDataRecordsOfTypes(types) { records ->
                val match = records.orEmpty().filter { r ->
                    val name = (r as? WKWebsiteDataRecord)?.displayName ?: return@filter false
                    names.any { name.equals(it, ignoreCase = true) }
                }
                if (match.isNotEmpty()) store.removeDataOfTypes(types, forDataRecords = match, completionHandler = {})
            }
        }
    }
}

object YandexAuth {
    private const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
    const val AUTH_URL = "https://oauth.yandex.ru/authorize?response_type=token&client_id=$CLIENT_ID&force_confirm=yes"

    fun forget() = WebLoginSheet.forgetSites("yandex.ru", "yandex.com")

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
            val parsed = url?.let(WebUrl::parse) ?: return false
            if (parsed.scheme != "https" || parsed.host !in setOf("oauth.yandex.ru", "oauth.yandex.com") || parsed.path != "/verification_code") return false
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
