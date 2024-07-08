package itkach.aard2

import android.database.DataSetObservable
import android.database.DataSetObserver
import android.util.Log
import java.util.AbstractList

abstract class BaseDescriptorList<T : BaseDescriptor>(
    private val typeParameterClass: Class<T>,
    private val store: DescriptorStore<T>
) : AbstractList<T>() {
    private val dataSetObservable = DataSetObservable()
    private val list: MutableList<T> = ArrayList()

    private var updating = 0

    fun registerDataSetObserver(observer: DataSetObserver) {
        dataSetObservable.registerObserver(observer)
    }

    fun unregisterDataSetObserver(observer: DataSetObserver) {
        dataSetObservable.unregisterObserver(observer)
    }

    fun beginUpdate() {
        Log.d(javaClass.name, "beginUpdate")
        updating++
    }

    fun endUpdate(changed: Boolean) {
        Log.d(javaClass.name, "endUpdate, changed? $changed")
        updating--
        if (changed) {
            notifyChanged()
        }
    }

    protected fun notifyChanged() {
        if (this.updating == 0) {
            dataSetObservable.notifyChanged()
        }
    }

    open fun load() {
        this.addAll(store.load(typeParameterClass))
    }

    override fun get(i: Int): T {
        return list[i]
    }

    override fun set(location: Int, value: T): T {
        val result = list.set(location, value)
        store.save(value)
        notifyChanged()
        return result
    }

    override val size: Int
        get() = list.size

    override fun add(location: Int, value: T) {
        list.add(location, value)
        store.save(value)
        notifyChanged()
    }

    override fun addAll(location: Int, collection: Collection<T>): Boolean {
        beginUpdate()
        val result = super.addAll(location, collection)
        endUpdate(result)
        return result
    }

    override fun addAll(collection: Collection<T>): Boolean {
        beginUpdate()
        val result = super.addAll(collection)
        endUpdate(result)
        return result
    }

    override fun removeAt(location: Int): T {
        val result = list.removeAt(location)
        store.delete(result!!.id)
        notifyChanged()
        return result
    }

    override fun clear() {
        val wasEmpty = size == 0
        beginUpdate()
        super.clear()
        endUpdate(!wasEmpty)
    }
}
