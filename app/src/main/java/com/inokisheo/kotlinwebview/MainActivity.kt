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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var frameLayout: FrameLayout
    private var customView: ViewGroup? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private lateinit var fab: FloatingActionButton

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

        // 悬浮按钮（缩小，且大幅度上移）
        fab = FloatingActionButton(this)
        fab.setImageResource(android.R.drawable.ic_menu_revert)
        fab.size = FloatingActionButton.SIZE_MINI
        val density = resources.displayMetrics.density
        val fabSize = (40 * density).toInt() // 40dp
        
        // 计算上移3个fab高度（图片大小为fabSize），加上原本的64dp
        val baseBottomMargin = (64 * density).toInt()
        val moveUpBy = fabSize * 3
        val fabParams = FrameLayout.LayoutParams(
            fabSize,
            fabSize
        )
        fabParams.gravity = Gravity.BOTTOM or Gravity.END
        fabParams.setMargins(
            0, 0,
            (16 * density).toInt(), // 右侧间距16dp
            baseBottomMargin + moveUpBy // 底部间距增大，实现上移
        )
        frameLayout.addView(fab, fabParams)

        setContentView(frameLayout)

        fab.setOnClickListener {
            webView.loadUrl("https://cs.yuemlk.xyz")
        }

        // WebView 设置
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            val defaultUA = WebSettings.getDefaultUserAgent(this@MainActivity)
            userAgentString = "$defaultUA YuemAPP"
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
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
                fab.visibility = View.GONE
            }

            override fun onHideCustomView() {
                val decor = window.decorView as ViewGroup
                customView?.let {
                    decor.removeView(it)
                    customView = null
                }
                webView.visibility = View.VISIBLE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                customViewCallback?.onCustomViewHidden()
                fab.visibility = View.VISIBLE
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
