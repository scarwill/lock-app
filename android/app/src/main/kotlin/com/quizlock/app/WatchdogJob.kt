package com.quizlock.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Chowkidar: har ~15 minute (Android ki sabse chhoti limit) dekhta hai ki lock
 * chalu hona chahiye par service mar chuki hai, to use wapas chalu karta hai.
 * Restart ke baad bhi chalta rehta hai.
 */
class WatchdogJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (Store(this).shouldRun() && !LockService.isRunning) {
            try {
                LockService.start(this)
            } catch (e: Exception) {
                // Battery optimization chalu ho to Android 12+ mana kar sakta hai; agli baar phir.
            }
        }
        return false
    }

    override fun onStopJob(params: JobParameters?) = false

    companion object {
        private const val JOB_ID = 42

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(JOB_ID) != null) return
            js.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, WatchdogJob::class.java))
                    .setPeriodic(15 * 60_000L)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
