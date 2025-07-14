package com.inokisheo.kotlinwebview

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var frameLayout: FrameLayout
    private var customView: ViewGroup? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建 WebView 和 ProgressBar
        webView = WebView(this)
        progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        val progressBarParams = FrameLayout.LayoutParams(120, 120)
        progressBarParams.gravity = Gravity.CENTER

        // 父布局
        frameLayout = FrameLayout(this)
        frameLayout.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        frameLayout.addView(progressBar, progressBarParams)
        setContentView(frameLayout)

        // WebView 设置
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // 标准UA+YuemAPP标识
            val defaultUA = WebSettings.getDefaultUserAgent(this@MainActivity)
            userAgentString = "$defaultUA YuemAPP"
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        // 硬件加速（通常默认开启，保险起见加上）
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                // 启动30秒超时
                timeoutRunnable?.let { handler.removeCallbacks(it) }
                timeoutRunnable = Runnable {
                    progressBar.visibility = View.GONE
                    Snackbar.make(frameLayout, "加载超时，请检查网络", Snackbar.LENGTH_INDEFINITE)
                        .setAction("重试") {
                            progressBar.visibility = View.VISIBLE
                            webView.reload()
                        }.show()
                }.also { handler.postDelayed(it, 30000) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                timeoutRunnable?.let { handler.removeCallbacks(it) }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                progressBar.visibility = View.GONE
                timeoutRunnable?.let { handler.removeCallbacks(it) }
                Snackbar.make(frameLayout, "页面加载错误", Snackbar.LENGTH_LONG)
                    .setAction("重试") {
                        progressBar.visibility = View.VISIBLE
                        webView.reload()
                    }.show()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                progressBar.visibility = View.GONE
                timeoutRunnable?.let { handler.removeCallbacks(it) }
                if (request?.isForMainFrame == true) {
                    Snackbar.make(frameLayout, "页面加载错误", Snackbar.LENGTH_LONG)
                        .setAction("重试") {
                            progressBar.visibility = View.VISIBLE
                            webView.reload()
                        }.show()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            private var fullScreenContainer: FrameLayout? = null
            private val FULLSCREEN_LAYOUT_PARAMS = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                val decor = window.decorView as ViewGroup
                fullScreenContainer = FrameLayout(this@MainActivity)
                fullScreenContainer?.addView(view, FULLSCREEN_LAYOUT_PARAMS)
                decor.addView(fullScreenContainer, FULLSCREEN_LAYOUT_PARAMS)
                customView = fullScreenContainer
                customViewCallback = callback
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                val decor = window.decorView as ViewGroup
                customView?.let {
                    decor.removeView(it)
                    customView = null
                }
                webView.visibility = View.VISIBLE
                // 恢复自动旋转
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                customViewCallback?.onCustomViewHidden()
            }
        }

        webView.loadUrl("https://cs.yuemlk.xyz")
    }

    override fun onBackPressed() {
        if (customView != null) {
            (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        webView.destroy()
        super.onDestroy()
    }
}
