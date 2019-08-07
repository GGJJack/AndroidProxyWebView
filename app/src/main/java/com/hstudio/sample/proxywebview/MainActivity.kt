package com.hstudio.sample.proxywebview

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadUrl()
        btn_refresh.setOnClickListener { proxyLoadUrl() }
    }

    private fun proxyLoadUrl() {
        val result = webView.setProxy(tv_host.text.toString(), tv_port.text.toString().toInt())
        Toast.makeText(this, "Set Proxy result : $result", Toast.LENGTH_SHORT).show()
        loadUrl()
    }

    private fun loadUrl() {
        webView.loadUrl("https://ipecho.net/plain")
    }
}
