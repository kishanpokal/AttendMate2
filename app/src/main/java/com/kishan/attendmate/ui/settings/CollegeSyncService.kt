package com.kishan.attendmate.ui.settings

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import com.kishan.attendmate.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class CollegeSyncService : Service() {

    private var webView: WebView? = null
    private val notificationId = 1001
    private val channelId = "college_sync_channel"
    private val prefs by lazy { getSharedPreferences("CollegeSyncPrefs", Context.MODE_PRIVATE) }
    private var currentPhase = ScrapePhase.IDLE

    companion object {
        const val ACTION_STOP = "STOP_SYNC"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(notificationId, buildNotification("Starting College Sync..."))

        val email = intent?.getStringExtra("EMAIL") ?: ""
        val password = intent?.getStringExtra("PASSWORD") ?: ""

        startScraping(email, password)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "College Sync", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, channelId)
        .setContentTitle("AttendMate · Syncing")
        .setContentText(text)
        // Fixed: Using a default Android sync icon so it compiles immediately
        .setSmallIcon(android.R.drawable.ic_popup_sync)
        .setOngoing(true) // This works again because the line above is fixed!
        .setProgress(100, 0, true)
        .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(notificationId, buildNotification(text))
        prefs.edit().putString("currentSyncStatus", text).apply()
    }

    private fun startScraping(email: String, password: String) {
        currentPhase = ScrapePhase.LOGIN
        ScrapingEventBus.tryEmit(ScrapingEvent.SetPhase(currentPhase))

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

            // MUST be "Android" to match JS scripts, not "AndroidSync"
            addJavascriptInterface(
                ScraperBridge(
                    progressCb = { msg ->
                        updateNotification(msg)
                        parseAndEmitEvent(msg)
                    },
                    errorCb = { err ->
                        updateNotification("Error: $err")
                        ScrapingEventBus.tryEmit(ScrapingEvent.SetPhase(ScrapePhase.IDLE))
                        stopSelf()
                    },
                    dataCb = { json ->
                        saveScrapedData(this@CollegeSyncService, parseJsonToRecords(json))
                        ScrapingEventBus.tryEmit(ScrapingEvent.SetPhase(ScrapePhase.IDLE))
                        updateNotification("Sync complete!")
                        stopSelf()
                    },
                    loginSuccessCb = {
                        if (currentPhase == ScrapePhase.LOGIN_INJECTED) {
                            currentPhase = ScrapePhase.SCRAPING
                            ScrapingEventBus.tryEmit(ScrapingEvent.SetPhase(currentPhase))
                            CoroutineScope(Dispatchers.Main).launch {
                                loadUrl("https://attendence-system-1910.vercel.app/students/current/attendances")
                            }
                        }
                    }
                ), "Android"
            )

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    when {
                        currentPhase == ScrapePhase.LOGIN && url.contains("/users/login") -> {
                            currentPhase = ScrapePhase.LOGIN_INJECTED
                            ScrapingEventBus.tryEmit(ScrapingEvent.SetPhase(currentPhase))
                            val safeEmail = email.replace("\\", "\\\\").replace("'", "\\'")
                            val safePassword = password.replace("\\", "\\\\").replace("'", "\\'")
                            view.evaluateJavascript(ScraperScripts.buildLoginScript(safeEmail, safePassword), null)
                        }
                        currentPhase == ScrapePhase.SCRAPING && url.contains("/students/current/attendances") -> {
                            currentPhase = ScrapePhase.EXTRACTING
                            ScrapingEventBus.tryEmit(ScrapingEvent.SetPhase(currentPhase))
                            view.evaluateJavascript(ScraperScripts.buildScrapingScript(), null)
                        }
                    }
                }
            }

            loadUrl("https://attendence-system-1910.vercel.app/users/login")
        }
    }

    private fun parseAndEmitEvent(msg: String) {
        Regex("Processing:\\s*([^(]+)\\s*\\((\\d+)/(\\d+)\\)").find(msg)?.let {
            val name = it.groupValues[1].trim()
            ScrapingEventBus.tryEmit(ScrapingEvent.SpawnSubject(name))
            ScrapingEventBus.tryEmit(ScrapingEvent.StartExtraction(name))
            ScrapingEventBus.tryEmit(ScrapingEvent.UpdateProgress(0f, "Targeting $name..."))
        }

        Regex("(.*?)\\s*—\\s*page\\s*\\d+\\s*\\((\\d+)/(\\d+)\\)").find(msg)?.let {
            val name = it.groupValues[1].trim()
            val cur = it.groupValues[2].toFloatOrNull() ?: 0f
            val tot = it.groupValues[3].toFloatOrNull() ?: 1f
            val pct = ((cur / tot) * 100).coerceIn(0f, 100f)
            ScrapingEventBus.tryEmit(ScrapingEvent.UpdateProgress(pct, "Extracting $name: ${cur.toInt()}/${tot.toInt()}"))
        }

        Regex("(.*?):\\s*Scraped").find(msg)?.let {
            val name = it.groupValues[1].trim()
            ScrapingEventBus.tryEmit(ScrapingEvent.FinishSubject(name))
            ScrapingEventBus.tryEmit(ScrapingEvent.UpdateProgress(100f, "$name COMPLETE"))
        }

        if (msg.contains("No attendance data") || msg.contains("Skipping")) {
            Regex("(.*?):\\s*(No attendance|Skipping)").find(msg)?.let {
                val name = it.groupValues[1].trim()
                ScrapingEventBus.tryEmit(ScrapingEvent.FinishSubject(name))
            }
        }
    }

    private fun parseJsonToRecords(json: String): List<CollegeAttendanceRecord> {
        val list = mutableListOf<CollegeAttendanceRecord>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    CollegeAttendanceRecord(
                        subject = o.optString("subject", ""),
                        date = o.optString("date", ""),
                        fromTime = o.optString("fromTime", ""),
                        toTime = o.optString("toTime", ""),
                        topic = o.optString("topic", ""),
                        status = o.optString("status", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("CollegeSync", "Failed to parse service payload", e)
        }
        return list
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}