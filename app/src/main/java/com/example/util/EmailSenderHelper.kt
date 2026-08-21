package com.baoverung.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.LabeledIntent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import com.baoverung.app.data.local.entity.EmailQueueEntity
import java.io.File

object EmailSenderHelper {

    /**
     * Sends a single email with optional single or pipe-separated multiple attachments using system Intent chooser.
     */
    fun sendEmail(
        context: Context,
        recipient: String,
        subject: String,
        body: String,
        attachmentPath: String? = null,
        forceGmailOnly: Boolean = false
    ): Boolean {
        return try {
            val uris = ArrayList<Uri>()
            if (!attachmentPath.isNullOrEmpty()) {
                val paths = attachmentPath.split("|").filter { it.isNotBlank() }
                for (p in paths) {
                    if (p.startsWith("content://")) {
                        uris.add(Uri.parse(p))
                    } else {
                        val file = File(p)
                        if (file.exists()) {
                            val uri: Uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            uris.add(uri)
                        } else {
                            // If it doesn't exist as a file, it might be a malformed URI string
                            try {
                                val u = Uri.parse(p)
                                if (u.scheme == "content") uris.add(u)
                            } catch (e: Exception) {}
                        }
                    }
                }
            }

            val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                if (uris.size == 1) {
                    val u = uris[0]
                    type = context.contentResolver.getType(u) ?: "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, u)
                } else if (uris.size > 1) {
                    type = "multipart/mixed"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                } else {
                    type = "message/rfc822"
                }

                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.gm") // Try to force Gmail
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                if (forceGmailOnly) {
                    Toast.makeText(context, "Vui lòng cài đặt ứng dụng Gmail để thực hiện báo cáo định kỳ!", Toast.LENGTH_LONG).show()
                    return false
                }
                // If Gmail is not installed and not forced, use chooser
                val chooser = Intent.createChooser(intent.apply { setPackage(null) }, "Gửi báo cáo qua...")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Không tìm thấy ứng dụng Email trên thiết bị!", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Sends multiple emails from queue using system Intent.
     */
    fun sendEmailQueueBatch(
        context: Context,
        emailQueue: List<EmailQueueEntity>,
        forceGmailOnly: Boolean = false
    ) {
        if (emailQueue.isEmpty()) {
            Toast.makeText(context, "Hàng chờ email trống!", Toast.LENGTH_SHORT).show()
            return
        }

        if (emailQueue.size == 1) {
            val item = emailQueue[0]
            sendEmail(context, item.recipientEmail, item.subject, item.body, item.attachmentPath, forceGmailOnly)
            return
        }

        try {
            val firstItem = emailQueue[0]
            val recipient = firstItem.recipientEmail
            val combinedSubject = "[Bảo vệ rừng - Đại Thành] Báo cáo tổng hợp hàng chờ (${emailQueue.size} báo cáo)"
            val combinedBody = StringBuilder()
            combinedBody.append("TỔNG HỢP NHẬT KÝ VÀ BÁO CÁO THỰC ĐỊA (${emailQueue.size} MỤC)\n\n")

            val uris = ArrayList<Uri>()
            for ((index, item) in emailQueue.withIndex()) {
                combinedBody.append("--- Báo cáo #${index + 1} ---\n")
                combinedBody.append("Tiêu đề: ${item.subject}\n")
                combinedBody.append("Nội dung:\n${item.body}\n\n")

                if (!item.attachmentPath.isNullOrEmpty()) {
                    val paths = item.attachmentPath.split("|").filter { it.isNotBlank() }
                    for (p in paths) {
                        if (p.startsWith("content://")) {
                            val u = Uri.parse(p)
                            if (!uris.contains(u)) uris.add(u)
                        } else {
                            val file = File(p)
                            if (file.exists()) {
                                val uri: Uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                if (!uris.contains(uri)) {
                                    uris.add(uri)
                                }
                            }
                        }
                    }
                }
            }

            val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                type = "multipart/mixed"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, combinedSubject)
                putExtra(Intent.EXTRA_TEXT, combinedBody.toString())

                if (uris.size > 1) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                } else if (uris.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
                
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.gm") // Force Gmail
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                if (forceGmailOnly) {
                    Toast.makeText(context, "Vui lòng cài đặt ứng dụng Gmail để thực hiện báo cáo hàng chờ!", Toast.LENGTH_LONG).show()
                } else {
                    val chooser = Intent.createChooser(intent.apply { setPackage(null) }, "Gửi báo cáo hàng chờ...")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Lỗi khi mở Gmail: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private const val CHANNEL_ID = "forestry_reports"
    private const val NOTIF_ID = 2001

    /**
     * Shows a notification that triggers the email intent when clicked.
     */
    fun showReportNotification(
        context: Context,
        recipient: String,
        subject: String,
        body: String,
        attachmentPath: String? = null
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("recipient", recipient)
            putExtra("subject", subject)
            putExtra("body", body)
            putExtra("attachmentPath", attachmentPath)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            NOTIF_ID, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "Báo cáo lâm nghiệp", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Thông báo nhắc nhở gửi báo cáo 17h"
            }
        } else null

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && channel != null) {
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Báo cáo 17h đã sẵn sàng")
            .setContentText("Chạm để gửi báo cáo tổng hợp dữ liệu hôm nay.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            manager.notify(NOTIF_ID, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Receiver to handle notification click and launch email intent.
     */
    class NotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val recipient = intent.getStringExtra("recipient") ?: ""
            val subject = intent.getStringExtra("subject") ?: ""
            val body = intent.getStringExtra("body") ?: ""
            val attachmentPath = intent.getStringExtra("attachmentPath")
            
            sendEmail(context, recipient, subject, body, attachmentPath, forceGmailOnly = true)
        }
    }
}
