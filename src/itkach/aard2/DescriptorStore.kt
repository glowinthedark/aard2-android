package itkach.aard2

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

class DescriptorStore<T : BaseDescriptor>(
    private val mapper: ObjectMapper,
    private val dir: File
) {
    fun load(type: Class<T>): List<T> {
        val result: MutableList<T> = ArrayList()
        val files = dir.listFiles()
        if (files != null) {
            for (f in files) {
                try {
                    val sd = mapper.readValue(f, type)
                    result.add(sd)
                } catch (e: Exception) {
                    val path = f.absolutePath
                    Log.w(TAG, String.format("Loading data from file %s failed", path), e)
                    val deleted = f.delete()
                    Log.w(
                        TAG, String.format(
                            "Attempt to delete corrupted file %s succeeded? %s",
                            path, deleted
                        )
                    )
                }
            }
        }
        return result
    }

    fun save(lst: List<T>) {
        for (item in lst) {
            save(item)
        }
    }

    fun save(item: T) {
        if (item!!.id == null) {
            Log.d(javaClass.name, "Can't save item without id")
            return
        }
        try {
            mapper.writeValue(File(dir, item.id), item)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun delete(itemId: String?): Boolean {
        if (itemId == null) {
            return false
        }
        return File(dir, itemId).delete()
    }

    companion object {
        val TAG: String = DescriptorStore::class.java.simpleName
    }
}
