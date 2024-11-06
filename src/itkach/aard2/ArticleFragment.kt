package itkach.aard2

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment

class ArticleFragment : Fragment() {
     var webView: ArticleWebView? = null
        private set
    private lateinit var miBookmark: MenuItem
    private lateinit var miFullscreen: MenuItem
    var url: String? = null
     var position: Int? = -1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        //Looks like this may be called multiple times with the same menu
        //for some reason when activity is restored, so need to clear
        //to avoid duplicates
        menu.clear()
        inflater.inflate(R.menu.article, menu)
        miBookmark = menu.findItem(R.id.action_bookmark_article)
        miFullscreen = menu.findItem(R.id.action_fullscreen)
        if (Build.VERSION.SDK_INT < 19) {
            miFullscreen.setVisible(false)
            miFullscreen.setEnabled(false)
        }
    }

    private fun displayBookmarked(value: Boolean) {
        miBookmark.setChecked(value).setIcon(if (value) R.drawable.bookmark_fill_24px else R.drawable.bookmark_24px)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (webView == null) {
            return false;
        }
        val itemId = item.itemId
        if (itemId == R.id.action_find_in_page) {
            webView!!.showFindDialog(null, true)
            return true
        }
        if (itemId == R.id.action_bookmark_article) {
            val app = requireActivity().application as Application
            if (this.url != null) {
                if (item.isChecked) {
                    app.removeBookmark(url!!)
                    displayBookmarked(false)
                } else {
                    app.addBookmark(url!!)
                    displayBookmarked(true)
                }
            }
            return true
        }
        if (itemId == R.id.action_fullscreen) {
            (activity as ArticleCollectionActivity?)!!.toggleFullScreen()
            return true
        }
        if (itemId == R.id.action_zoom_in) {
            webView!!.textZoomIn()
            return true
        }
        if (itemId == R.id.action_zoom_out) {
            webView!!.textZoomOut()
            return true
        }
        if (itemId == R.id.action_zoom_reset) {
            webView!!.resetTextZoom()
            return true
        }
        if (itemId == R.id.action_load_remote_content) {
            webView!!.forceLoadRemoteContent = !webView!!.forceLoadRemoteContent
            webView!!.settings.cacheMode =
                if (webView!!.forceLoadRemoteContent) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
            webView!!.reload()
            return true
        }
        if (itemId == R.id.action_select_style) {
            val builder = AlertDialog.Builder(activity)
            val styleTitles = webView!!.availableStyles
            builder.setTitle(R.string.select_style)
                .setItems(styleTitles) { dialog, which ->
                    val title = styleTitles[which]
                    webView!!.saveStylePref(title)
                    webView!!.applyStylePref()
                }
            val dialog = builder.create()
            dialog.show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val args = arguments
        this.url = args?.getString(ARG_URL)
        this.position = args?.getInt(ARG_POSITION)
        if (url == null) {
            val layout = inflater.inflate(R.layout.empty_view, container, false)
            val textView = layout.findViewById<View>(R.id.empty_text) as TextView
            textView.text = ""
            val icon = layout.findViewById<View>(R.id.empty_icon) as ImageView
            icon.setImageResource(R.drawable.cancel)
            //            this.setHasOptionsMenu(false);
            return layout
        }

        val layout = inflater.inflate(R.layout.article_view, container, false)
        val progressBar =
            layout.findViewById<View>(R.id.webViewProgress) as ContentLoadingProgressBar
        webView = layout.findViewById<View>(R.id.webView) as ArticleWebView
        if (this.position == (activity as ArticleCollectionActivity?)!!.currentPosition) {
            loadUrlIfNeeded()
        }
//        webView.restoreState(savedInstanceState!!)
        webView!!.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                val activity: Activity? = activity
                activity?.runOnUiThread {
                    progressBar.progress = newProgress
                    if (newProgress >= progressBar.max) {
                        progressBar.visibility = ViewGroup.GONE
                    }
                }
            }
        }

        return layout
    }

    fun loadUrlIfNeeded() {
        webView?.loadUrl(url!!)

    }


    override fun onResume() {
        super.onResume()
        applyTextZoomPref()
        applyStylePref()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (this.url == null) {
            miBookmark.setVisible(false)
        } else {
            val app = requireActivity().application as Application
            try {
                val bookmarked = app.isBookmarked(url!!)
                displayBookmarked(bookmarked)
            } catch (ex: Exception) {
                miBookmark.setVisible(false)
            }
        }
        applyTextZoomPref()
        applyStylePref()
        miFullscreen.setIcon(R.drawable.fullscreen_24px)
    }

    fun applyTextZoomPref() {
        webView?.applyTextZoomPref()
    }

    fun applyStylePref() {
        webView?.applyStylePref()
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }

    companion object {
        const val ARG_URL: String = "articleUrl"
        const val ARG_POSITION: String = "articlePosition"
    }
}