package com.hstudio.proxywebview

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

class ProxyWebView : WebView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    fun setProxy(host: String, port: Int): Boolean {
        return WebViewProxyUtil.setProxy(this, host, port)
    }
}