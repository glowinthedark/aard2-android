package itkach.aard2

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView.MultiChoiceModeListener
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.ListFragment

abstract class BaseListFragment : ListFragment() {
    protected lateinit var  emptyView: View
    var actionMode: ActionMode? = null

    abstract val emptyIcon: Int

    abstract val emptyText: CharSequence?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        //        setRetainInstance(true);
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        emptyView = inflater.inflate(R.layout.empty_view, container, false)
        val emptyText = (emptyView.findViewById<View>(R.id.empty_text) as TextView)
        emptyText.movementMethod = LinkMovementMethod.getInstance()
        emptyText.text = this.emptyText
        val emptyIcon = emptyView.findViewById<View>(R.id.empty_icon) as ImageView
        emptyIcon.setImageResource(this.emptyIcon)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    protected open fun setSelectionMode(selectionMode: Boolean) {}

    protected open val selectionMenuId: Int
        get() = 0

    protected open fun onSelectionActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        return false
    }

    protected open fun supportsSelection(): Boolean {
        return true
    }

    fun finishActionMode(): Boolean {
        if (actionMode != null) {
            actionMode!!.finish()
            return true
        }
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val listView = listView
        listView.emptyView = emptyView
        (listView.parent as ViewGroup).addView(emptyView, 0)

        if (supportsSelection()) {
            listView.itemsCanFocus = false
            listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
            listView.setMultiChoiceModeListener(object : MultiChoiceModeListener {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    actionMode = mode
                    val inflater = mode.menuInflater
                    inflater.inflate(selectionMenuId, menu)
                    setSelectionMode(true)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    return false
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    return onSelectionActionItemClicked(mode, item)
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    setSelectionMode(false)
                    actionMode = null
                }

                override fun onItemCheckedStateChanged(
                    mode: ActionMode,
                    position: Int, id: Long, checked: Boolean
                ) {
                }
            })
        }
    }
}
