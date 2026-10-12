package com.shibill.app.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.shibill.app.MainActivity
import com.shibill.app.R
import com.shibill.app.parse.model.ParsedBill
import com.shibill.app.util.AmountFormat
import com.shibill.app.util.Constants

/**
 * 采集结果通知（§5.3）：
 * - 解析成功 → 「记一笔：¥25.00 美团外卖（微信）」点击进 ConfirmScreen 预填
 * - 解析失败 → 「捕获到支付但无法识别，点击手动补记」
 * - 自动入账 → 「已入账：…」展示型通知
 */
object CaptureNotifier {

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_CAPTURE,
            context.getString(R.string.notification_channel_capture),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_capture_desc)
        }
        manager.createNotificationChannel(channel)
    }

    /** 解析成功：待确认通知 */
    fun notifyConfirm(context: Context, pendingId: String, bill: ParsedBill) {
        val title = buildTitle("记一笔", bill)
        val route = "confirm?pending=$pendingId&raw=${Uri.encode(bill.rawText.take(500))}"
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("点击确认入账")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bill.rawText))
            .setAutoCancel(true)
            .setContentIntent(buildRoutePendingIntent(context, route))
            .build()
        notify(context, bill.fingerprint.hashCode(), notification)
    }

    /** 解析失败：手动补记通知 */
    fun notifyFailure(context: Context, rawText: String) {
        val route = "confirm?raw=${Uri.encode(rawText.take(1000))}"
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("捕获到支付但无法识别")
            .setContentText("点击手动补记")
            .setStyle(NotificationCompat.BigTextStyle().bigText(rawText))
            .setAutoCancel(true)
            .setContentIntent(buildRoutePendingIntent(context, route))
            .build()
        notify(context, rawText.hashCode(), notification)
    }

    /** 自动入账（免确认）展示型通知 */
    fun notifyAutoCommitted(context: Context, bill: ParsedBill) {
        val title = buildTitle("已入账", bill)
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("已自动记账")
            .setAutoCancel(true)
            .setContentIntent(buildRoutePendingIntent(context, "home"))
            .build()
        notify(context, bill.fingerprint.hashCode(), notification)
    }

    private fun buildTitle(prefix: String, bill: ParsedBill): String {
        val merchantPart = if (bill.merchant.isNotEmpty()) " ${bill.merchant}" else ""
        return "$prefix：${AmountFormat.format(bill.amount)}$merchantPart（${bill.account}）"
    }

    private fun buildRoutePendingIntent(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(id, notification)
    }
}
