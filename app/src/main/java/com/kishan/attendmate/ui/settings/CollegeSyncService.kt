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

        // Pre-emit configured target subjects to prime the 3D orbit
        val syncPrefs = CollegeSyncPreferences(this)
        val initialSubjects = syncPrefs.targetSubjects?.toList() ?: emptyList()
        if (initialSubjects.isNotEmpty()) {
            ScrapingEventBus.tryEmit(ScrapingEvent.SubjectsFetched(initialSubjects))
            initialSubjects.forEach { name ->
                ScrapingEventBus.tryEmit(ScrapingEvent.SpawnSubject(name))
            }
        }

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
                        if (currentPhase == ScrapePhase.LOGIN || currentPhase == ScrapePhase.LOGIN_INJECTED) {
                            currentPhase = ScrapePhase.SCRAPING
                            ScrapingEventBus.tryEmit(ScrapingEvent.SetPhase(currentPhase))
                            CoroutineScope(Dispatchers.Main).launch {
                                webView?.loadUrl("https://attendence-system-1910.vercel.app/students/current/attendances")
                            }
                        }
                    }
                ), "Android"
            )

            webViewClient = object : WebViewClient() {
                private fun checkAndInject(view: WebView, url: String) {
                    Log.d("CollegeSyncService", "checkAndInject url=$url currentPhase=$currentPhase")
                    when {
                        (currentPhase == ScrapePhase.LOGIN || currentPhase == ScrapePhase.LOGIN_INJECTED) && url.contains("/users/login") -> {
                            currentPhase = ScrapePhase.LOGIN_INJECTED
                            ScrapingEventBus.tryEmit(ScrapingEvent.SetPhase(currentPhase))
                            val safeEmail = email.replace("\\", "\\\\").replace("'", "\\'")
                            val safePassword = password.replace("\\", "\\\\").replace("'", "\\'")
                            view.evaluateJavascript(ScraperScripts.buildLoginScript(safeEmail, safePassword), null)
                        }
                        (currentPhase == ScrapePhase.SCRAPING || currentPhase == ScrapePhase.LOGIN || currentPhase == ScrapePhase.LOGIN_INJECTED) && url.contains("/students/current/attendances") -> {
                            currentPhase = ScrapePhase.EXTRACTING
                            ScrapingEventBus.tryEmit(ScrapePhase.EXTRACTING.let { ScrapingEvent.SetPhase(it) })
                            val prefsHelper = CollegeSyncPreferences(this@CollegeSyncService)
                            val sem = prefsHelper.selectedSemester ?: "Sem9"
                            val subjects = prefsHelper.targetSubjects?.toList() ?: emptyList()
                            if (subjects.isNotEmpty()) {
                                ScrapingEventBus.tryEmit(ScrapingEvent.SubjectsFetched(subjects))
                            }
                            view.evaluateJavascript(ScraperScripts.buildScrapingScript(sem, subjects), null)
                        }
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    checkAndInject(view, url)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    checkAndInject(view, url)
                }
            }

            loadUrl("https://attendence-system-1910.vercel.app/users/login")
        }
    }

    private fun parseAndEmitEvent(msg: String) {
        Regex("Found\\s*(\\d+)\\s*subjects:\\s*(.*)").find(msg)?.let {
            val subjectsStr = it.groupValues[2]
            val list = subjectsStr.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
            if (list.isNotEmpty()) {
                ScrapingEventBus.tryEmit(ScrapingEvent.SubjectsFetched(list))
                list.forEach { s -> ScrapingEventBus.tryEmit(ScrapingEvent.SpawnSubject(s)) }
            }
        }

        Regex("Processing:\\s*([^(]+)\\s*\\((\\d+)/(\\d+)\\)").find(msg)?.let {
            val name = it.groupValues[1].trim()
            ScrapingEventBus.tryEmit(ScrapingEvent.SpawnSubject(name))
            ScrapingEventBus.tryEmit(ScrapingEvent.StartExtraction(name))
            ScrapingEventBus.tryEmit(ScrapingEvent.UpdateProgress(0f, "Targeting $name..."))
        }

        Regex("(.*?):\\s*Targeting\\s*(\\d+|all)\\s*records").find(msg)?.let {
            val name = it.groupValues[1].trim()
            ScrapingEventBus.tryEmit(ScrapingEvent.StartExtraction(name))
        }

        Regex("(.*?)\\s*—\\s*page\\s*\\d+\\s*\\((\\d+)/(\\d+)\\)").find(msg)?.let {
            val name = it.groupValues[1].trim()
            val cur = it.groupValues[2].toFloatOrNull() ?: 0f
            val tot = it.groupValues[3].toFloatOrNull() ?: 1f
            val pct = if (tot > 0f) ((cur / tot) * 100).coerceIn(0f, 100f) else 0f
            ScrapingEventBus.tryEmit(ScrapingEvent.UpdateProgress(pct, "Extracting $name: ${cur.toInt()}/${tot.toInt()}"))
            ScrapingEventBus.tryEmit(ScrapingEvent.RecordExtracted(cur.toInt()))
        }

        Regex("(.*?):\\s*Scraped\\s*(\\d+)").find(msg)?.let {
            val name = it.groupValues[1].trim()
            val count = it.groupValues[2].toIntOrNull() ?: 0
            ScrapingEventBus.tryEmit(ScrapingEvent.RecordExtracted(count))
            ScrapingEventBus.tryEmit(ScrapingEvent.FinishSubject(name))
            ScrapingEventBus.tryEmit(ScrapingEvent.UpdateProgress(100f, "$name COMPLETE"))
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
                ScrapingEventBus.tryEmit(ScrapingEvent.UpdateProgress(100f, "$name: Skipped"))
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