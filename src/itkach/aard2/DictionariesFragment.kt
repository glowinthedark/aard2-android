package itkach.aard2

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class DictionariesFragment : BaseListFragment() {
    private var listAdapter: DictionaryListAdapter? = null

    override val emptyIcon: Int
        get() = R.drawable.dictionary_24px

    override val emptyText: CharSequence?
        get() = Html.fromHtml(getString(R.string.main_empty_dictionaries))

    override fun supportsSelection(): Boolean {
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as Application
        listAdapter = DictionaryListAdapter(app.dictionaries, requireActivity())
        setListAdapter(listAdapter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val result = super.onCreateView(inflater, container, savedInstanceState)
        val extraEmptyView =
            inflater.inflate(R.layout.dictionaries_empty_view_extra, container, false)
        val btn = extraEmptyView.findViewById<Button>(R.id.dictionaries_empty_btn_scan)
        //        btn.setCompoundDrawablesWithIntrinsicBounds(
//                IconMaker.list(getActivity(), IconMaker.IC_ADD),
//                null, null, null);
        btn.setOnClickListener { selectDictionaryFiles() }
        val emptyViewLayout = emptyView as LinearLayout
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        emptyViewLayout.addView(extraEmptyView, layoutParams)
        return result
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.dictionaries, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        val activity = activity
        val miAddDictionaries = menu.findItem(R.id.action_add_dictionaries)
        miAddDictionaries.setIcon(R.drawable.add_24px)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_dictionaries) {
            selectDictionaryFiles()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectDictionaryFiles() {
        val intent = Intent()
        intent.setAction(Intent.ACTION_OPEN_DOCUMENT)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.setType("*/*")
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try {
            startActivityForResult(intent, FILE_SELECT_REQUEST)
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Not activity to get content", e)
            Toast.makeText(
                context, R.string.msg_no_activity_to_get_content,
                Toast.LENGTH_LONG
            ).show()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode != FILE_SELECT_REQUEST) {
            Log.d(TAG, "Unknown request code: $requestCode")
            return
        }

        val dataUri = intent?.data
        Log.d(
            TAG,
            String.format(
                "req code %s, result code: %s, data: %s",
                requestCode,
                resultCode,
                dataUri
            )
        )

        if (resultCode == Activity.RESULT_OK && intent != null) {
            val app = (requireActivity().application as Application)
            val selection: MutableList<Uri> = ArrayList()
            if (dataUri != null) {
                selection.add(dataUri)
            }
            val clipData = intent.clipData
            if (clipData != null) {
                val itemCount = clipData.itemCount
                for (i in 0 until itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    selection.add(uri)
                }
            }
            for (uri in selection) {
                requireActivity().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                app.addDictionary(uri)
            }
        }
    }

    companion object {
        private val TAG: String = DictionariesFragment::class.java.simpleName

        const val FILE_SELECT_REQUEST: Int = 17
    }
}
