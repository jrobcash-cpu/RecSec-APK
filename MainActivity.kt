package com.jrobcash.recsec

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val permissions: Array<String> get() = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(40, 40, 40, 40) }
        layout.addView(TextView(this).apply { text = "RecSec"; textSize = 34f; gravity = Gravity.CENTER })
        status = TextView(this).apply { text = "Start recording before turning the screen off. A persistent notification indicates recording."; textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 32, 0, 32) }
        layout.addView(status)
        layout.addView(Button(this).apply { text = "START RECORDING"; setOnClickListener { start() } })
        layout.addView(Button(this).apply { text = "STOP RECORDING"; setOnClickListener { stopService(Intent(this@MainActivity, RecordService::class.java)); status.text = "Stopping…" } })
        setContentView(layout)
        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) requestPermissions(permissions, 10)
    }
    private fun start() {
        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissions, 10); status.text = "Grant permissions, then tap Start again"; return
        }
        try {
            startForegroundService(Intent(this, RecordService::class.java))
            status.text = "Starting… Watch the recording notification for status. You may now turn the display off."
        } catch (e: Exception) { status.text = "Could not start: ${e.message}" }
    }
}
