package itkach.aard2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import java.util.SortedSet


class MainActivity : AppCompatActivity() {
    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }


    private lateinit var bottomNavigationViewItems: SortedSet<Int>
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private lateinit var bottomNavigationView: BottomNavigationView

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as Application
        app.installTheme(this)
        setContentView(R.layout.activity_main)


        val navHostFragment = supportFragmentManager.findFragmentById(
            R.id.nav_host_container
        ) as NavHostFragment
        navController = navHostFragment.navController

        // Setup the bottom navigation view with navController
        bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)
    //        bottomNavigationView.setOnItemSelectedListener {
    //            blur()
    //            true
    //        }

        // Setup the ActionBar with navController and 3 top level destinations
        bottomNavigationViewItems = sortedSetOf(R.id.lookupFragment, R.id.bookmarksFragment, R.id.historyFragment, R.id.dictionariesFragment, R.id.settingsFragment)
        appBarConfiguration = AppBarConfiguration(
            bottomNavigationViewItems
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        if (app.dictionaries.size == 0) {
            bottomNavigationView.selectedItemId = R.id.dictionaries
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                val app = application as Application
                val blob = app.random()
                if (blob == null) {
                    Toast.makeText(
                        this,
                        R.string.article_collection_nothing_found,
                        Toast.LENGTH_SHORT
                    ).show()
                    return true
                }
                val intent: Intent = Intent(
                    this,
                    ArticleCollectionActivity::class.java
                )
                intent.setData(Uri.parse(app.getUrl(blob)))
                startActivity(intent)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }
    private fun blur() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        try {
            val currentFocus: View? = currentFocus
            if (currentFocus != null) {
                inputMethodManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hiding soft input failed", e)
        }
    }
//    override fun onPause() {
//        //Looks like shown soft input sometimes causes a system ui visibility
//        //change event that breaks article activity launched from here out of full screen mode.
//        //Hiding it appears to reduce that.
//        blur()
//        super.onPause()
//    }
    fun getCurrentlyVisibleFragment(): Fragment? {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_container)
        return navHostFragment?.childFragmentManager?.fragments?.first()
    }
    override fun onBackPressed() {
//        val currentItem = viewPager!!.currentItem
//        val frag = appSectionsPagerAdapter!!.createFragment(currentItem)
//        Log.d(TAG, "current tab: $currentItem")
        val frag = getCurrentlyVisibleFragment()
        if (frag is BlobDescriptorListFragment) {
            if (frag.isFilterExpanded) {
                Log.d(TAG, "Filter is expanded")
                frag.collapseFilter()
                return
            }
        }
        super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!autoPaste()) {
            Log.d(TAG, "Auto-paste is off")
            return
        }
        if (!hasFocus) {
            Log.d(TAG, "has no focus")
            return
        }
        val text = Clipboard.peek(this)
        if (text != null) {
            bottomNavigationView.selectedItemId = (R.id.lookup)
            invalidateOptionsMenu()
        }
    }

    private fun useVolumeForNav(): Boolean {
        val app = application as Application
        return app.useVolumeForNav()
    }

    private fun autoPaste(): Boolean {
        val app = application as Application
        return app.autoPaste()
    }
    private val currentIndex: Int
        get() =  appBarConfiguration.topLevelDestinations.indexOf(navController.currentDestination?.id)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCanceled) {
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!useVolumeForNav()) {
                return false
            }
            val currentIndex = currentIndex
            val newIndex = (bottomNavigationViewItems.size + currentIndex - 1) % bottomNavigationViewItems.size
            val menus = bottomNavigationView.menu
            bottomNavigationView.selectedItemId = bottomNavigationView.menu.getItem(newIndex).itemId

            return true
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!useVolumeForNav()) {
                return false
            }


            val currentIndex = currentIndex
            val newIndex = (currentIndex + 1) % bottomNavigationViewItems.size
            bottomNavigationView.selectedItemId = bottomNavigationView.menu.getItem(newIndex).itemId

            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return useVolumeForNav()
        }
        return super.onKeyDown(keyCode, event)
    }
}
