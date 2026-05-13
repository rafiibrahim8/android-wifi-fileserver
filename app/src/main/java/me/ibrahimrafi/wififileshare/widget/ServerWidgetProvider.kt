package me.ibrahimrafi.wififileshare.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import me.ibrahimrafi.wififileshare.MainActivity
import me.ibrahimrafi.wififileshare.R
import me.ibrahimrafi.wififileshare.model.ServerStateStore
import me.ibrahimrafi.wififileshare.server.FileServerService

class ServerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) renderWidget(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val running = ServerStateStore.isRunningFlow.value
            if (running) FileServerService.stop(context) else FileServerService.start(context)
            // The service will broadcast back through updateAll() when it (un)acquires the server.
        }
    }

    companion object {
        const val ACTION_TOGGLE = "me.ibrahimrafi.wififileshare.widget.TOGGLE"

        /** Re-render every placed instance of the widget. Safe to call from any thread. */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ServerWidgetProvider::class.java)
            val ids = mgr.getAppWidgetIds(component)
            for (id in ids) renderWidget(context, mgr, id)
        }

        private fun renderWidget(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val running = ServerStateStore.isRunningFlow.value
            val url = ServerStateStore.urlFlow.value
            val views = RemoteViews(ctx.packageName, R.layout.widget_server)

            views.setTextViewText(R.id.widget_status, ctx.getString(if (running) R.string.status_running else R.string.status_stopped))
            views.setInt(
                R.id.widget_status,
                "setBackgroundResource",
                if (running) R.drawable.bg_status_pill_running else R.drawable.bg_status_pill_stopped,
            )
            views.setTextColor(
                R.id.widget_status,
                ctx.getColor(if (running) R.color.success else R.color.danger),
            )

            views.setTextViewText(R.id.widget_url, if (running) url else "")
            views.setTextViewText(R.id.widget_toggle, ctx.getString(if (running) R.string.stop_server else R.string.start_server))

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val toggleIntent = Intent(ctx, ServerWidgetProvider::class.java).setAction(ACTION_TOGGLE)
            val togglePi = PendingIntent.getBroadcast(ctx, 100, toggleIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_toggle, togglePi)

            val openIntent = Intent(ctx, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val openPi = PendingIntent.getActivity(ctx, 101, openIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_root, openPi)

            mgr.updateAppWidget(id, views)
        }
    }
}
