package com.zhixu.android.ui

import android.net.Uri
import com.zhixu.android.data.UiDoc

sealed interface DocListMutation {
    data class Created(val doc: UiDoc) : DocListMutation

    data class Deleted(val docUri: Uri) : DocListMutation

    data class Renamed(val oldUri: Uri, val newUri: Uri) : DocListMutation
}

