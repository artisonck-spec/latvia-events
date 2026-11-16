package lv.latviaevents

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Every few hours (when the phone has internet) checks GitHub for a newer app version and for new events,
 * and shows a notification. No boot permission: the check is set up again each time the app opens.
 */
class BackgroundCheck : JobService() {

    companion object {
        private const val JOB_ID = 2001
        private const val BASE = "https://raw.githubusercontent.com/artisonck-spec/latvia-events/main/data/"

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, BackgroundCheck::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(4 * 60 * 60 * 1000L)
                .build()
            runCatching { js.schedule(job) }
        }

        private fun get(url: String): String {
            val c = URL(url + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 20000; c.useCaches = false
            return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
        }

        fun check(ctx: Context) {
            val prefs = ctx.getSharedPreferences("background", Context.MODE_PRIVATE)
            // 1. new app version
            runCatching {
                val app = JSONObject(get(BASE + "app.json"))
                val newest = app.optLong("build")
                val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                val mine = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
                if (newest > mine && prefs.getLong("notifiedBuild", 0) < newest) {
                    val note = app.optString("notes").ifBlank { "Ir gatava jaunāka versija." }
                    Reminders.notify(ctx, "", "Pieejams atjauninājums · versija $newest", "$note\nAtver lietotni un spied Atjaunināt.", 901)
                    prefs.edit().putLong("notifiedBuild", newest).apply()
                }
            }
            // 2. new events since the last check
            runCatching {
                val events = JSONObject(get(BASE + "events.json")).getJSONArray("events")
                val ids = HashSet<String>(); val titles = HashMap<String, String>()
                for (i in 0 until events.length()) {
                    val e = events.getJSONObject(i); val id = e.optString("id")
                    if (id.isNotEmpty()) { ids.add(id); titles[id] = e.optString("title") }
                }
                val known = prefs.getStringSet("knownIds", null)
                if (known != null) {
                    val fresh = ids.filter { it !in known }
                    if (fresh.isNotEmpty()) {
                        val head = if (fresh.size == 1) "Jauns pasākums" else "Jauni pasākumi: ${fresh.size}"
                        val text = fresh.take(5).mapNotNull { titles[it] }.joinToString("\n")
                        Reminders.notify(ctx, if (fresh.size == 1) fresh[0] else "", head, text, 902)
                    }
                }
                prefs.edit().putStringSet("knownIds", ids).apply()
            }
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { check(applicationContext) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
