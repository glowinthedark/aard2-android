package itkach.aard2

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsListAdapter internal constructor(private val fragment: Fragment) : BaseAdapter(),
    OnSharedPreferenceChangeListener {
    private val context: Activity?
    private val app: Application

    private var userStyleNames: List<String>? = null
    private var userStyleData: Map<String, *>? = null
    private val userStylePrefs: SharedPreferences
    private val onDeleteUserStyle: View.OnClickListener


    init {
        this.context = fragment.activity
        this.app = context!!.application as Application
        this.userStylePrefs = context.getSharedPreferences(
            "userStyles", Activity.MODE_PRIVATE
        )
        userStylePrefs.registerOnSharedPreferenceChangeListener(this)

        this.onDeleteUserStyle = View.OnClickListener { view ->
            val name = view.tag as String
            deleteUserStyle(name)
        }
    }

    override fun getCount(): Int {
        return 8
    }

    override fun getItem(i: Int): Any {
        return i
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    override fun getViewTypeCount(): Int {
        return count
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun getView(i: Int, convertView: View?, parent: ViewGroup): View? {
        return when (i) {
            POS_UI_THEME -> getUIThemeSettingsView(convertView, parent)
            POS_REMOTE_CONTENT -> getRemoteContentSettingsView(convertView, parent)
            POS_FAV_RANDOM -> getFavRandomSwitchView(convertView, parent)
            POS_USE_VOLUME_FOR_NAV -> getUseVolumeForNavView(convertView, parent)
            POS_AUTO_PASTE -> getAutoPasteView(convertView, parent)
            POS_USER_STYLES -> getUserStylesView(convertView, parent)
            POS_CLEAR_CACHE -> getClearCacheView(convertView, parent)
            POS_ABOUT -> getAboutView(convertView, parent)
            else -> null
        }
    }

    private fun getUIThemeSettingsView(convertView: View?, parent: ViewGroup): View {
        val view: View
        if (convertView != null) {
            view = convertView
        } else {
            val inflater = parent.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(
                R.layout.settings_ui_theme_item, parent,
                false
            )

            val prefs = app.prefs()

            val currentValue = prefs.getString(
                Application.PREF_UI_THEME,
                Application.PREF_UI_THEME_SYSTEM
            )
            Log.d("Settings", Application.PREF_UI_THEME + " current value: " + currentValue)

            val clickListener = View.OnClickListener { view ->
                val editor = prefs.edit()
                var value: String? = null
                val id = view.id
                if (id == R.id.setting_ui_theme_system) {
                    value = Application.PREF_UI_THEME_SYSTEM
                } else if (id == R.id.setting_ui_theme_light) {
                    value = Application.PREF_UI_THEME_LIGHT
                } else if (id == R.id.setting_ui_theme_dark) {
                    value = Application.PREF_UI_THEME_DARK
                }
                Log.d("Settings", Application.PREF_UI_THEME + ": " + value)
                if (value != null) {
                    editor.putString(Application.PREF_UI_THEME, value)
                    editor.commit()
                }
                context!!.recreate()
            }
            val btnSystem = view
                .findViewById<View>(R.id.setting_ui_theme_system) as RadioButton
            val btnLight = view
                .findViewById<View>(R.id.setting_ui_theme_light) as RadioButton
            val btnDark = view
                .findViewById<View>(R.id.setting_ui_theme_dark) as RadioButton
            btnSystem.setOnClickListener(clickListener)
            btnLight.setOnClickListener(clickListener)
            btnDark.setOnClickListener(clickListener)
            btnSystem.isChecked = currentValue == Application.PREF_UI_THEME_SYSTEM
            btnLight.isChecked = currentValue == Application.PREF_UI_THEME_LIGHT
            btnDark.isChecked = currentValue == Application.PREF_UI_THEME_DARK
        }

        return view
    }

    private fun getFavRandomSwitchView(convertView: View?, parent: ViewGroup): View {
        val view: View
        val inflater = parent.context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val app = context!!.application as Application
        if (convertView != null) {
            view = convertView
        } else {
            view = inflater.inflate(
                R.layout.settings_fav_random_search, parent,
                false
            )
            val toggle = view.findViewById<View>(R.id.setting_fav_random_search) as MaterialSwitch
            toggle.setOnClickListener {
                val currentValue = app.isOnlyFavDictsForRandomLookup
                val newValue = !currentValue
                app.isOnlyFavDictsForRandomLookup = newValue
                toggle.isChecked = newValue
            }
        }
        val currentValue = app.isOnlyFavDictsForRandomLookup
        val toggle = view.findViewById<View>(R.id.setting_fav_random_search) as MaterialSwitch
        toggle.isChecked = currentValue
        return view
    }

    private fun getUseVolumeForNavView(convertView: View?, parent: ViewGroup): View {
        val view: View
        val inflater = parent.context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val app = context!!.application as Application
        if (convertView != null) {
            view = convertView
        } else {
            view = inflater.inflate(
                R.layout.settings_use_volume_for_nav, parent,
                false
            )
            val toggle = view.findViewById<View>(R.id.setting_use_volume_for_nav) as MaterialSwitch
            toggle.setOnClickListener {
                val currentValue = app.useVolumeForNav()
                val newValue = !currentValue
                app.setUseVolumeForNav(newValue)
                toggle.isChecked = newValue
            }
        }
        val currentValue = app.useVolumeForNav()
        val toggle = view.findViewById<View>(R.id.setting_use_volume_for_nav) as MaterialSwitch
        toggle.isChecked = currentValue
        return view
    }

    private fun getAutoPasteView(convertView: View?, parent: ViewGroup): View {
        val view: View
        val inflater = parent.context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val app = context!!.application as Application
        if (convertView != null) {
            view = convertView
        } else {
            view = inflater.inflate(
                R.layout.settings_auto_paste, parent,
                false
            )
            val toggle = view.findViewById<View>(R.id.setting_auto_paste) as MaterialSwitch
            toggle.setOnClickListener {
                val currentValue = app.autoPaste()
                val newValue = !currentValue
                app.setAutoPaste(newValue)
                toggle.isChecked = newValue
            }
        }
        val currentValue = app.autoPaste()
        val toggle = view.findViewById<View>(R.id.setting_auto_paste) as MaterialSwitch
        toggle.isChecked = currentValue
        return view
    }


    private fun getUserStylesView(convertView: View?, parent: ViewGroup): View {
        val view: View
        val inflater = parent.context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        if (convertView != null) {
            view = convertView
        } else {
            this.userStyleData = userStylePrefs.all
            this.userStyleNames = ArrayList((userStyleData as MutableMap<String, *>?)?.keys)
            Util.sort(this.userStyleNames)

            view = inflater.inflate(
                R.layout.settings_user_styles_item, parent,
                false
            )
            val btnAdd = view.findViewById<MaterialButton>(R.id.setting_btn_add_user_style)
            btnAdd.setOnClickListener {
                val intent = Intent()
                intent.setAction(Intent.ACTION_GET_CONTENT)
                intent.setType("text/*")
                val chooser = Intent.createChooser(intent, "Select CSS file")
                try {
                    fragment.startActivityForResult(chooser, CSS_SELECT_REQUEST)
                } catch (e: ActivityNotFoundException) {
                    Log.d(TAG, "Not activity to get content", e)
                    Toast.makeText(
                        context, R.string.msg_no_activity_to_get_content,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }


        val emptyView = view.findViewById<View>(R.id.setting_user_styles_empty)
        emptyView.visibility = if (userStyleNames!!.size == 0) View.VISIBLE else View.GONE

        val userStyleListLayout =
            view.findViewById<View>(R.id.setting_user_styles_list) as LinearLayout
        userStyleListLayout.removeAllViews()
        for (i in userStyleNames!!.indices) {
            val styleItemView = inflater.inflate(
                R.layout.user_styles_list_item, parent,
                false
            )
            val btnDelete =
                styleItemView.findViewById<View>(R.id.user_styles_list_btn_delete) as ImageView
            btnDelete.setOnClickListener(onDeleteUserStyle)

            val name = userStyleNames!![i]

            btnDelete.tag = name

            val nameView = styleItemView.findViewById<View>(R.id.user_styles_list_name) as TextView
            nameView.text = name

            userStyleListLayout.addView(styleItemView)
        }

        return view
    }

    private fun deleteUserStyle(name: String) {
        val message = context!!.getString(R.string.setting_user_style_confirm_forget, name)
        AlertDialog.Builder(context)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("")
            .setMessage(message)
            .setPositiveButton(android.R.string.yes) { dialog, which ->
                Log.d(TAG, "Deleting user style $name")
                val edit = userStylePrefs.edit()
                edit.remove(name)
                edit.commit()
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, s: String?) {
        this.userStyleData = sharedPreferences.all
        this.userStyleNames = ArrayList(userStyleData?.keys)
        Util.sort(userStyleNames)
        notifyDataSetChanged()
    }

    private fun getRemoteContentSettingsView(convertView: View?, parent: ViewGroup): View {
        val view: View
        if (convertView != null) {
            view = convertView
        } else {
            val inflater = parent.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(
                R.layout.settings_remote_content_item, parent,
                false
            )

            val prefs = view.context.getSharedPreferences(
                ArticleWebView.PREF, Activity.MODE_PRIVATE
            )

            val currentValue = prefs.getString(
                ArticleWebView.PREF_REMOTE_CONTENT,
                ArticleWebView.PREF_REMOTE_CONTENT_WIFI
            )
            Log.d("Settings", "Remote content, current value: $currentValue")

            val clickListener = View.OnClickListener { view ->
                val editor = prefs.edit()
                var value: String? = null
                val id = view.id
                if (id == R.id.setting_remote_content_always) {
                    value = ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS
                } else if (id == R.id.setting_remote_content_wifi) {
                    value = ArticleWebView.PREF_REMOTE_CONTENT_WIFI
                } else if (id == R.id.setting_remote_content_never) {
                    value = ArticleWebView.PREF_REMOTE_CONTENT_NEVER
                }
                Log.d("Settings", "Remote content: $value")
                if (value != null) {
                    editor.putString(ArticleWebView.PREF_REMOTE_CONTENT, value)
                    editor.commit()
                }
            }
            val btnAlways = view
                .findViewById<View>(R.id.setting_remote_content_always) as RadioButton
            val btnWiFi = view
                .findViewById<View>(R.id.setting_remote_content_wifi) as RadioButton
            val btnNever = view
                .findViewById<View>(R.id.setting_remote_content_never) as RadioButton
            btnAlways.setOnClickListener(clickListener)
            btnWiFi.setOnClickListener(clickListener)
            btnNever.setOnClickListener(clickListener)
            btnAlways.isChecked = currentValue == ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS
            btnWiFi.isChecked = currentValue == ArticleWebView.PREF_REMOTE_CONTENT_WIFI
            btnNever.isChecked = currentValue == ArticleWebView.PREF_REMOTE_CONTENT_NEVER
        }

        return view
    }

    private fun getClearCacheView(convertView: View?, parent: ViewGroup): View {
        val view: View
        if (convertView != null) {
            view = convertView
        } else {
            val context = parent.context
            val inflater = context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(
                R.layout.settings_clear_cache_item, parent,
                false
            )
        }
        return view
    }

    private fun getAboutView(convertView: View?, parent: ViewGroup): View {
        val view: View
        if (convertView != null) {
            view = convertView
        } else {
            val context = parent.context
            val inflater = context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(
                R.layout.settings_about_item, parent,
                false
            )

            val appName = context.getString(R.string.app_name)

            val title = context.getString(R.string.setting_about, appName)

            val titleView = view.findViewById<View>(R.id.setting_about) as TextView
            titleView.text = title

            val licenseName = context.getString(R.string.application_license_name)
            val licenseUrl = context.getString(R.string.application_license_url)
            val license = context.getString(R.string.application_license, licenseUrl, licenseName)
            val licenseView = view.findViewById<View>(R.id.application_license) as TextView
            licenseView.setOnClickListener {
                val uri = Uri.parse(licenseUrl)
                val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(browserIntent)
            }
            licenseView.text = Html.fromHtml(license.trim { it <= ' ' })

            val manager = context.packageManager
            var versionName: String
            try {
                val info = manager.getPackageInfo(context.packageName, 0)
                versionName = info.versionName.toString()
            } catch (e: PackageManager.NameNotFoundException) {
                versionName = "?"
            }

            val version = context.getString(R.string.application_version, versionName)
            val versionView = view.findViewById<View>(R.id.application_version) as TextView
            versionView.text = Html.fromHtml(version)
        }
        return view
    }

    companion object {
        const val CSS_SELECT_REQUEST: Int = 13

        private val TAG: String = SettingsListAdapter::class.java.simpleName
        const val POS_UI_THEME: Int = 0
        const val POS_REMOTE_CONTENT: Int = 1
        const val POS_FAV_RANDOM: Int = 2
        const val POS_USE_VOLUME_FOR_NAV: Int = 3
        const val POS_AUTO_PASTE: Int = 4
        const val POS_USER_STYLES: Int = 5
        const val POS_CLEAR_CACHE: Int = 6
        const val POS_ABOUT: Int = 7
    }
}
