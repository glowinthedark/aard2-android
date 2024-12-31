package itkach.aard2

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.widget.SearchView

abstract class BlobDescriptorListFragment : BaseListFragment() {
//    private var icFilter: Drawable? = null
//    private var icClock: Drawable? = null
//    private var icList: Drawable? = null
//    private var icArrowUp: Drawable? = null
//    private var icArrowDown: Drawable? = null

    private var listAdapter: BlobDescriptorListAdapter? = null
    private var deleteConfirmationDialog: AlertDialog? = null

    private var miFilter: MenuItem? = null

    val isFilterExpanded: Boolean
        get() = miFilter != null && miFilter!!.isActionViewExpanded

    fun collapseFilter() {
        if (miFilter != null) {
            miFilter!!.collapseActionView()
        }
    }

    abstract val descriptorList: BlobDescriptorList

    abstract val itemClickAction: String?


    override fun setSelectionMode(selectionMode: Boolean) {
        listAdapter!!.isSelectionMode = selectionMode
    }

    override val selectionMenuId: Int
        get() = R.menu.blob_descriptor_selection

    abstract val deleteConfirmationItemCountResId: Int

    abstract val preferencesNS: String?

    private fun prefs(): SharedPreferences {
        return requireActivity().getSharedPreferences(preferencesNS, Activity.MODE_PRIVATE)
    }

    override fun onSelectionActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        val listView = listView
        val itemId = item?.itemId
        if (itemId == R.id.blob_descriptor_delete) {
            val count = listView.checkedItemCount
            val countStr =
                resources.getQuantityString(deleteConfirmationItemCountResId, count, count)
            val message = getString(R.string.blob_descriptor_confirm_delete, countStr)
            deleteConfirmationDialog = AlertDialog.Builder(activity)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes) { dialog, which ->
                    deleteSelectedItems()
                    mode?.finish()
                    deleteConfirmationDialog = null
                }
                .setNegativeButton(android.R.string.no, null).create()
            deleteConfirmationDialog!!.setOnDismissListener(DialogInterface.OnDismissListener {
                deleteConfirmationDialog = null
            })
            deleteConfirmationDialog!!.show()
            return true
        } else if (itemId == R.id.blob_descriptor_select_all) {
            val itemCount = listView.count
            for (i in itemCount - 1 downTo -1 + 1) {
                listView.setItemChecked(i, true)
            }
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val descriptorList = descriptorList

        val p = this.prefs()

        val sortOrderStr = p.getString(
            PREF_SORT_ORDER,
            BlobDescriptorList.SortOrder.TIME.name
        )
        val sortOrder = BlobDescriptorList.SortOrder.valueOf(
            sortOrderStr!!
        )

        val sortDir = p.getBoolean(PREF_SORT_DIRECTION, false)

        descriptorList.setSort(sortOrder, sortDir)

        listAdapter = BlobDescriptorListAdapter(descriptorList)

        val activity = activity

//        icFilter = IconMaker.actionBar(activity, IconMaker.IC_FILTER)
//        icClock = IconMaker.actionBar(activity, IconMaker.IC_CLOCK)
//        icList = IconMaker.actionBar(activity, IconMaker.IC_LIST)
//        icArrowUp = IconMaker.actionBar(activity, IconMaker.IC_SORT_ASC)
//        icArrowDown = IconMaker.actionBar(activity, IconMaker.IC_SORT_DESC)

        val listView = listView
        listView.onItemClickListener = object : OnItemClickListener {
            override fun onItemClick(
                parent: AdapterView<*>?, view: View,
                position: Int, id: Long
            ) {
                val intent = Intent(
                    activity,
                    ArticleCollectionActivity::class.java
                )
                intent.setAction(itemClickAction)
                intent.putExtra("position", position)
                startActivity(intent)
            }
        }

        setListAdapter(listAdapter)
    }

    protected fun deleteSelectedItems() {
        val checkedItems = listView.checkedItemPositions
        for (i in checkedItems.size() - 1 downTo -1 + 1) {
            val position = checkedItems.keyAt(i)
            val checked = checkedItems[position]
            if (checked) {
                descriptorList.removeAt(position)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.blob_descriptor_list, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        val list = descriptorList

        miFilter = menu.findItem(R.id.action_filter)
        miFilter!!.setIcon(R.drawable.search_24px)
        val searchView = miFilter!!.actionView as SearchView?
        //        View filterActionView = miFilter.getActionView();
//        SearchView searchView = (SearchView) filterActionView
//                .findViewById(R.id.fldFilter);
        searchView!!.queryHint = miFilter!!.title
        searchView.setQuery(list.getFilter(), true)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                val list: BlobDescriptorList = descriptorList
                if (newText != list.getFilter()) {
                    descriptorList.setFilter(newText)
                }
                return true
            }
        })
        setSortOrder(menu.findItem(R.id.action_sort_order), list.sortOrder)
        setAscending(menu.findItem(R.id.action_sort_asc), list.isAscending)

        super.onPrepareOptionsMenu(menu)
    }

    private fun setSortOrder(mi: MenuItem, order: BlobDescriptorList.SortOrder) {
        val icon: Int
        val textRes: Int
        if (order == BlobDescriptorList.SortOrder.TIME) {
            icon = R.drawable.schedule_24px
            textRes = R.string.action_sort_by_time
        } else {
            icon = R.drawable.title_24px
            textRes = R.string.action_sort_by_title
        }
        mi.setIcon(icon)
        mi.setTitle(textRes)
        val p = this.prefs()
        val editor = p.edit()
        editor.putString(PREF_SORT_ORDER, order.name)
        editor.commit()
    }

    private fun setAscending(mi: MenuItem, ascending: Boolean) {
        val icon: Int
        val textRes: Int
        if (ascending) {
            icon = R.drawable.sort_ascending
            textRes = R.string.action_ascending
        } else {
            icon = R.drawable.sort_descending
            textRes = R.string.action_descending
        }
        mi.setIcon(icon)
        mi.setTitle(textRes)
        val p = this.prefs()
        val editor = p.edit()
        editor.putBoolean(PREF_SORT_DIRECTION, ascending)
        editor.commit()
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(mi: MenuItem): Boolean {
        val list = descriptorList
        val itemId = mi.itemId
        if (itemId == R.id.action_sort_asc) {
            list.setSort(!list.isAscending)
            setAscending(mi, list.isAscending)
            return true
        }
        if (itemId == R.id.action_sort_order) {
            if (list.sortOrder == BlobDescriptorList.SortOrder.TIME) {
                list.setSort(BlobDescriptorList.SortOrder.NAME)
            } else {
                list.setSort(BlobDescriptorList.SortOrder.TIME)
            }
            setSortOrder(mi, list.sortOrder)
            return true
        }
        return super.onOptionsItemSelected(mi)
    }


    override fun onPause() {
        super.onPause()
        if (deleteConfirmationDialog != null) {
            deleteConfirmationDialog!!.dismiss()
        }
    }

    companion object {
        private const val PREF_SORT_ORDER = "sortOrder"
        private const val PREF_SORT_DIRECTION = "sortDir"
    }
}
