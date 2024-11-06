package itkach.aard2

import android.database.DataSetObservable
import android.database.DataSetObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ibm.icu.text.Collator
import com.ibm.icu.text.RuleBasedCollator
import com.ibm.icu.text.StringSearch
import itkach.slob.Slob
import itkach.slob.Slob.KeyComparator
import java.text.StringCharacterIterator
import java.util.AbstractList
import java.util.Collections
import java.util.Locale

class BlobDescriptorList @JvmOverloads constructor(
    private val app: Application,
    private val store: DescriptorStore<BlobDescriptor>,
    private val maxSize: Int = 100
) : AbstractList<BlobDescriptor>() {
    private val TAG: String = javaClass.simpleName

    enum class SortOrder {
        TIME, NAME
    }

    private val list: MutableList<BlobDescriptor> = ArrayList()
    private val filteredList: MutableList<BlobDescriptor> = ArrayList()
    private var filter: String?
    var sortOrder: SortOrder
        private set
    var isAscending: Boolean
        private set
    private val dataSetObservable = DataSetObservable()
    private val nameComparatorAsc: Comparator<BlobDescriptor>
    private val nameComparatorDesc: Comparator<BlobDescriptor>
    private val timeComparatorAsc: Comparator<BlobDescriptor>
    private val timeComparatorDesc: Comparator<BlobDescriptor>
    private var comparator: Comparator<BlobDescriptor>? = null
    private val lastAccessComparator: Comparator<BlobDescriptor>
    private val keyComparator: KeyComparator
    private var filterCollator: RuleBasedCollator? = null
    private val handler: Handler

    init {
        this.filter = ""
        keyComparator = Slob.Strength.QUATERNARY.comparator

        nameComparatorAsc = Comparator { b1, b2 -> keyComparator.compare(b1.key, b2.key) }

        nameComparatorDesc = Collections.reverseOrder(nameComparatorAsc)

        timeComparatorAsc = Comparator { b1, b2 -> Util.compare(b1.createdAt, b2.createdAt) }

        timeComparatorDesc = Collections.reverseOrder(timeComparatorAsc)

        lastAccessComparator = Comparator { b1, b2 -> Util.compare(b2.lastAccess, b1.lastAccess) }

        sortOrder = SortOrder.TIME
        isAscending = false
        setSort(sortOrder, isAscending)

        try {
            filterCollator = Collator.getInstance(Locale.ROOT).clone() as RuleBasedCollator
        } catch (e: CloneNotSupportedException) {
            throw RuntimeException(e)
        }
        filterCollator!!.strength = Collator.PRIMARY
        filterCollator!!.isAlternateHandlingShifted = false
        handler = Handler(Looper.getMainLooper())
    }

    fun registerDataSetObserver(observer: DataSetObserver) {
        dataSetObservable.registerObserver(observer)
    }

    fun unregisterDataSetObserver(observer: DataSetObserver) {
        dataSetObservable.unregisterObserver(observer)
    }

    /**
     * Notifies the attached observers that the underlying data has been changed
     * and any View reflecting the data set should refresh itself.
     */
    fun notifyDataSetChanged() {
        filteredList.clear()
        if (filter == null || filter!!.length == 0) {
            filteredList.addAll(this.list)
        } else {
            for (bd in this.list) {
                val stringSearch = StringSearch(
                    filter, StringCharacterIterator(bd!!.key), filterCollator
                )
                val matchPos = stringSearch.first()
                if (matchPos != StringSearch.DONE) {
                    filteredList.add(bd)
                }
            }
        }
        sortOrderChanged()
    }

    private fun sortOrderChanged() {
        Util.sort(this.filteredList, comparator)
        dataSetObservable.notifyChanged()
    }

    /**
     * Notifies the attached observers that the underlying data is no longer
     * valid or available. Once invoked this adapter is no longer valid and
     * should not report further data set changes.
     */
    fun notifyDataSetInvalidated() {
        dataSetObservable.notifyInvalidated()
    }

    fun load() {
        list.addAll(store.load(BlobDescriptor::class.java))
        notifyDataSetChanged()
    }

    private fun doUpdateLastAccess(bd: BlobDescriptor) {
        val t = System.currentTimeMillis()
        val dt = t - bd.lastAccess
        if (dt < 2000) {
            return
        }
        bd.lastAccess = t
        store.save(bd)
    }

    fun updateLastAccess(bd: BlobDescriptor) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            doUpdateLastAccess(bd)
        } else {
            handler.post { doUpdateLastAccess(bd) }
        }
    }

    fun resolveOwner(bd: BlobDescriptor): Slob? {
        var slob = app.getSlob(bd.slobId)
        //        if (slob == null || !slob.file.exists()) {
        if (slob == null) {
            slob = app.findSlob(bd.slobUri)
        }
        return slob
    }

    fun resolve(bd: BlobDescriptor): Slob.Blob? {
        val slob = resolveOwner(bd)
        var blob: Slob.Blob? = null
        if (slob == null) {
            return null
        }
        val slobId = slob.id.toString()
        if (slobId == bd.slobId) {
            blob = Slob.Blob(slob, bd.blobId, bd.key, bd.fragment)
        } else {
            try {
                val result = slob.find(
                    bd.key,
                    Slob.Strength.QUATERNARY
                )
                if (result.hasNext()) {
                    blob = result.next()
                    bd.slobId = slobId
                    bd.blobId = blob.id
                }
            } catch (ex: Exception) {
                Log.w(
                    TAG,
                    String.format(
                        "Failed to resolve descriptor %s (%s) in %s (%s)",
                        bd.blobId, bd.key, slob.id, slob.fileURI
                    ), ex
                )
                blob = null
            }
        }
        if (blob != null) {
            updateLastAccess(bd)
        }
        return blob
    }

    fun createDescriptor(contentUrl: String): BlobDescriptor? {
        Log.d(TAG, "Create descriptor from content url: $contentUrl")
        val uri = Uri.parse(contentUrl)
        val bd = BlobDescriptor.fromUri(uri)
        if (bd != null) {
            val slobUri = app.getSlobURI(bd.slobId)
            Log.d(TAG, "Found slob uri for: " + bd.slobId + " " + slobUri)
            bd.slobUri = slobUri
        }
        return bd
    }

    fun add(contentUrl: String): BlobDescriptor? {
        val bd = createDescriptor(contentUrl) ?: return null
        val index = list.indexOf(bd)
        if (index > -1) {
            return list[index]
        }

        list.add(bd)
        store.save(bd)
        if (list.size > this.maxSize) {
            Util.sort(this.list, lastAccessComparator)
            val lru = list.removeAt(list.size - 1)
            store.delete(lru!!.id)
        }
        notifyDataSetChanged()
        return bd
    }

    fun remove(contentUrl: String): BlobDescriptor? {
        val index = list.indexOf(createDescriptor(contentUrl))
        if (index > -1) {
            return removeByIndex(index)
        }
        return null
    }

    override fun removeAt(index: Int): BlobDescriptor? {
        //FIXME find exact item by uuid or using sorted<->unsorted mapping
        val bd = filteredList[index]
        val realIndex = list.indexOf(bd)
        if (realIndex > -1) {
            return removeByIndex(realIndex)
        }
        return null
    }

    private fun removeByIndex(index: Int): BlobDescriptor? {
        val bd = list.removeAt(index)
        if (bd != null) {
            val removed = store.delete(bd.id)
            Log.d(TAG, String.format("Item (%s) %s removed? %s", bd.key, bd.id, removed))
            if (removed) {
                notifyDataSetChanged()
            }
        }
        return bd
    }

    fun contains(contentUrl: String): Boolean {
        val toFind = createDescriptor(contentUrl)
        for (bd in this.list) {
            if (bd == toFind) {
                Log.d(TAG, "Found exact match, bookmarked")
                return true
            }
            if (bd!!.key == toFind!!.key && bd.slobUri == toFind.slobUri) {
                Log.d(TAG, "Found approximate match, bookmarked")
                return true
            }
        }
        Log.d(TAG, "not bookmarked")
        return false
    }

    fun setFilter(filter: String?) {
        this.filter = filter
        notifyDataSetChanged()
    }

    fun getFilter(): String? {
        return this.filter
    }

    override fun get(location: Int): BlobDescriptor? {
        return filteredList[location]
    }

    override val size: Int
        get() = filteredList.size

    fun setSort(ascending: Boolean) {
        setSort(this.sortOrder, ascending)
    }

    fun setSort(order: SortOrder) {
        setSort(order, this.isAscending)
    }

    fun setSort(order: SortOrder, ascending: Boolean) {
        this.sortOrder = order
        this.isAscending = ascending
        var c: Comparator<BlobDescriptor>? = null
        if (order == SortOrder.NAME) {
            c = if (ascending) nameComparatorAsc else nameComparatorDesc
        }
        if (order == SortOrder.TIME) {
            c = if (ascending) timeComparatorAsc else timeComparatorDesc
        }
        if (c !== comparator) {
            comparator = c
            sortOrderChanged()
        }
    }
}
