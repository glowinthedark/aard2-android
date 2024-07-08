package itkach.aard2

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.database.DataSetObserver
import android.net.Uri
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.materialswitch.MaterialSwitch
import itkach.aard2.Util.isBlank
import java.util.Locale

class DictionaryListAdapter internal constructor(
    private val data: SlobDescriptorList,
    private val context: Activity
) : BaseAdapter() {
    private val openUrlOnClick: View.OnClickListener

    init {
        val observer: DataSetObserver = object : DataSetObserver() {
            override fun onChanged() {
                notifyDataSetChanged()
            }

            override fun onInvalidated() {
                notifyDataSetInvalidated()
            }
        }
        data.registerDataSetObserver(observer)

        openUrlOnClick = View.OnClickListener { v ->
            val url = v.tag as String
            if (!isBlank(url)) {
                try {
                    val uri = Uri.parse(url)
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                    v.context.startActivity(browserIntent)
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to launch browser with url $url", e)
                }
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val desc = getItem(position) as SlobDescriptor
        val label = desc.label
        var fileName: String?
        try {
            val documentFile = DocumentFile.fromSingleUri(parent.context, Uri.parse(desc.path))
            fileName = documentFile!!.name
        } catch (ex: Exception) {
            fileName = desc.path
            Log.w(TAG, "Couldn't parse get document file name from uri" + desc.path, ex)
        }
        val blobCount = desc.blobCount
        val available = data.resolve(desc) != null
        val view: View
        if (convertView != null) {
            view = convertView
        } else {
            val inflater = parent.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(
                R.layout.dictionary_list_item, parent,
                false
            )

            val licenseView = view.findViewById<View>(R.id.dictionary_license)
            licenseView.setOnClickListener(openUrlOnClick)

            val sourceView = view.findViewById<View>(R.id.dictionary_source)
            sourceView.setOnClickListener(openUrlOnClick)

            val activeSwitch = view.findViewById<View>(R.id.dictionary_active) as MaterialSwitch
            activeSwitch.setOnClickListener { view ->
                val activeSwitch = view as Switch
                val position = view.getTag() as Int
                val desc = data[position]
                desc.active = activeSwitch.isChecked
                data[position] = desc
            }

            val btnForget = view
                .findViewById<View>(R.id.dictionary_btn_forget)
            btnForget.setOnClickListener { view ->
                val position = view.tag as Int
                forget(position)
            }

            val detailToggle = View.OnClickListener { view ->
                val position = view.tag as Int
                val desc = data[position]
                desc.expandDetail = !desc.expandDetail
                data[position] = desc
            }

            val viewDetailToggle = view
                .findViewById<View>(R.id.dictionary_detail_toggle)
            viewDetailToggle.setOnClickListener(detailToggle)

            val toggleFavListener = View.OnClickListener { view ->
                val position = view.tag as Int
                val desc = data[position]
                val currentTime = System.currentTimeMillis()
                if (desc.priority == 0L) {
                    desc.priority = currentTime
                } else {
                    desc.priority = 0
                }
                desc.lastAccess = currentTime
                data.beginUpdate()
                data[position] = desc
                data.sort()
                data.endUpdate(true)
            }
            val btnToggleFav = view
                .findViewById<View>(R.id.dictionary_btn_toggle_fav)
            btnToggleFav.setOnClickListener(toggleFavListener)
            val dictLabel = view
                .findViewById<View>(R.id.dictionary_label)
            dictLabel.setOnClickListener(toggleFavListener)
        }

        val r = parent.resources

        val switchView = view
            .findViewById<View>(R.id.dictionary_active) as MaterialSwitch

        switchView.isChecked = desc.active
        switchView.tag = position

        val titleView = view
            .findViewById<View>(R.id.dictionary_label) as TextView
        titleView.isEnabled = available
        titleView.text = label
        titleView.tag = position

        val detailView = view.findViewById<View>(R.id.dictionary_details)
        detailView.visibility = if (desc.expandDetail) View.VISIBLE else View.GONE

        setupBlobCountView(desc, blobCount, available, view, r)
        setupCopyrightView(desc, available, view)
        setupLicenseView(desc, available, view)
        setupSourceView(desc, available, view)
        setupPathView(fileName, available, view)
        setupErrorView(desc, view)

        val btnToggleDetail = view
            .findViewById<View>(R.id.dictionary_btn_toggle_detail) as ImageView
        btnToggleDetail.setImageResource(if (desc.expandDetail) R.drawable.keyboard_arrow_up_24px else R.drawable.keyboard_arrow_down_24px)

        val viewDetailToggle = view
            .findViewById<View>(R.id.dictionary_detail_toggle)
        viewDetailToggle.tag = position

        val btnForget = view
            .findViewById<View>(R.id.dictionary_btn_forget)
        btnForget.tag = position

        val btnToggleFav = view
            .findViewById<View>(R.id.dictionary_btn_toggle_fav) as ImageView
        btnToggleFav.setImageResource(if (desc.priority > 0) R.drawable.star_fill_24px else R.drawable.star_24px)
        btnToggleFav.tag = position
        return view
    }

    private fun setupPathView(path: String?, available: Boolean, view: View) {
        val pathRow = view.findViewById<View>(R.id.dictionary_path_row)

        val pathView = view.findViewById<View>(R.id.dictionary_path) as TextView
        pathView.text = path

        pathRow.isEnabled = available
    }

    private fun setupErrorView(desc: SlobDescriptor, view: View) {
        val errorRow = view.findViewById<View>(R.id.dictionary_error_row)

        val errorView = view
            .findViewById<View>(R.id.dictionary_error) as TextView
        errorView.text = desc.error

        errorRow.visibility =
            if (desc.error == null) View.GONE else View.VISIBLE
    }

    private fun setupBlobCountView(
        desc: SlobDescriptor,
        blobCount: Long,
        available: Boolean,
        view: View,
        r: Resources
    ) {
        val blobCountView = view
            .findViewById<View>(R.id.dictionary_blob_count) as TextView
        blobCountView.isEnabled = available
        blobCountView.visibility = if (desc.error == null) View.VISIBLE else View.GONE

        blobCountView.text = String.format(
            Locale.getDefault(),
            r.getQuantityString(R.plurals.dict_item_count, blobCount.toInt()), blobCount
        )
    }

    private fun setupCopyrightView(desc: SlobDescriptor, available: Boolean, view: View) {
        val copyrightRow = view.findViewById<View>(R.id.dictionary_copyright_row)

        val copyrightIcon = view.findViewById<View>(R.id.dictionary_copyright_icon) as ImageView

        val copyrightView = view.findViewById<View>(R.id.dictionary_copyright) as TextView
        val copyright = desc.tags["copyright"]
        copyrightView.text = copyright

        copyrightRow.visibility =
            if (isBlank(copyright)) View.GONE else View.VISIBLE
        copyrightRow.isEnabled = available
    }

    private fun setupSourceView(desc: SlobDescriptor, available: Boolean, view: View) {
        val sourceRow = view.findViewById<View>(R.id.dictionary_license_row)

        val sourceIcon = view.findViewById<View>(R.id.dictionary_source_icon) as ImageView

        val sourceView = view.findViewById<View>(R.id.dictionary_source) as TextView
        val source = desc.tags["source"]
        val sourceHtml: CharSequence = Html.fromHtml(String.format(hrefTemplate, source, source))
        sourceView.text = sourceHtml
        sourceView.tag = source

        val visibility = if (isBlank(source)) View.GONE else View.VISIBLE
        //Setting visibility on layout seems to have no effect
        //if one of the children is a link
        sourceIcon.visibility = visibility
        sourceView.visibility = visibility
        sourceRow.visibility = visibility
        sourceRow.isEnabled = available
    }

    private fun setupLicenseView(desc: SlobDescriptor, available: Boolean, view: View) {
        val licenseRow = view.findViewById<View>(R.id.dictionary_license_row)

        val licenseIcon = view.findViewById<View>(R.id.dictionary_license_icon) as ImageView

        val licenseView = view.findViewById<View>(R.id.dictionary_license) as TextView
        var licenseName = desc.tags["license.name"]
        val licenseUrl = desc.tags["license.url"]
        val license: CharSequence?
        if (isBlank(licenseUrl)) {
            license = licenseName
        } else {
            if (isBlank(licenseName)) {
                licenseName = licenseUrl
            }
            license = Html.fromHtml(String.format(hrefTemplate, licenseUrl, licenseName))
        }
        licenseView.text = license
        licenseView.tag = licenseUrl

        val visibility =
            if ((isBlank(licenseName) && isBlank(licenseUrl))) View.GONE else View.VISIBLE
        licenseIcon.visibility = visibility
        licenseView.visibility = visibility
        licenseRow.visibility = visibility
        licenseRow.isEnabled = available
    }

    private fun forget(position: Int) {
        val desc = data[position]
        val label = desc.label
        val message = context.getString(R.string.dictionaries_confirm_forget, label)
        val deleteConfirmationDialog = AlertDialog.Builder(context)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("")
            .setMessage(message)
            .setPositiveButton(android.R.string.yes) { _, _ -> data.removeAt(position) }
            .setNegativeButton(android.R.string.no, null)
            .create()
//        deleteConfirmationDialog.setOnDismissListener(DialogInterface.OnDismissListener {
//            deleteConfirmationDialog = null
//        })
        deleteConfirmationDialog.show()
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItem(position: Int): Any {
        return data[position]
    }

    override fun getCount(): Int {
        return data.size
    }

    companion object {
        private val TAG: String = DictionaryListAdapter::class.java.name

        private const val hrefTemplate = "<a href=\'%1\$s\'>%2\$s</a>"
    }
}
