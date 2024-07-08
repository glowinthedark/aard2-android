package itkach.aard2

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import itkach.slob.Slob
import java.io.FileInputStream

class SlobDescriptor : BaseDescriptor() {
    var path: String? = null
    var tags: Map<String, String> = HashMap()
    var active: Boolean = true
    @JvmField
    var priority: Long = 0
    var blobCount: Long = 0
    var error: String? = null
    var expandDetail: Boolean = false

    @Transient
    private var fileDescriptor: ParcelFileDescriptor? = null

    fun update(s: Slob) {
        this.id = s.id.toString()
        this.path = s.fileURI
        this.tags = s.tags
        this.blobCount = s.blobCount
        this.error = null
    }

    fun load(context: Context): Slob? {
        var slob: Slob? = null

        //File f = new File(path);
        try {
            //slob = new Slob(f);
            val uri = Uri.parse(path)
            //must hold on to ParcelFileDescriptor,
            //otherwise it gets garbage collected and trashes underlying file descriptor
            fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            val fileInputStream = FileInputStream(fileDescriptor!!.fileDescriptor)
            slob = Slob(fileInputStream.channel, path)
            this.update(slob)
        } catch (e: Exception) {
            Log.e(TAG, "Error while opening " + this.path, e)
            error = e.message
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Error while opening " + this.path, e)
            }
            expandDetail = true
            active = false
        }
        return slob
    }

    val label: String
        get() {
            var label = tags["label"]
            if (label == null || label.trim { it <= ' ' }.length == 0) {
                label = "???"
            }
            return label
        }

    companion object {
        @Transient
        private val TAG: String = SlobDescriptor::class.java.simpleName

        //    static SlobDescriptor fromFile(File file) {
        //        SlobDescriptor s = new SlobDescriptor();
        //        s.path = file.getAbsolutePath();
        //        s.load();
        //        return s;
        //    }
        fun fromUri(context: Context, uri: String?): SlobDescriptor {
            val s = SlobDescriptor()
            s.path = uri
            s.load(context)
            return s
        }
    }
}
