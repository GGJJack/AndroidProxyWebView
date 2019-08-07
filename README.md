[![](https://jitpack.io/v/ggjjack/AndroidProxyWebView.svg)](https://jitpack.io/#ggjjack/AndroidProxyWebView)

# ProxyWebView
Library to help webviews use proxies

Download
--------
This library use gradle

Project gradle
```
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```

App gradle
```
dependencies {
  ...
  implementation 'com.github.ggjjack:AndroidProxyWebView:1.0.0'
  ...
}
```

Use it
--------
How to use this library

WebViewType
```
  boolean result = proxyWebView.setProxy(PROXY_HOST, PROXY_PORT);
```

Use Util
```
  boolean result = WebViewProxyUtil.setProxy(WebView, PROXY_HOST, PROXY_PORT)
```
