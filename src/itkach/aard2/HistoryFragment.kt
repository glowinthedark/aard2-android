package itkach.aard2

class HistoryFragment : BlobDescriptorListFragment() {
    override val itemClickAction: String?
        get() = "showHistory"

    override val descriptorList: BlobDescriptorList
        get() {
            val app = requireActivity().application as Application
            return app.history!!
        }

    override val emptyIcon: Int
        get() = R.drawable.history_24px

    override val emptyText: CharSequence?
        get() = getString(R.string.main_empty_history)


    override val deleteConfirmationItemCountResId: Int
        get() = R.plurals.confirm_delete_history_count

    override val preferencesNS: String?
        get() = "history"
}
