package com.kishan.attendmate.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kishan.attendmate.alarms.DayConfirmationAlarmScheduler
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * DailyPlanningWorker
 *
 * Responsibility:
 * - Safety net only
 * - Ensures daily Day Confirmation alarm is scheduled
 *
 * NOT responsible for:
 * - Lecture timing
 * - Slot logic
 * - Lecture notifications
 */
class DailyPlanningWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

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

        return Result.success()
    }
}
