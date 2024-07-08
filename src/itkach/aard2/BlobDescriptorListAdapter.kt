package itkach.aard2

import android.content.Context
import android.database.DataSetObserver
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import java.text.DateFormat

class BlobDescriptorListAdapter(var list: BlobDescriptorList?) : BaseAdapter() {
    var dateFormat: DateFormat = DateFormat.getDateTimeInstance()
    private val observer: DataSetObserver = object : DataSetObserver() {
        override fun onChanged() {
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            notifyDataSetInvalidated()
        }
    }
    var isSelectionMode: Boolean = false
        set(selectionMode) {
            field = selectionMode
            notifyDataSetChanged()
        }

    init {
        list!!.registerDataSetObserver(observer)
    }

    override fun getCount(): Int {
        synchronized(list!!) {
            return if (list == null) 0 else list!!.size
        }
    }

    override fun getItem(position: Int): Any {
        synchronized(list!!) {
            return list!![position]!!
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = list!![position]
        val timestamp = DateUtils.getRelativeTimeSpanString(
            item!!.createdAt
        )
        val view: View
        if (convertView != null) {
            view = convertView
        } else {
            val inflater = parent.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(
                R.layout.blob_descriptor_list_item, parent,
                false
            )
        }
        val titleView = view
            .findViewById<View>(R.id.blob_descriptor_key) as TextView
        titleView.text = item.key
        val sourceView = view
            .findViewById<View>(R.id.blob_descriptor_source) as TextView
        val slob = list!!.resolveOwner(item)
        sourceView.text = if (slob == null) "???" else slob.tags["label"]
        val timestampView = view
            .findViewById<View>(R.id.blob_descriptor_timestamp) as TextView
        timestampView.text = timestamp
        val cb = view
            .findViewById<View>(R.id.blob_descriptor_checkbox) as CheckBox
        cb.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        return view
    }
}
