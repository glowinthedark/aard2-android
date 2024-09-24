package itkach.aard2

class BookmarksFragment : BlobDescriptorListFragment() {
    override val descriptorList: BlobDescriptorList
        get() {
            val app = requireActivity().application as Application
            return app.bookmarks!!
        }
    override val itemClickAction: String?
        get() = "showBookmarks"

    override val emptyIcon: Int
        get() = R.drawable.bookmarks_24px

    override val emptyText: CharSequence?
        get() = getString(R.string.main_empty_bookmarks)

    override val deleteConfirmationItemCountResId: Int
        get() = R.plurals.confirm_delete_bookmark_count

    override val preferencesNS: String?
        get() = "bookmarks"

}