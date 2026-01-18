package com.kishan.attendmate.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kishan.attendmate.alarms.DayConfirmationAlarmScheduler
import com.kishan.attendmate.domain.lectures.TodayScheduleBootstrapper
import com.kishan.attendmate.util.DebugLog
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * DailyPlanningWorker
 *
 * Responsibility:
 * - Safety net only
 * - Ensures day confirmation alarm exists
 * - Triggers lecture recovery + scheduling
 */
class DailyPlanningWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        DebugLog.d("DailyPlanningWorker: started")

        // 🔔 Fixed confirmation time (08:00 AM)
        val triggerAtMillis = LocalDate.now()
            .atTime(LocalTime.of(8, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        DayConfirmationAlarmScheduler.schedule(
            context = applicationContext,
            triggerAtMillis = triggerAtMillis
        )

        // 🔁 CRITICAL: recover & schedule today's lectures
        TodayScheduleBootstrapper.run(applicationContext)

        DebugLog.d("DailyPlanningWorker: completed")

        return Result.success()
    }
}
