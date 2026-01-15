package com.kishan.attendmate.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.kishan.attendmate.alarms.DayConfirmationAlarmScheduler
import java.time.LocalTime

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
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {

        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result.success()

        /*
         * Fixed, predictable confirmation time
         * (example: 08:00 AM)
         *
         * AlarmManager guarantees precision.
         * Worker only ensures it exists.
         */
        DayConfirmationAlarmScheduler.schedule(
            context = context,
            triggerTime = LocalTime.of(8, 0)
        )

        return Result.success()
    }
}
