package itkach.aard2

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import itkach.aard2.BlobListAdapter
import itkach.slob.Slob
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BlobListAdapter @JvmOverloads constructor(
    context: Context,
    private val chunkSize: Int = 20,
    private val loadMoreThreashold: Int = 10
) : BaseAdapter() {
    var mainHandler: Handler = Handler(context.mainLooper)
    var list: MutableList<Slob.Blob>? = ArrayList(chunkSize)
    var iter: Iterator<Slob.Blob>? = null
    var executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var loadingChunk: Boolean = false

    var MAX_SIZE: Int = 10000

    fun setData(lookupResultsIter: Iterator<Slob.Blob>?) {
        mainHandler.post {
            list!!.clear()
            notifyDataSetChanged()
        }
        this.iter = lookupResultsIter
        loadChunk()
    }

    private fun loadChunk() {
        if (loadingChunk || !iter!!.hasNext()) {
            return
        }
        loadingChunk = true
        Log.d(TAG, "loadChunk")
        GlobalScope.async {
            val t0 = System.currentTimeMillis()
            var count = 0
            val chunkList: MutableList<Slob.Blob> = LinkedList()

            while (iter!!.hasNext() && count < chunkSize && list!!.size <= MAX_SIZE) {
                count++
                val b = iter!!.next()
                chunkList.add(b)
            }

            mainHandler.post {
                list!!.addAll(chunkList)
                notifyDataSetChanged()
            }
            loadingChunk = false
            Log.d(
                TAG,
                String.format(
                    "Loaded chunk of %d (adapter size %d) in %d ms",
                    count, list!!.size, (System.currentTimeMillis() - t0)
                )
            )
        }.start()

    }

//    private fun loadChunk() {
//        if (!iter!!.hasNext()) {
//            return
//        }
//        executor.execute { loadChunkSync() }
//    }

    override fun getCount(): Int {
        return if (list == null) 0 else list!!.size
    }

    override fun getItem(position: Int): Any {
        val result: Any = list!![position]
        return result
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun maybeLoadMore(position: Int) {
        if (position >= list!!.size - loadMoreThreashold) {
            loadChunk()
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = list!![position]
        val slob = item.owner
        maybeLoadMore(position)

        val view: View
        if (convertView != null) {
            view = convertView
        } else {
            val inflater = parent.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.blob_descriptor_list_item, parent, false)
        }

        val titleView = view.findViewById<View>(R.id.blob_descriptor_key) as TextView
        titleView.text = item.key
        val sourceView = view.findViewById<View>(R.id.blob_descriptor_source) as TextView
        sourceView.text = if (slob == null) "???" else slob.tags["label"]
        val timestampView = view.findViewById<View>(R.id.blob_descriptor_timestamp) as TextView
        timestampView.text = ""
        timestampView.visibility = View.GONE
        return view
    }

    companion object {
        private val TAG: String = BlobListAdapter::class.java.simpleName
    }
}
