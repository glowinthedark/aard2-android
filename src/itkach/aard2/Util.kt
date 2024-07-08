package itkach.aard2

import android.net.Uri
import android.util.Log
import java.util.Collections

internal object Util {
    val TAG: String = Util::class.java.simpleName

    @JvmStatic
    fun compare(l1: Long, l2: Long): Int {
        return if (l1 < l2) -1 else (if (l1 == l2) 0 else 1)
    }

    fun <T : Comparable<T>?> sort(list: List<T>?) {
        try {
            Collections.sort(list)
        } catch (e: Exception) {
            Log.w(TAG, "Error while sorting:", e)
        }
    }

    fun <T> sort(list: List<T>?, comparator: Comparator<in T>?) {
        try {
            Collections.sort(list, comparator)
        } catch (e: Exception) {
            //From http://www.oracle.com/technetwork/java/javase/compatibility-417013.html#source
            /*
            Synopsis: Updated sort behavior for Arrays and Collections may throw an IllegalArgumentException
            Description: The sorting algorithm used by java.util.Arrays.sort and (indirectly) by
                         java.util.Collections.sort has been replaced. The new sort implementation may
                         throw an IllegalArgumentException if it detects a Comparable that violates
                         the Comparable contract. The previous implementation silently ignored such a situation.
                         If the previous behavior is desired, you can use the new system property,
                         java.util.Arrays.useLegacyMergeSort, to restore previous mergesort behavior.
            Nature of Incompatibility: behavioral
            RFE: 6804124
             */
            //Name comparators use ICU collation key comparison. Given Unicode collation complexity
            //it's hard to be sure that collation key comparisons won't trigger an exception. It certainly
            //does at least for some keys in ICU 53.1.
            //Incorrect or no sorting seems preferable than a crashing app.
            //TODO perhaps java.util.Collections.sort shouldn't be used at all
            Log.w(TAG, "Error while sorting:", e)
        }
    }

    @JvmStatic
    fun isBlank(value: String?): Boolean {
        return value == null || value.trim { it <= ' ' } == ""
    }

    fun wikipediaToSlobUri(uri: Uri): String? {
        val host = uri.host
        if (isBlank(host)) {
            return null
        }
        var normalizedHost = host
        val parts = host!!.split(".".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        //if mobile host like en.m.wikipedia.opr get rid of m
        if (parts.size == 4) {
            normalizedHost = String.format("%s.%s.%s", parts[0], parts[2], parts[3])
        }
        return "http://$normalizedHost"
    }
}
