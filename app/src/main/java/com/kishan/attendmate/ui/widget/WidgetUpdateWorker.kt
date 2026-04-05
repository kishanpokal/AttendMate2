package com.kishan.attendmate.ui.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class WidgetUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
        CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val (total, attended) = fetchTotalAttendance()
            val insight = AttendanceMathHelper.getInsight(total, attended)
            
            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = manager.getGlanceIds(TotalAttendanceWidget::class.java)

            for (glanceId in glanceIds) {
                // Save explicitly to the PreferencesGlanceStateDefinition via updateAppWidgetState
                updateAppWidgetState(applicationContext, glanceId) { prefs ->
                    prefs[TotalAttendanceWidget.TOTAL_CLASSES_KEY] = total
                    prefs[TotalAttendanceWidget.ATTENDED_CLASSES_KEY] = attended
                    
                    val insightMessage = when (insight) {
                        is AttendanceMathHelper.MathInsight.NoData -> "No Data"
                        is AttendanceMathHelper.MathInsight.OnTrack -> "Can skip ${insight.canSkip} classes"
                        is AttendanceMathHelper.MathInsight.NeedsAttention -> "Attend next ${insight.mustAttendNext} classes"
                    }
                    prefs[TotalAttendanceWidget.INSIGHT_MSG_KEY] = insightMessage
                    prefs[TotalAttendanceWidget.IS_ON_TRACK_KEY] = insight is AttendanceMathHelper.MathInsight.OnTrack
                }
            }

            // Force all widgets to re-render using the new local state
            TotalAttendanceWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("WidgetUpdateWorker", "Error updating widget", e)
            Result.retry()
        }
    }

    private suspend fun fetchTotalAttendance(): Pair<Int, Int> {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return Pair(0, 0)

        var total = 0
        var attended = 0

        try {
            val subjects = db.collection("users")
                .document(userId)
                .collection("subjects")
                .get() 
                .await()

            for (subjectDoc in subjects.documents) {
                val attendanceSnapshot = subjectDoc.reference
                    .collection("attendance")
                    .get()
                    .await()

                for (doc in attendanceSnapshot.documents) {
                    val status = doc.getString("status")?.uppercase() ?: "ABSENT"
                    if (status == "PRESENT" || status == "ABSENT") {
                        total++
                        if (status == "PRESENT") attended++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdateWorker", "Error fetching data for widget", e)
        }

        return Pair(total, attended)
    }
}
