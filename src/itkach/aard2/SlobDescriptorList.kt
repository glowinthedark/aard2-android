package itkach.aard2

import itkach.aard2.SlobDescriptor
import itkach.aard2.Util.compare
import itkach.aard2.Util.sort
import itkach.slob.Slob

class SlobDescriptorList internal constructor(
    private val app: Application,
    store: DescriptorStore<SlobDescriptor>?
) : BaseDescriptorList<SlobDescriptor>(
    SlobDescriptor::class.java, store!!
) {
    private val comparator =
        java.util.Comparator<SlobDescriptor> { d1, d2 -> //Dictionaries that are unfavorited
            //go immediately after favorites
            if (d1.priority == 0L && d2.priority == 0L) {
                return@Comparator compare(d2.lastAccess, d1.lastAccess)
            }
            //Favorites are always above other
            if (d1.priority == 0L && d2.priority > 0) {
                return@Comparator 1
            }
            if (d1.priority > 0 && d2.priority == 0L) {
                return@Comparator -1
            }
            //Old favorites are above more recent ones
            compare(d1.priority, d2.priority)
        }

    fun resolve(sd: SlobDescriptor): Slob? {
        return app.getSlob(sd.id)
    }

    fun sort() {
        sort<SlobDescriptor>(this, comparator)
    }

    override fun load() {
        beginUpdate()
        super.load()
        sort()
        endUpdate(true)
    }
}
