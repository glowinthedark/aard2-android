package itkach.aard2

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.util.Patterns

object Clipboard {
    private val NO_PASTE_PATTERNS = arrayOf(
        Patterns.WEB_URL,
        Patterns.EMAIL_ADDRESS,
        Patterns.PHONE
    )

    fun peek(activity: Activity): CharSequence? {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = cm.primaryClip ?: return null
        val count = clipData.itemCount
        for (i in 0 until count) {
            val item = clipData.getItemAt(i)
            val text = item.text
            if (text != null && text.length > 0) {
                for (p in NO_PASTE_PATTERNS) {
                    if (p.matcher(text).find()) {
                        Log.d(
                            "CLIPBOARD",
                            "Text matched pattern " + p.pattern() + ", not pasting: " + text
                        )
                        return null
                    }
                }
                return text
            }
        }
        return null
    }

    fun take(activity: Activity): CharSequence? {
        val text = peek(activity)
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(null, ""))
        return text
    }
}
