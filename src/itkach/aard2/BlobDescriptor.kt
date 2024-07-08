package itkach.aard2

import android.net.Uri
import java.util.UUID

class BlobDescriptor : BaseDescriptor() {
    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (if ((blobId == null)) 0 else blobId.hashCode())
        result = (prime * result
                + (if ((fragment == null)) 0 else fragment.hashCode()))
        result = prime * result + (if ((slobId == null)) 0 else slobId.hashCode())
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as BlobDescriptor
        if (blobId == null) {
            if (other.blobId != null) return false
        } else if (blobId != other.blobId) return false
        if (fragment == null) {
            if (other.fragment != null) return false
        } else if (fragment != other.fragment) return false
        if (slobId == null) {
            if (other.slobId != null) return false
        } else if (slobId != other.slobId) return false
        return true
    }

    var slobId: String? = null
    var slobUri: String? = null
    var blobId: String? = null
    lateinit var key: String
    lateinit var fragment: String

    companion object {
        fun fromUri(uri: Uri): BlobDescriptor? {
            val bd = BlobDescriptor()
            bd.id = UUID.randomUUID().toString()
            bd.createdAt = System.currentTimeMillis()
            bd.lastAccess = bd.createdAt
            val pathSegments = uri.pathSegments
            val segmentCount = pathSegments.size
            if (segmentCount < 3) {
                return null
            }
            bd.slobId = pathSegments[1]
            val key = StringBuilder()
            for (i in 2 until segmentCount) {
                if (key.length > 0) {
                    key.append("/")
                }
                key.append(pathSegments[i])
            }
            bd.key = key.toString()
            bd.blobId = uri.getQueryParameter("blob")
            bd.fragment = uri.fragment.toString()
            return bd
        }
    }
}
