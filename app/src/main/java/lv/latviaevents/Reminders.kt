package lv.latviaevents

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import java.util.Calendar

/** Reminders for saved events: the day before at 18:00 and on the day at 08:00 (phone's time zone). */
object Reminders {
    private const val PREFS = "reminders"
    const val CHANNEL = "reminders"
    const val ACTION = "lv.latviaevents.REMIND"

    /** Called by the page with the saved events: [{id,title,start,place,time}] */
    fun save(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", json).apply()
        schedule(ctx)
    }

    fun schedule(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        // cancel what was set before
        prefs.getStringSet("codes", emptySet())?.forEach { c ->
            c.toIntOrNull()?.let { code -> am.cancel(pending(ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION))) }
        }
        val codes = HashSet<String>()
        val list = runCatching { JSONArray(prefs.getString("list", "[]")) }.getOrDefault(JSONArray())
        val now = System.currentTimeMillis()
        for (i in 0 until list.length()) {
            val e = list.optJSONObject(i) ?: continue
            val id = e.optString("id"); val start = e.optString("start")
            val p = start.split("-"); if (id.isEmpty() || p.size != 3) continue
            val y = p[0].toIntOrNull() ?: continue; val m = p[1].toIntOrNull() ?: continue; val d = p[2].toIntOrNull() ?: continue
            for (kind in 0..1) {   // 0 = day before, 1 = on the day
                val cal = Calendar.getInstance().apply {
                    set(y, m - 1, d, if (kind == 0) 18 else 8, 0, 0); set(Calendar.MILLISECOND, 0)
                    if (kind == 0) add(Calendar.DAY_OF_MONTH, -1)
                }
                if (cal.timeInMillis <= now) continue
                val code = (id.hashCode() and 0x3fffffff) * 2 + kind
                val intent = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION)
                    .putExtra("id", id).putExtra("title", e.optString("title"))
                    .putExtra("place", e.optString("place")).putExtra("time", e.optString("time"))
                    .putExtra("kind", kind).putExtra("code", code)
                runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending(ctx, code, intent)) }
                codes.add(code.toString())
            }
        }
        prefs.edit().putStringSet("codes", codes).apply()
    }

    private fun pending(ctx: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun notify(ctx: Context, id: String, title: String, text: String, code: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Atgādinājumi par pasākumiem", NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(ctx, MainActivity::class.java).putExtra("event", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, code, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setColor(0xFFF0703A.toInt())
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(code, n)
    }
}

/** Fires at the reminder time and shows the notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION) return
        val title = intent.getStringExtra("title") ?: return
        val place = intent.getStringExtra("place") ?: ""
        val time = intent.getStringExtra("time") ?: ""
        val head = if (intent.getIntExtra("kind", 1) == 0) "Rīt: $title" else "Šodien: $title"
        val body = listOf(time, place).filter { it.isNotBlank() }.joinToString(" · ")
        Reminders.notify(ctx, intent.getStringExtra("id") ?: "", head, body, intent.getIntExtra("code", 1))
    }
}

