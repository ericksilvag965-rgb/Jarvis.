package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val title = TextView(this).apply { text = "J.A.R.V.I.S."; textSize = 30f; gravity = Gravity.CENTER }
        val btn = Button(this).apply { text = "Ativar Jarvis"; textSize = 20f; setOnClickListener { start() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 48, 48, 48)
            addView(title); addView(btn)
        })
    }

    private fun start() {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(need.toTypedArray(), 1); return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, JarvisService::class.java))
        finish()
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<String>, r: IntArray) {
        if (r.isNotEmpty() && r.all { it == PackageManager.PERMISSION_GRANTED }) start()
    }
}
