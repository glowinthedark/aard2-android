package itkach.aard2

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.ListView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.ListFragment
import itkach.aard2.Application.Companion.readTextFile
import itkach.aard2.Application.FileTooBigException

class SettingsFragment : ListFragment() {
    private var listAdapter: SettingsListAdapter? = null
    private var clearCacheConfirmationDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listAdapter = SettingsListAdapter(this)
        setListAdapter(listAdapter)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        if (position == SettingsListAdapter.POS_CLEAR_CACHE) {
            val builder = AlertDialog.Builder(activity)
            builder.setMessage(R.string.confirm_clear_cached_content)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    val webView = WebView(requireActivity())
                    webView.clearCache(true)
                }
                .setNegativeButton(android.R.string.no) { _, _ ->
                    // User cancelled the dialog
                }
            clearCacheConfirmationDialog = builder.create()
            clearCacheConfirmationDialog!!.setOnDismissListener(DialogInterface.OnDismissListener {
                clearCacheConfirmationDialog = null
            })
            clearCacheConfirmationDialog!!.show()
            return
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != SettingsListAdapter.CSS_SELECT_REQUEST) {
            Log.d(TAG, String.format("Unknown request code: %d", requestCode))
            return
        }
        val dataUri = data?.data
        Log.d(
            TAG,
            String.format(
                "req code %s, result code: %s, data: %s",
                requestCode,
                resultCode,
                dataUri
            )
        )
        if (resultCode == Activity.RESULT_OK && dataUri != null) {
            try {
                val `is` = requireActivity().contentResolver.openInputStream(dataUri)
                val documentFile = DocumentFile.fromSingleUri(requireContext(), dataUri)
                var fileName = documentFile!!.name
                val app = requireActivity().application as Application
                var userCss = readTextFile(`is`, 256 * 1024)
                val pathSegments = dataUri.pathSegments
                Log.d(TAG, fileName!!)
                Log.d(TAG, userCss)
                val lastIndexOfDot = fileName.lastIndexOf(".")
                if (lastIndexOfDot > -1) {
                    fileName = fileName.substring(0, lastIndexOfDot)
                }
                if (fileName.length == 0) {
                    fileName = "???"
                }
                val prefs = requireActivity().getSharedPreferences(
                    "userStyles", Activity.MODE_PRIVATE
                )

                userCss = userCss.replace("\r", "").replace("\n", "\\n")

                val editor = prefs.edit()
                editor.putString(fileName, userCss)
                val saved = editor.commit()
                if (!saved) {
                    Toast.makeText(
                        activity, R.string.msg_failed_to_store_user_style,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: FileTooBigException) {
                Log.d(TAG, "File is too big: $dataUri")
                Toast.makeText(
                    activity, R.string.msg_file_too_big,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.d(TAG, "Failed to load: $dataUri", e)
                Toast.makeText(
                    activity, R.string.msg_failed_to_read_file,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    override fun onPause() {
        super.onPause()
        if (clearCacheConfirmationDialog != null) {
            clearCacheConfirmationDialog!!.dismiss()
        }
    }

    companion object {
        private val TAG: String = SettingsFragment::class.java.simpleName
    }
}
