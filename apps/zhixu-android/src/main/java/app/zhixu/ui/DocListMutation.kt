package app.zhixu.ui

import android.net.Uri
import app.zhixu.data.UiDoc

sealed interface DocListMutation {
    data class Created(val doc: UiDoc) : DocListMutation

    data class Deleted(val docUri: Uri) : DocListMutation

    data class Renamed(val oldUri: Uri, val newUri: Uri) : DocListMutation
}

