package com.hstudio.proxywebview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Proxy
import android.os.Build
import android.util.ArrayMap
import android.util.Log
import android.webkit.WebView
import org.apache.http.HttpHost

import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import android.os.Parcelable
import android.annotation.TargetApi

// 1 : https://stackoverflow.com/questions/4488338/webview-android-proxy
// 2 : https://github.com/Psiphon-Labs/psiphon-tunnel-core

object WebViewProxyUtil {
    private val LOG_TAG = WebViewProxyUtil::class.java.simpleName

    fun setProxy(webView: WebView, host: String, port: Int, applicationClassName: String = ""): Boolean {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB_MR2) {
            setProxyUpToHC(webView, host, port)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            setProxyICS(webView, host, port)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setProxyJB(webView, host, port)
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            setProxyKKPlus(webView, host, port, applicationClassName)
        } else {
            setWebkitProxyLollipop(webView.context.applicationContext, host, port)
        }
    }

    /**
     * Set Proxy for Android 3.2 and below.
     */
    private fun setProxyUpToHC(webview: WebView, host: String, port: Int): Boolean {
        Log.d(LOG_TAG, "Setting proxy with <= 3.2 API.")

        val proxyServer = HttpHost(host, port)
        // Getting network
        var networkClass: Class<*>? = null
        var network: Any? = null
        try {
            networkClass = Class.forName("android.webkit.Network")
            if (networkClass == null) {
                Log.e(LOG_TAG, "failed to get class for android.webkit.Network")
                return false
            }
            val getInstanceMethod = networkClass.getMethod("getInstance", Context::class.java)
            if (getInstanceMethod == null) {
                Log.e(LOG_TAG, "failed to get getInstance method")
            }
            network = getInstanceMethod.invoke(networkClass, *arrayOf<Any>(webview.context))
        } catch (ex: Exception) {
            Log.e(LOG_TAG, "error getting network: $ex")
            return false
        }

        if (network == null) {
            Log.e(LOG_TAG, "error getting network: network is null")
            return false
        }
        var requestQueue: Any? = null
        try {
            val requestQueueField = networkClass
                .getDeclaredField("mRequestQueue")
            requestQueue = getFieldValueSafely(requestQueueField, network)
        } catch (ex: Exception) {
            Log.e(LOG_TAG, "error getting field value")
            return false
        }

        if (requestQueue == null) {
            Log.e(LOG_TAG, "Request queue is null")
            return false
        }
        var proxyHostField: Field? = null
        try {
            val requestQueueClass = Class.forName("android.net.http.RequestQueue")
            proxyHostField = requestQueueClass
                .getDeclaredField("mProxyHost")
        } catch (ex: Exception) {
            Log.e(LOG_TAG, "error getting proxy host field")
            return false
        }

        val temp = proxyHostField!!.isAccessible
        try {
            proxyHostField.isAccessible = true
            proxyHostField.set(requestQueue, proxyServer)
        } catch (ex: Exception) {
            Log.e(LOG_TAG, "error setting proxy host")
        } finally {
            proxyHostField.isAccessible = temp
        }

        Log.d(LOG_TAG, "Setting proxy with <= 3.2 API successful!")
        return true
    }

    private fun setProxyICS(webview: WebView, host: String, port: Int): Boolean {
        try {
            Log.d(LOG_TAG, "Setting proxy with 4.0 API.")

            val jwcjb = Class.forName("android.webkit.JWebCoreJavaBridge")
            val params = arrayOfNulls<Class<*>>(1)
            params[0] = Class.forName("android.net.ProxyProperties")
            val updateProxyInstance = jwcjb.getDeclaredMethod("updateProxy", *params)

            val wv = Class.forName("android.webkit.WebView")
            val mWebViewCoreField = wv.getDeclaredField("mWebViewCore")
            val mWebViewCoreFieldInstance = getFieldValueSafely(mWebViewCoreField, webview)

            val wvc = Class.forName("android.webkit.WebViewCore")
            val mBrowserFrameField = wvc.getDeclaredField("mBrowserFrame")
            val mBrowserFrame = getFieldValueSafely(mBrowserFrameField, mWebViewCoreFieldInstance)

            val bf = Class.forName("android.webkit.BrowserFrame")
            val sJavaBridgeField = bf.getDeclaredField("sJavaBridge")
            val sJavaBridge = getFieldValueSafely(sJavaBridgeField, mBrowserFrame)

            val ppclass = Class.forName("android.net.ProxyProperties")
            val pparams = arrayOfNulls<Class<*>>(3)
            pparams[0] = String::class.java
            pparams[1] = Int::class.javaPrimitiveType
            pparams[2] = String::class.java
            val ppcont = ppclass.getConstructor(*pparams)

            updateProxyInstance.invoke(sJavaBridge, ppcont.newInstance(host, port, null))

            Log.d(LOG_TAG, "Setting proxy with 4.0 API successful!")
            return true
        } catch (ex: Exception) {
            Log.e(LOG_TAG, "failed to set HTTP proxy: $ex")
            return false
        }

    }

    /**
     * Set Proxy for Android 4.1 - 4.3.
     */
    private fun setProxyJB(webview: WebView, host: String, port: Int): Boolean {
        Log.d(LOG_TAG, "Setting proxy with 4.1 - 4.3 API.")

        try {
            val wvcClass = Class.forName("android.webkit.WebViewClassic")
            val wvParams = arrayOfNulls<Class<*>>(1)
            wvParams[0] = Class.forName("android.webkit.WebView")
            val fromWebView = wvcClass.getDeclaredMethod("fromWebView", *wvParams)
            val webViewClassic = fromWebView.invoke(null, webview)

            val wv = Class.forName("android.webkit.WebViewClassic")
            val mWebViewCoreField = wv.getDeclaredField("mWebViewCore")
            val mWebViewCoreFieldInstance = getFieldValueSafely(mWebViewCoreField, webViewClassic)

            val wvc = Class.forName("android.webkit.WebViewCore")
            val mBrowserFrameField = wvc.getDeclaredField("mBrowserFrame")
            val mBrowserFrame = getFieldValueSafely(mBrowserFrameField, mWebViewCoreFieldInstance)

            val bf = Class.forName("android.webkit.BrowserFrame")
            val sJavaBridgeField = bf.getDeclaredField("sJavaBridge")
            val sJavaBridge = getFieldValueSafely(sJavaBridgeField, mBrowserFrame)

            val ppclass = Class.forName("android.net.ProxyProperties")
            val pparams = arrayOfNulls<Class<*>>(3)
            pparams[0] = String::class.java
            pparams[1] = Int::class.javaPrimitiveType
            pparams[2] = String::class.java
            val ppcont = ppclass.getConstructor(*pparams)

            val jwcjb = Class.forName("android.webkit.JWebCoreJavaBridge")
            val params = arrayOfNulls<Class<*>>(1)
            params[0] = Class.forName("android.net.ProxyProperties")
            val updateProxyInstance = jwcjb.getDeclaredMethod("updateProxy", *params)

            updateProxyInstance.invoke(sJavaBridge, ppcont.newInstance(host, port, null))
        } catch (ex: Exception) {
            Log.e(LOG_TAG, "Setting proxy with >= 4.1 API failed with error: " + ex.message)
            return false
        }

        Log.d(LOG_TAG, "Setting proxy with 4.1 - 4.3 API successful!")
        return true
    }

    // from https://stackoverflow.com/questions/19979578/android-webview-set-proxy-programatically-kitkat
    @SuppressLint("NewApi")
    private fun setProxyKKPlus(webView: WebView, host: String, port: Int, applicationClassName: String): Boolean {
        Log.d(LOG_TAG, "Setting proxy with >= 4.4 API.")

        val appContext = webView.context.applicationContext
        System.setProperty("http.proxyHost", host)
        System.setProperty("http.proxyPort", port.toString() + "")
        System.setProperty("https.proxyHost", host)
        System.setProperty("https.proxyPort", port.toString() + "")
        try {
            val applictionCls = Class.forName(applicationClassName)
            val loadedApkField = applictionCls.getField("mLoadedApk")
            loadedApkField.isAccessible = true
            val loadedApk = loadedApkField.get(appContext)
            val loadedApkCls = Class.forName("android.app.LoadedApk")
            val receiversField = loadedApkCls.getDeclaredField("mReceivers")
            receiversField.isAccessible = true
            val receivers = receiversField.get(loadedApk) as ArrayMap<*, *>
            for (receiverMap in receivers.values) {
                for (rec in (receiverMap as ArrayMap<*, *>).keys) {
                    val clazz = rec.javaClass
                    if (clazz.name.contains("ProxyChangeListener")) {
                        val onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context::class.java, Intent::class.java)
                        val intent = Intent(Proxy.PROXY_CHANGE_ACTION)

                        onReceiveMethod.invoke(rec, appContext, intent)
                    }
                }
            }

            Log.d(LOG_TAG, "Setting proxy with >= 4.4 API successful!")
            return true
        } catch (e: ClassNotFoundException) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val exceptionAsString = sw.toString()
            Log.v(LOG_TAG, e.message)
            Log.v(LOG_TAG, exceptionAsString)
        } catch (e: NoSuchFieldException) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val exceptionAsString = sw.toString()
            Log.v(LOG_TAG, e.message)
            Log.v(LOG_TAG, exceptionAsString)
        } catch (e: IllegalAccessException) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val exceptionAsString = sw.toString()
            Log.v(LOG_TAG, e.message)
            Log.v(LOG_TAG, exceptionAsString)
        } catch (e: IllegalArgumentException) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val exceptionAsString = sw.toString()
            Log.v(LOG_TAG, e.message)
            Log.v(LOG_TAG, exceptionAsString)
        } catch (e: NoSuchMethodException) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val exceptionAsString = sw.toString()
            Log.v(LOG_TAG, e.message)
            Log.v(LOG_TAG, exceptionAsString)
        } catch (e: InvocationTargetException) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val exceptionAsString = sw.toString()
            Log.v(LOG_TAG, e.message)
            Log.v(LOG_TAG, exceptionAsString)
        }

        return false
    }

    // http://stackanswers.com/questions/25272393/android-webview-set-proxy-programmatically-on-android-l
    @TargetApi(Build.VERSION_CODES.KITKAT) // for android.util.ArrayMap methods
    private fun setWebkitProxyLollipop(appContext: Context, host: String, port: Int): Boolean {
        System.setProperty("http.proxyHost", host)
        System.setProperty("http.proxyPort", port.toString() + "")
        System.setProperty("https.proxyHost", host)
        System.setProperty("https.proxyPort", port.toString() + "")
        try {
            for (receiver in getCurrentReceiversSet(appContext)) {
                val receiverClass = receiver.javaClass
                val receiverName = receiverClass.getCanonicalName()
                if (receiverName != null && receiverName!!.contains("ProxyChangeListener")) {
                    val onReceiveMethod = receiverClass.getDeclaredMethod("onReceive", Context::class.java, Intent::class.java)
                    val intent = Intent(Proxy.PROXY_CHANGE_ACTION)

                    val CLASS_NAME = "android.net.ProxyInfo"
                    val proxyInfoClass = Class.forName(CLASS_NAME)
                    val constructor = proxyInfoClass.getConstructor(String::class.java, Integer.TYPE, String::class.java)
                    constructor.isAccessible = true
                    val proxyInfo = constructor.newInstance(host, port, null)
                    intent.putExtra("android.intent.extra.PROXY_INFO", proxyInfo as Parcelable)

                    try {
                        onReceiveMethod.invoke(receiver, appContext, intent)
                    } catch (e: InvocationTargetException) {
                        // This receiver may throw on an unexpected intent, continue to the next one
                    }

                }
            }
            return true
        } catch (e: ClassNotFoundException) {
        } catch (e: IllegalAccessException) {
        } catch (e: NoSuchMethodException) {
        } catch (e: InvocationTargetException) {
        } catch (e: InstantiationException) {
        }

        return false
    }

    private fun getCurrentReceiversSet(ctx: Context): List<Any> {
        val appContext = ctx.applicationContext
        val receiversList = ArrayList<Any>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return receiversList
        }

        try {
            val applicationClass = Class.forName("android.app.Application")
            val mLoadedApkField = applicationClass.getDeclaredField("mLoadedApk")
            mLoadedApkField.isAccessible = true
            val mloadedApk = mLoadedApkField.get(appContext)
            val loadedApkClass = Class.forName("android.app.LoadedApk")
            val mReceiversField = loadedApkClass.getDeclaredField("mReceivers")
            mReceiversField.isAccessible = true
            val receivers = mReceiversField.get(mloadedApk) as ArrayMap<*, *>
            for (receiverMap in receivers.values) {
                for (receiver in (receiverMap as ArrayMap<*, *>).keys) {
                    if (receiver == null) {
                        continue
                    }
                    receiversList.add(receiver)
                }
            }
        } catch (e: ClassNotFoundException) {
        } catch (e: NoSuchFieldException) {
        } catch (e: IllegalAccessException) {
        }

        return receiversList
    }

    @Throws(IllegalArgumentException::class, IllegalAccessException::class)
    private fun getFieldValueSafely(field: Field, classInstance: Any): Any {
        val oldAccessibleValue = field.isAccessible
        field.isAccessible = true
        val result = field.get(classInstance)
        field.isAccessible = oldAccessibleValue
        return result
    }
}
