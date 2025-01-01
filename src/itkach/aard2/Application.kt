package itkach.aard2

import android.app.Activity
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.DataSetObserver
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import itkach.slob.Slob
import itkach.slob.Slob.PeekableIterator
import itkach.slobber.Slobber
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import java.util.Collections
import java.util.Locale
import java.util.Random
import kotlin.math.floor

class Application : android.app.Application() {
    private var slobber: Slobber? = null

    lateinit var bookmarks: BlobDescriptorList
    lateinit var history: BlobDescriptorList
    lateinit var dictionaries: SlobDescriptorList

    private var port = -1

    @JvmField
    var lastResult: BlobListAdapter? = null

    private var bookmarkStore: DescriptorStore<BlobDescriptor>? = null
    private var historyStore: DescriptorStore<BlobDescriptor>? = null
    private var dictStore: DescriptorStore<SlobDescriptor>? = null

    private var mapper: ObjectMapper? = null

    var lookupQuery: String = ""
        private set

    private var articleActivities: MutableList<Activity>? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 19) {
            try {
                val setWebContentsDebuggingEnabledMethod = WebView::class.java.getMethod(
                    "setWebContentsDebuggingEnabled", Boolean::class.javaPrimitiveType
                )
                setWebContentsDebuggingEnabledMethod.invoke(null, true)
            } catch (e1: NoSuchMethodException) {
                Log.d(
                    TAG,
                    "setWebContentsDebuggingEnabledMethod method not found"
                )
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
        articleActivities = Collections.synchronizedList(ArrayList())

        mapper = ObjectMapper()
        mapper!!.configure(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )
        dictStore = DescriptorStore(mapper!!, getDir("dictionaries", MODE_PRIVATE))
        bookmarkStore = DescriptorStore(
            mapper!!, getDir(
                "bookmarks", MODE_PRIVATE
            )
        )
        historyStore = DescriptorStore(
            mapper!!, getDir(
                "history", MODE_PRIVATE
            )
        )
        slobber = Slobber()

        val t0 = System.currentTimeMillis()

        startWebServer()

        Log.d(
            TAG, String.format(
                "Started web server on port %d in %d ms",
                port, (System.currentTimeMillis() - t0)
            )
        )
        try {
            var `is` = javaClass.classLoader.getResourceAsStream("styleswitcher.js")
            jsStyleSwitcher = readTextFile(`is`, 0)
            `is` = assets.open("userstyle.js")
            jsUserStyle = readTextFile(`is`, 0)
            `is` = assets.open("clearuserstyle.js")
            jsClearUserStyle = readTextFile(`is`, 0)
            `is` = assets.open("setcannedstyle.js")
            jsSetCannedStyle = readTextFile(`is`, 0)
            `is` = assets.open("taptosearch.min.js")
            jsTapToSearch = readTextFile(`is`, 0)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        val initialQuery = prefs().getString("query", "")

        lastResult = BlobListAdapter(this)

        dictionaries = SlobDescriptorList(this, dictStore)
        bookmarks = BlobDescriptorList(this, bookmarkStore!!)
        history = BlobDescriptorList(this, historyStore!!)

        dictionaries!!.registerDataSetObserver(object : DataSetObserver() {
            @Synchronized
            override fun onChanged() {
                lastResult!!.setData(ArrayList<Slob.Blob>().iterator())
                slobber!!.setSlobs(null)
                val slobs: MutableList<Slob> = ArrayList()
                for (sd in dictionaries!!) {
                    val origSlobId = sd.id
                    val s = sd.load(applicationContext)
                    if (s != null) {
                        if (!origSlobId.equals(sd.id)) {
                            Log.d(TAG, String.format("%s has been replaced, updating dict store %s -> %s", sd.path, origSlobId, sd.id))
                            //dictionary file has been replaced
                            //(same file name, different slob uuid)
                            //need to update store accordingly
                            dictStore!!.delete(origSlobId)
                            dictStore!!.save(sd)
                        }
                        slobs.add(s)
                    }
                }
                slobber!!.setSlobs(slobs)

                EnableLinkHandling().execute(*activeSlobs)

                lookup(lookupQuery)
                bookmarks.notifyDataSetChanged()
                history.notifyDataSetChanged()
            }
        })

        dictionaries!!.load()
        lookup(initialQuery, false)
        bookmarks!!.load()
        history!!.load()
    }

    private fun startWebServer() {
        var portCandidate = PREFERRED_PORT
        try {
            slobber!!.start("127.0.0.1", portCandidate)
            port = portCandidate
        } catch (e: IOException) {
            Log.w(
                TAG,
                String.format(
                    "Failed to start on preferred port %d",
                    portCandidate
                ), e
            )
            val seen: MutableSet<Int> = HashSet()
            seen.add(PREFERRED_PORT)
            val rand = Random()
            var attemptCount = 0
            while (true) {
                val value = 1 + floor((65535 - 1025) * rand.nextDouble())
                    .toInt()
                portCandidate = 1024 + value
                if (seen.contains(portCandidate)) {
                    continue
                }
                attemptCount += 1
                seen.add(portCandidate)
                var lastError: Exception?
                try {
                    slobber!!.start("127.0.0.1", portCandidate)
                    port = portCandidate
                    break
                } catch (e1: IOException) {
                    lastError = e1
                    Log.w(
                        TAG,
                        String.format(
                            "Failed to start on port %d",
                            portCandidate
                        ), e1
                    )
                }
                if (attemptCount >= 20) {
                    throw RuntimeException("Failed to start web server", lastError)
                }
            }
        }
    }

    fun prefs(): SharedPreferences {
        return this.getSharedPreferences(PREF, MODE_PRIVATE)
    }

    val preferredTheme: String?
        get() {
            val theme = prefs().getString(
                PREF_UI_THEME,
                PREF_UI_THEME_SYSTEM
            )

            if (theme == PREF_UI_THEME_SYSTEM) {
                val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

                return if ((mode == Configuration.UI_MODE_NIGHT_YES)) "dark" else "light"
            }

            return theme
        }

    fun installTheme(activity: Activity?) {
        val theme = prefs().getString(
            PREF_UI_THEME,
            PREF_UI_THEME_SYSTEM
        )
        //            activity.setTheme(android.R.style.Theme_Material);
        if (theme == PREF_UI_THEME_DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else if (theme == PREF_UI_THEME_LIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        //        if (theme.equals(PREF_UI_THEME_DARK)) {
//        }
//        else {
//            activity.setTheme(android.R.style.Theme_Material_Light);
//        }
    }

    fun push(activity: Activity) {
        articleActivities!!.add(activity)
        Log.d(TAG, "Activity added, stack size " + articleActivities!!.size)
        if (articleActivities!!.size > 3) {
            Log.d(TAG, "Max stack size exceeded, finishing oldest activity")
            articleActivities!![0].finish()
        }
    }

    fun pop(activity: Activity) {
        articleActivities!!.remove(activity)
    }


    val activeSlobs: Array<Slob?>
        get() {
            val result: MutableList<Slob?> = ArrayList(
                dictionaries!!.size
            )
            for (sd in dictionaries!!) {
                if (sd.active) {
                    val s = slobber!!.getSlob(sd.id)
                    if (s != null) {
                        result.add(s)
                    }
                }
            }
            return result.toTypedArray<Slob?>()
        }

    val favoriteSlobs: Array<Slob?>
        get() {
            val result: MutableList<Slob?> = ArrayList(
                dictionaries!!.size
            )
            for (sd in dictionaries!!) {
                if (sd.active && sd.priority > 0) {
                    val s = slobber!!.getSlob(sd.id)
                    if (s != null) {
                        result.add(s)
                    }
                }
            }
            return result.toTypedArray<Slob?>()
        }


    fun find(key: String?): Iterator<Slob.Blob> {
        return Slob.find(key, *activeSlobs)
    }

    fun find(key: String?, preferredSlobId: String?): Iterator<Slob.Blob> {
        //When following links we want to consider all dictionaries
        //including the ones user turned off
        return find(key, preferredSlobId, false)
    }

    @JvmOverloads
    fun find(
        key: String?,
        preferredSlobId: String?,
        activeOnly: Boolean,
        upToStrength: Slob.Strength? = null
    ): PeekableIterator<Slob.Blob> {
        val t0 = System.currentTimeMillis()
        val slobs = if (activeOnly) activeSlobs else slobber!!.slobs
        val result = Slob.find(key, slobs, slobber!!.findSlob(preferredSlobId), upToStrength)
        Log.d(TAG, String.format("find ran in %dms", System.currentTimeMillis() - t0))
        return result
    }

    var isOnlyFavDictsForRandomLookup: Boolean
        get() {
            val prefs = prefs()
            return prefs.getBoolean(PREF_RANDOM_FAV_LOOKUP, false)
        }
        set(value) {
            val prefs = prefs()
            val editor = prefs.edit()
            editor.putBoolean(PREF_RANDOM_FAV_LOOKUP, value)
            editor.commit()
        }

    fun random(): Slob.Blob {
        val slobs = if (isOnlyFavDictsForRandomLookup) favoriteSlobs else activeSlobs
        return slobber!!.findRandom(slobs)
    }

    fun useVolumeForNav(): Boolean {
        val prefs = prefs()
        return prefs.getBoolean(PREF_USE_VOLUME_FOR_NAV, true)
    }

    fun setUseVolumeForNav(value: Boolean) {
        val prefs = prefs()
        val editor = prefs.edit()
        editor.putBoolean(PREF_USE_VOLUME_FOR_NAV, value)
        editor.apply()
    }

    fun autoPaste(): Boolean {
        val prefs = prefs()
        return prefs.getBoolean(PREF_AUTO_PASTE, false)
    }

    fun setAutoPaste(value: Boolean) {
        val prefs = prefs()
        val editor = prefs.edit()
        editor.putBoolean(PREF_AUTO_PASTE, value)
        editor.apply()
    }

    fun tapToSearch(): Boolean {
        val prefs = prefs()
        return prefs.getBoolean(PREF_TAP_TO_SEARCH, false)
    }

    fun setTapToSearch(value: Boolean) {
        val prefs = prefs()
        val editor = prefs.edit()
        editor.putBoolean(PREF_TAP_TO_SEARCH, value)
        editor.apply()
    }


    fun getUrl(blob: Slob.Blob?): String {
        return String.format(
            CONTENT_URL_TEMPLATE,
            port, Slobber.mkContentURL(blob)
        )
    }

    fun getSlob(slobId: String?): Slob? {
        return slobber?.getSlob(slobId)
    }

    @Synchronized
    fun addDictionary(uri: Uri): Boolean {
        val newDesc = SlobDescriptor.fromUri(applicationContext, uri.toString())
        if (newDesc.id != null) {
            for (d in dictionaries!!) {
                if (d.id != null && d.id == newDesc.id) {
                    return true
                }
            }
        }
        dictionaries!!.add(newDesc)
        return false
    }


    fun findSlob(slobOrUri: String?): Slob? {
        return slobber?.findSlob(slobOrUri)
    }

    fun getSlobURI(slobId: String?): String? {
        return slobber?.getSlobURI(slobId)
    }


    fun addBookmark(contentURL: String) {
        bookmarks!!.add(contentURL)
    }

    fun removeBookmark(contentURL: String) {
        bookmarks!!.remove(contentURL)
    }

    fun isBookmarked(contentURL: String): Boolean {
        return bookmarks!!.contains(contentURL)
    }

    private fun setLookupResult(query: String, data: Iterator<Slob.Blob>) {
        lastResult!!.setData(data)
        lookupQuery = query
        val edit = prefs().edit()
        edit.putString("query", query)
        edit.apply()
    }

    private var currentLookupTask: Deferred<Unit>? = null

    @JvmOverloads
    fun lookup(query: String?, async: Boolean = true) {
        if (currentLookupTask != null) {
            currentLookupTask!!.cancel()
            notifyLookupCanceled(query)
            currentLookupTask = null
        }
        notifyLookupStarted(query)
        if (query == null || query == "") {
            setLookupResult("", ArrayList<Slob.Blob>().iterator())
            notifyLookupFinished(query)
            return
        }

        if (async) {
            val deferred = GlobalScope.async {
                val result = find(query)
                setLookupResult(query, result)
                notifyLookupFinished(query)
            }
            deferred.start()
//            currentLookupTask = object : AsyncTask<Void?, Void?, Iterator<Slob.Blob>>() {
//
//                override fun doInBackground(vararg params: Void?): Iterator<Slob.Blob> {
//                    return find(query)
//                }
//                override fun onPostExecute(result: Iterator<Slob.Blob>) {
//                    if (!isCancelled) {
//                        setLookupResult(query, result)
//                        notifyLookupFinished(query)
//                        currentLookupTask = null
//                    }
//                }
//            }
//            currentLookupTask.execute()
        } else {
            setLookupResult(query, find(query))
            notifyLookupFinished(query)
        }
    }

    private fun notifyLookupStarted(query: String?) {
        for (l in lookupListeners) {
            l.onLookupStarted(query)
        }
    }

    private fun notifyLookupFinished(query: String?) {
        for (l in lookupListeners) {
            l.onLookupFinished(query)
        }
    }

    private fun notifyLookupCanceled(query: String?) {
        for (l in lookupListeners) {
            l.onLookupCanceled(query)
        }
    }

    private val lookupListeners: MutableList<LookupListener> = ArrayList()

    fun addLookupListener(listener: LookupListener) {
        lookupListeners.add(listener)
    }

    fun removeLookupListener(listener: LookupListener) {
        lookupListeners.remove(listener)
    }


    internal class FileTooBigException : IOException()


    private inner class EnableLinkHandling : AsyncTask<Slob, Void?, Void?>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(slobs: Array<Slob>): Void? {
            val hosts: MutableSet<String> = HashSet()
            for (slob in slobs) {
                try {
                    val uriValue = slob.tags["uri"]
                    val uri = Uri.parse(uriValue)
                    val host = uri.host
                    if (host != null) {
                        hosts.add(host.lowercase(Locale.getDefault()))
                    }
                } catch (ex: Exception) {
                    Log.w(
                        TAG,
                        String.format("Dictionary %s (%s) has no uri tag", slob.id, slob.tags),
                        ex
                    )
                }
            }

            var t0 = System.currentTimeMillis()
            val packageName = packageName
            try {
                val pm = packageManager
                val p = pm.getPackageInfo(
                    packageName,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_DISABLED_COMPONENTS
                )
                Log.d(
                    TAG,
                    "Done getting available activities in " + (System.currentTimeMillis() - t0)
                )
                t0 = System.currentTimeMillis()
                for (activityInfo in p.activities!!) {

                    if (isCancelled) break
                    if (activityInfo.targetActivity != null) {
                        val enabled = hosts.contains(activityInfo.name)
                        if (enabled) {
                            Log.d(TAG, "Enabling links handling for " + activityInfo.name)
                        }
                        val setting =
                            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        pm.setComponentEnabledSetting(
                            ComponentName(applicationContext, activityInfo.name),
                            setting, PackageManager.DONT_KILL_APP
                        )
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, e)
            }
            Log.d(TAG, "Done enabling activities in " + (System.currentTimeMillis() - t0))
            return null
        }
    }

    companion object {
        const val LOCALHOST: String = "127.0.0.1"
        const val CONTENT_URL_TEMPLATE: String = "http://" + LOCALHOST + ":%s%s"

        private const val PREFERRED_PORT = 8013
        @JvmField
        var jsStyleSwitcher: String? = null
        @JvmField
        var jsTapToSearch: String? = null
        @JvmField
        var jsUserStyle: String? = null
        @JvmField
        var jsClearUserStyle: String? = null
        @JvmField
        var jsSetCannedStyle: String? = null

        private const val PREF = "app"
        const val PREF_RANDOM_FAV_LOOKUP: String = "onlyFavDictsForRandomLookup"
        const val PREF_UI_THEME: String = "UITheme"
        const val PREF_UI_THEME_SYSTEM: String = "system"
        const val PREF_UI_THEME_LIGHT: String = "light"
        const val PREF_UI_THEME_DARK: String = "dark"
        const val PREF_USE_VOLUME_FOR_NAV: String = "useVolumeForNav"
        const val PREF_AUTO_PASTE: String = "autoPaste"
        const val PREF_TAP_TO_SEARCH: String = "tapToSearch"

        private val TAG: String = Application::class.java.simpleName

        @JvmStatic
        @Throws(IOException::class, FileTooBigException::class)
        fun readTextFile(`is`: InputStream?, maxSize: Int): String {
            val reader = InputStreamReader(`is`, "UTF-8")
            val sw = StringWriter()
            val buf = CharArray(16384)
            var count = 0
            while (true) {
                val read = reader.read(buf)
                if (read == -1) {
                    break
                }
                count += read
                if (maxSize > 0 && count > maxSize) {
                    throw FileTooBigException()
                }
                sw.write(buf, 0, read)
            }
            reader.close()
            return sw.toString()
        }
    }
}
