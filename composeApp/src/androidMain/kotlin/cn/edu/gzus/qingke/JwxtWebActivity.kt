package cn.edu.gzus.qingke

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import cn.edu.gzus.qingke.data.cookieRecords
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

class JwxtWebActivity : ComponentActivity() {
    private var webView: WebView? = null

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val startUrl = intent.getStringExtra(EXTRA_URL) ?: "https://jwxt.gzus.edu.cn/jwglxt/xtgl/login_slogin.html"
        val title = mutableStateOf(intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { titleFor(startUrl) })
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieRecords().forEach { row ->
            val host = row.domain.trimStart('.').ifBlank { hostOf(startUrl) }
            val url = "https://$host/"
            val piece = "${row.name}=${row.value}; Domain=$host; Path=${row.path.ifBlank { "/" }}"
            cookieManager.setCookie(url, piece)
        }
        cookieManager.flush()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val view = webView
                    if (view != null && view.canGoBack()) view.goBack() else finish()
                }
            },
        )
        setContent {
            QingkeTheme {
                Scaffold(
                    containerColor = MiuixTheme.colorScheme.surface,
                    topBar = {
                        SmallTopAppBar(
                            title = title.value,
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(MiuixIcons.Back, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                                }
                            },
                        )
                    },
                ) { padding ->
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webView = this
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 15; Qingke) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        view?.title?.let { if (it.isNotBlank()) title.value = it }
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                loadUrl(startUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}

private fun titleFor(url: String): String = when {
    url.contains("ecarduser.gzus.edu.cn") -> "一卡通"
    url.contains("ehall.gzus.edu.cn") -> "办事大厅"
    url.contains("cas.gzus.edu.cn") || url.contains("sso.gzus.edu.cn") -> "统一身份认证"
    else -> "正方教务"
}

private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore("/").substringBefore(":")
