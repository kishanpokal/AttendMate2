package com.kishan.attendmate.ui.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.kishan.attendmate.MainActivity
import kotlinx.coroutines.tasks.await
import java.util.Locale

class TotalAttendanceWidget : GlanceAppWidget() {

    data class AttendanceData(
        val totalClasses: Int,
        val attendedClasses: Int
    ) {
        val percentage: Float
            get() = if (totalClasses > 0) (attendedClasses.toFloat() / totalClasses) * 100 else 0f
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = fetchTotalAttendance()
        provideContent {
            GlanceTheme {
                WidgetUI(data)
            }
        }
    }

    private suspend fun fetchTotalAttendance(): AttendanceData {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return AttendanceData(0, 0)

        var total = 0
        var attended = 0

        try {
            val subjects = db.collection("users")
                .document(userId)
                .collection("subjects")
                .get(Source.CACHE)
                .await()

            for (subjectDoc in subjects.documents) {
                val attendanceSnapshot = subjectDoc.reference
                    .collection("attendance")
                    .get(Source.CACHE)
                    .await()

                for (doc in attendanceSnapshot.documents) {
                    val status = doc.getString("status")?.uppercase() ?: "ABSENT"
                    // We only count PRESENT or ABSENT towards the total
                    if (status == "PRESENT" || status == "ABSENT") {
                        total++
                        if (status == "PRESENT") attended++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TotalAttendanceWidget", "Error fetching data for widget", e)
        }

        return AttendanceData(total, attended)
    }

    @Composable
    private fun WidgetUI(data: AttendanceData) {
        val context = LocalContext.current
        val colorPrimary = Color(0xFF6366F1) // Indigo Primary
        val colorSurface = Color(0xFF1E2235) // Premium dark blue-gray surface

        val percentageFormatted = String.format(Locale.getDefault(), "%.1f%%", data.percentage)
        
        val statusText = when {
            data.totalClasses == 0 -> "No Data"
            data.percentage >= 75f -> "On Track"
            else -> "Needs Attention"
        }
        val statusColor = when {
            data.totalClasses == 0 -> Color(0xFF9E9E9E)
            data.percentage >= 75f -> Color(0xFF0CFDCD) // Neon cyan
            else -> Color(0xFFFF5252) // Vibrant Red
        }

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(colorSurface))
                .padding(16.dp)
                .clickable(
                    actionStartActivity(
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column (Percentage)
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Attendance",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.8f)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = percentageFormatted,
                    style = TextStyle(
                        color = ColorProvider(colorPrimary),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            // Right Column (Details and Status)
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = GlanceModifier.size(6.dp).background(ColorProvider(statusColor))) {}
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = ColorProvider(statusColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = "${data.attendedClasses} / ${data.totalClasses}",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Classes",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}
