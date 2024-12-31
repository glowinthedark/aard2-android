package itkach.aard2

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import itkach.aard2.MainActivity
import itkach.slob.Slob
import itkach.slob.Slob.PeekableIterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class ArticleCollectionActivity : AppCompatActivity(), OnSystemUiVisibilityChangeListener,
    OnSharedPreferenceChangeListener {
    var articleCollectionPagerAdapter: ArticleCollectionPagerAdapter? = null
    private var viewPager: ViewPager2? = null


    internal inner class ToBlobWithFragment(private val fragment: String) : ToBlob {
        override fun convert(item: Any): Slob.Blob? {
            val b = item as Slob.Blob
            return Slob.Blob(b.owner, b.id, b.key, this.fragment)
        }
    }

    private var blobToBlob: ToBlob = object : ToBlob {
        override fun convert(item: Any): Slob.Blob? {
            return item as Slob.Blob
        }
    }


    private var onDestroyCalled = false


    val currentPosition: Int
        get() = if (viewPager != null) viewPager!!.currentItem else -1

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_PROGRESS)
        val app = application as Application
        app.installTheme(this)
        supportActionBar!!.hide()
        setContentView(R.layout.activity_article_collection_loading)
        app.push(this)
        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        actionBar.setSubtitle("...")
        val intent = intent
        val position = intent.getIntExtra("position", 0)

        val deferred = GlobalScope.async {
            var result: ArticleCollectionPagerAdapter? = null
            val articleUrl = intent.data
            try {
                if (articleUrl != null) {
                    result = createFromUri(app, articleUrl)
                } else {
                    val action = intent.action
                    result = if (action == null) {
                        createFromLastResult(app)
                    } else if (action == "showBookmarks") {
                        createFromBookmarks(app)
                    } else if (action == "showHistory") {
                        createFromHistory(app)
                    } else {
                        createFromIntent(app, intent)
                    }
                }
                articleCollectionPagerAdapter = result
                if (articleCollectionPagerAdapter == null || articleCollectionPagerAdapter!!.itemCount == 0) {
                    val messageId = if (articleCollectionPagerAdapter == null) {
                        R.string.article_collection_invalid_link
                    } else {
                        R.string.article_collection_nothing_found
                    }
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            this@ArticleCollectionActivity, messageId,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    return@async
                }

                if (position > articleCollectionPagerAdapter!!.itemCount - 1) {
                    Toast.makeText(
                        this@ArticleCollectionActivity,
                        R.string.article_collection_selected_not_available,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@async
                }
                withContext(Dispatchers.Main) {
                    setContentView(R.layout.activity_article_collection)

                    val tabs = findViewById<TabLayout>(R.id.tabs)
                    tabs.visibility =
                        if (articleCollectionPagerAdapter!!.itemCount == 1) ViewGroup.GONE else ViewGroup.VISIBLE

                    viewPager = findViewById<View>(R.id.pager) as ViewPager2
                    viewPager!!.isUserInputEnabled = false
                    viewPager!!.isNestedScrollingEnabled = true
                    viewPager!!.adapter = articleCollectionPagerAdapter
                    val tabLayoutMediator =
                        TabLayoutMediator(tabs, viewPager!!, true) { tab, position ->
                            tab.setText(articleCollectionPagerAdapter!!.getPageTitle(position))
                        }
                    tabLayoutMediator.attach()
                    viewPager!!.registerOnPageChangeCallback(object : OnPageChangeCallback() {
                        override fun onPageScrollStateChanged(arg0: Int) {}

                        override fun onPageScrolled(arg0: Int, arg1: Float, arg2: Int) {}

                        override fun onPageSelected(position: Int) {
                            updateTitle(position)
                            // fragment might not be created yet
                            val fragment =
                                articleCollectionPagerAdapter!!.getPageFragment(position.toLong())
                            if (fragment != null) {
                                articleCollectionPagerAdapter!!.primaryItem = fragment
                                runOnUiThread {
                                    fragment.loadUrlIfNeeded()
                                    fragment.applyTextZoomPref()
                                }
                            }
                        }
                    })


                    articleCollectionPagerAdapter!!.registerAdapterDataObserver(object :
                        AdapterDataObserver() {
                        override fun onChanged() {
                            if (articleCollectionPagerAdapter!!.itemCount == 0) {
                                finish()
                            }
                        }
                    })
                    viewPager!!.setCurrentItem(position, false)
                    updateTitle(position)
                }

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this@ArticleCollectionActivity,
                        e!!.localizedMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
        deferred.start()
    }

    private fun createFromUri(app: Application, articleUrl: Uri): ArticleCollectionPagerAdapter? {
        val host = articleUrl.host
        if (!(host == "localhost" || host!!.matches("127.\\d{1,3}.\\d{1,3}.\\d{1,3}".toRegex()))) {
            return createFromIntent(app, intent)
        }
        val bd = BlobDescriptor.fromUri(articleUrl) ?: return null
        val result = app.find(bd.key, bd.slobId)
        val data = BlobListAdapter(this, 20, 1)
        data.setData(result)
        val hasFragment = !Util.isBlank(bd.fragment)
        return ArticleCollectionPagerAdapter(
            app, data, if (hasFragment) ToBlobWithFragment(bd.fragment) else blobToBlob, this
        )
    }

    private fun createFromLastResult(app: Application): ArticleCollectionPagerAdapter {
        return ArticleCollectionPagerAdapter(
            app, app.lastResult, blobToBlob, this
        )
    }

    private fun createFromBookmarks(app: Application): ArticleCollectionPagerAdapter {
        return ArticleCollectionPagerAdapter(
            app, BlobDescriptorListAdapter(app.bookmarks), object : ToBlob {
                override fun convert(item: Any): Slob.Blob? {
                    return app.bookmarks!!.resolve((item as BlobDescriptor))
                }
            }, this
        )
    }

    private fun createFromHistory(app: Application): ArticleCollectionPagerAdapter {
        return ArticleCollectionPagerAdapter(
            app, BlobDescriptorListAdapter(app.history), object : ToBlob {
                override fun convert(item: Any): Slob.Blob? {
                    return app.history!!.resolve((item as BlobDescriptor))
                }
            }, this
        )
    }

    private fun createFromIntent(app: Application, intent: Intent): ArticleCollectionPagerAdapter {
        var lookupKey = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            lookupKey = getIntent()
                .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString()
        }
        if (lookupKey == null) {
            lookupKey = intent.getStringExtra(SearchManager.QUERY)
        }
        if (lookupKey == null) {
            lookupKey = intent.getStringExtra("EXTRA_QUERY")
        }
        var preferredSlobId: String? = null
        if (lookupKey == null) {
            val uri = intent.data
            if (uri != null) {
                val segments = uri!!.pathSegments
                val length = segments.size
                if (length > 0) {
                    lookupKey = segments[length - 1]
                }
                val slobUri = Util.wikipediaToSlobUri(uri)
                Log.d(TAG, String.format("Converted URI %s to slob URI %s", uri, slobUri))
                if (slobUri != null) {
                    val slob = app.findSlob(slobUri)
                    if (slob != null) {
                        preferredSlobId = slob.id.toString()
                        Log.d(
                            TAG,
                            String.format("Found slob %s for slob URI %s", preferredSlobId, slobUri)
                        )
                    }
                }

            }
        }
        val data = BlobListAdapter(this, 20, 1)
        if (lookupKey == null || lookupKey.length == 0) {
            val msg = getString(R.string.article_collection_nothing_to_lookup)
            throw RuntimeException(msg)
        } else {
            val result = stemLookup(app, lookupKey, preferredSlobId)
            data.setData(result)
        }
        return ArticleCollectionPagerAdapter(
            app, data, blobToBlob, this
        )
    }

    private fun stemLookup(
        app: Application,
        lookupKey: String,
        preferredSlobId: String? = null
    ): Iterator<Slob.Blob> {
        var result: PeekableIterator<Slob.Blob>
        val length = lookupKey.length
        var currentLookupKey = lookupKey
        var currentLength = currentLookupKey.length
        do {
            result = app.find(currentLookupKey, preferredSlobId, true)
            if (result.hasNext()) {
                val b = result.peek()
                if (b.key.length - length > 3) {
                    //we don't like this result
                } else {
                    break
                }
            }
            currentLookupKey = currentLookupKey.substring(0, currentLength - 1)
            currentLength = currentLookupKey.length
        } while (length - currentLength < 5 && currentLength > 0)
        return result
    }

    private fun updateTitle(position: Int) {
        Log.d("updateTitle", "" + position + " count: " + articleCollectionPagerAdapter!!.itemCount)
        if (articleCollectionPagerAdapter!!.itemCount == 0 || position !in 0 until articleCollectionPagerAdapter!!.itemCount) {
            return
        }
        try {
            val blob = articleCollectionPagerAdapter!!.get(position)
            val pageTitle = articleCollectionPagerAdapter!!.getPageTitle(position)
            Log.d("updateTitle", "" + blob)
            val actionBar = supportActionBar
            if (blob != null) {
                val dictLabel = blob.owner.tags["label"]
                actionBar!!.title = dictLabel
                val app = application as Application
                app.history!!.add(app.getUrl(blob))
            } else {
                actionBar!!.setTitle("???")
            }
            actionBar.subtitle = pageTitle
        } catch (e: Exception) {
            Log.e(TAG, "TODO: FIXME!!!")
        }
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == PREF_FULLSCREEN) {
            applyFullScreenPref()
        }
    }

    private fun applyFullScreenPref() {
        if (fullScreenPref) {
            fullScreen()
        } else {
            unFullScreen()
        }
    }

    fun prefs(): SharedPreferences {
        return getSharedPreferences(PREF, MODE_PRIVATE)
    }

    var fullScreenPref: Boolean
        get() = prefs().getBoolean(PREF_FULLSCREEN, false)
        private set(value) {
            val editor = prefs().edit()
            editor.putBoolean(PREF_FULLSCREEN, value)
            editor.commit()
        }

    private fun fullScreen() {
        Log.d(TAG, "[F] fullscreen")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE)
        supportActionBar!!.hide()
    }

    private fun unFullScreen() {
        Log.d(TAG, "[F] unfullscreen")
        window.decorView.systemUiVisibility = 0
        supportActionBar!!.show()
    }

    fun toggleFullScreen() {
        fullScreenPref = !fullScreenPref
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[F] Resume")
        applyFullScreenPref()
        val decorView = window.decorView
        decorView.setOnSystemUiVisibilityChangeListener(this)
        prefs().registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "[F] Pause")
        val decorView = window.decorView
        decorView.setOnSystemUiVisibilityChangeListener(null)
        prefs().unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        onDestroyCalled = true
        if (viewPager != null) {
            viewPager!!.adapter = null
        }
        if (articleCollectionPagerAdapter != null) {
            articleCollectionPagerAdapter!!.destroy()
        }
        val app = application as Application
        app.pop(this)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                val upIntent =
                    Intent.makeMainActivity(ComponentName(this, MainActivity::class.java))
                if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                    TaskStackBuilder.create(this)
                        .addNextIntent(upIntent).startActivities()
                    finish()
                } else {
                    // This activity is part of the application's task, so simply
                    // navigate up to the hierarchical parent activity.
                    upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(upIntent)
                    finish()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onSystemUiVisibilityChange(visibility: Int) {
        if (isFinishing) {
            return
        }
        val decorView = window.decorView
        val uiOptions = decorView.systemUiVisibility
        val isHideNavigation =
            ((uiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == uiOptions)
        if (!isHideNavigation) {
            fullScreenPref = false
        }
    }

    private fun useVolumeForNav(): Boolean {
        val app = application as Application
        return app.useVolumeForNav()
    }


    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCanceled) {
            return true
        }
        if (articleCollectionPagerAdapter == null) {
            return false
        }
        val af = articleCollectionPagerAdapter!!.primaryItem
        if (af != null) {
            val webView = af.webView
            if (webView != null) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (webView.canGoBack()) {
                        webView.goBack()
                        return true
                    }
                }

                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (!useVolumeForNav()) {
                        return false
                    }
                    val scrolled = webView.pageUp(false)
                    if (!scrolled) {
                        val current = currentPosition
                        if (current > 0) {
                            viewPager!!.currentItem = current - 1
                        } else {
                            finish()
                        }
                    }
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (!useVolumeForNav()) {
                        return false
                    }
                    val scrolled = webView.pageDown(false)
                    if (!scrolled) {
                        val current = currentPosition
                        if (current >= 0 && current < articleCollectionPagerAdapter!!.itemCount - 1) {
                            viewPager!!.currentItem = current + 1
                        }
                    }
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!useVolumeForNav()) {
                return false
            }
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (!useVolumeForNav()) {
            return false
        }
        val af = articleCollectionPagerAdapter!!.primaryItem
        if (af != null) {
            val webView = af.webView!!
            if (webView == null) {
                return false
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                webView.pageUp(true)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                webView.pageDown(true)
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }


    interface ToBlob {
        fun convert(item: Any): Slob.Blob?
    }

    inner class ArticleCollectionPagerAdapter(
        private var app: Application?,
        private var data: BaseAdapter?,
        toBlob: ToBlob,
        activity: FragmentActivity?
    ) : FragmentStateAdapter(
        activity!!
    ) {
        private val observer: DataSetObserver
        private val toBlob: ToBlob
        private var count: Int
        var primaryItem: ArticleFragment? = null

        init {
            this.count = data!!.count
            this.observer = object : DataSetObserver() {
                override fun onChanged() {
                    count = data!!.count
                    notifyDataSetChanged()
                }
            }
            data!!.registerDataSetObserver(observer)
            this.toBlob = toBlob
        }

        fun destroy() {
            data!!.unregisterDataSetObserver(observer)
            data = null
            app = null
        }

        override fun createFragment(i: Int): Fragment {
            val fragment: Fragment = ArticleFragment()

            val blob = get(i)
            if (blob != null) {
                val articleUrl = app!!.getUrl(blob)
                val args = Bundle()
                args.putString(ArticleFragment.ARG_URL, articleUrl)
                args.putInt(ArticleFragment.ARG_POSITION, i)
                fragment.arguments = args
            }
            return fragment
        }

        override fun getItemCount(): Int {
            return count
        }

        fun get(position: Int): Slob.Blob? {
            return toBlob.convert(data!!.getItem(position))
        }

        override fun getItemId(position: Int): Long {
            // we override getItemId to ensure position is the id
            // so that we can find a fragment based on a position
            return position.toLong()
        }

        fun getPageFragment(id: Long): ArticleFragment? {
            return this@ArticleCollectionActivity.supportFragmentManager.findFragmentByTag(
                "f$id"
            ) as ArticleFragment?
        }


        //        @Override
        fun getPageTitle(position: Int): CharSequence {
            if (position < data!!.count) {
                val item = data!!.getItem(position)
                if (item is BlobDescriptor) {
                    return item.key
                }
                if (item is Slob.Blob) {
                    return item.key
                }
            }
            return "???"
        } //this is needed so that fragment is properly updated
        //if underlying data changes (such as on unbookmark)
        //https://code.google.com/p/android/issues/detail?id=19001
        //        @Override
        //        public int getItemPosition(Object object) {
        //            return POSITION_NONE;
        //        }
    }

    companion object {
        private val TAG: String = ArticleCollectionActivity::class.java.simpleName

        const val PREF: String = "articleCollection"
        const val PREF_FULLSCREEN: String = "fullscreen"
    }
}
