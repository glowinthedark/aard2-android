package itkach.aard2;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import itkach.aard2.article.ArticleCollectionActivity;
import itkach.aard2.dictionaries.DictionaryListFragment;
import itkach.aard2.lookup.LookupFragment;
import itkach.aard2.prefs.AppPrefs;
import itkach.aard2.prefs.SettingsFragment;
import itkach.aard2.utils.ClipboardUtils;
import itkach.aard2.utils.Utils;

public class MainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener,
        ViewPager.OnPageChangeListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private AppSectionsPagerAdapter appSectionsPagerAdapter;
    private ViewPager viewPager;
    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fab;
    private int oldPosition = -1;

    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.updateNightMode();
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        appSectionsPagerAdapter = new AppSectionsPagerAdapter(getSupportFragmentManager());

        viewPager = findViewById(R.id.pager);
        viewPager.setOffscreenPageLimit(appSectionsPagerAdapter.getCount());
        viewPager.setAdapter(appSectionsPagerAdapter);
        viewPager.setOnPageChangeListener(this);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(this);

        fab = findViewById(R.id.fab);

        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        } else if (SlobHelper.getInstance().dictionaries.isEmpty()) {
            viewPager.setCurrentItem(3);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int currentSection = savedInstanceState.getInt("currentSection");
        viewPager.setCurrentItem(currentSection);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentSection", viewPager.getCurrentItem());
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_lookup) {
            viewPager.setCurrentItem(0);
        } else if (itemId == R.id.action_bookmarks) {
            viewPager.setCurrentItem(1);
        } else if (itemId == R.id.action_history) {
            viewPager.setCurrentItem(2);
        } else if (itemId == R.id.action_dictionaries) {
            viewPager.setCurrentItem(3);
        } else if (itemId == R.id.action_settings) {
            viewPager.setCurrentItem(4);
        } else return false;
        return true;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        if (oldPosition >= 0) {
            bottomNavigationView.getMenu().getItem(oldPosition).setChecked(false);
        } else {
            bottomNavigationView.getMenu().getItem(0).setChecked(false);
        }

        bottomNavigationView.getMenu().getItem(position).setChecked(true);
        oldPosition = position;

        // Hide Ime
        if (oldPosition >= 0) {
            Fragment frag = appSectionsPagerAdapter.getItem(oldPosition);
            if (frag instanceof BlobDescriptorListFragment) {
                ((BlobDescriptorListFragment) frag).finishActionMode();
            }
        }
        if (oldPosition == 0) {
            View v = this.getCurrentFocus();
            if (v != null) {
                InputMethodManager mgr = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                mgr.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    protected void onPause() {
        //Looks like shown soft input sometimes causes a system ui visibility
        //change event that breaks article activity launched from here out of full screen mode.
        //Hiding it appears to reduce that.
        View focusedView = getCurrentFocus();
        if (focusedView != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        int currentItem = viewPager.getCurrentItem();
        Fragment frag = appSectionsPagerAdapter.getItem(currentItem);
        Log.d(TAG, "current tab: " + currentItem);
        if (frag instanceof BlobDescriptorListFragment) {
            BlobDescriptorListFragment bdFrag = (BlobDescriptorListFragment) frag;
            if (bdFrag.isFilterExpanded()) {
                Log.d(TAG, "Filter is expanded");
                bdFrag.collapseFilter();
                return;
            }
        }
        super.onBackPressed();
    }

    public void displayFab(@DrawableRes int icon, @StringRes int description, View.OnClickListener listener) {
        fab.setImageResource(icon);
        fab.setContentDescription(getString(description));
        fab.setOnClickListener(listener);
        fab.show();
    }

    public void hideFab() {
        fab.hide();
    }

    public static final class BookmarksFragment extends BlobDescriptorListFragment {
        @Override
        String getItemClickAction() {
            return ArticleCollectionActivity.ACTION_BOOKMARKS;
        }

        @Override
        BlobDescriptorList getDescriptorList() {
            return SlobHelper.getInstance().bookmarks;
        }

        @Override
        protected int getEmptyIcon() {
            return R.drawable.ic_bookmarks;
        }

        @Override
        protected String getEmptyText() {
            return getString(R.string.main_empty_bookmarks);
        }

        @Override
        int getDeleteConfirmationItemCountResId() {
            return R.plurals.confirm_delete_bookmark_count;
        }

        @Override
        String getPreferencesNS() {
            return "bookmarks";
        }
    }

    public static class HistoryFragment extends BlobDescriptorListFragment {
        @Override
        String getItemClickAction() {
            return ArticleCollectionActivity.ACTION_HISTORY;
        }

        @Override
        BlobDescriptorList getDescriptorList() {
            return SlobHelper.getInstance().history;
        }

        @Override
        protected int getEmptyIcon() {
            return R.drawable.ic_history;
        }

        @Override
        protected String getEmptyText() {
            return getString(R.string.main_empty_history);
        }

        @Override
        int getDeleteConfirmationItemCountResId() {
            return R.plurals.confirm_delete_history_count;
        }

        @Override
        String getPreferencesNS() {
            return "history";
        }

    }

    public static class AppSectionsPagerAdapter extends FragmentPagerAdapter {
        private final Fragment[] fragments;
        LookupFragment tabLookup;
        BlobDescriptorListFragment tabBookmarks;
        BlobDescriptorListFragment tabHistory;
        DictionaryListFragment tabDictionaries;
        SettingsFragment tabSettings;

        public AppSectionsPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            tabLookup = new LookupFragment();
            tabBookmarks = new BookmarksFragment();
            tabHistory = new HistoryFragment();
            tabDictionaries = new DictionaryListFragment();
            tabSettings = new SettingsFragment();
            fragments = new Fragment[]{tabLookup, tabBookmarks, tabHistory, tabDictionaries, tabSettings};
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            return fragments[i];
        }

        @Override
        public int getCount() {
            return fragments.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "";
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!AppPrefs.autoPasteInLookup()) {
            Log.d(TAG, "Auto-paste is off");
            return;
        }
        if (!hasFocus) {
            Log.d(TAG, "has no focus");
            return;
        }
        CharSequence text = ClipboardUtils.peek(this);
        if (text != null) {
            viewPager.setCurrentItem(0);
            invalidateOptionsMenu();
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event.isCanceled()) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!AppPrefs.useVolumeKeysForNavigation()) {
                return false;
            }
            int current = viewPager.getCurrentItem();
            if (current > 0) {
                viewPager.setCurrentItem(current - 1);
            } else {
                viewPager.setCurrentItem(appSectionsPagerAdapter.getCount() - 1);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!AppPrefs.useVolumeKeysForNavigation()) {
                return false;
            }
            int current = viewPager.getCurrentItem();
            if (current < appSectionsPagerAdapter.getCount() - 1) {
                viewPager.setCurrentItem(current + 1);
            } else {
                viewPager.setCurrentItem(0);
            }
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return AppPrefs.useVolumeKeysForNavigation();
        }
        return super.onKeyDown(keyCode, event);
    }

}
