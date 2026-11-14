package com.hellovoid.liquiddock.os4

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Allows the hooked Launcher process to wake LiquidDock and synchronously
 * re-sync OS4 native/widget configs after a Launcher version switch, even
 * when HyperOS freezes background apps or suppresses package broadcasts.
 */
class Os4SyncProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "sync") {
            val app = context?.applicationContext ?: return null
            Os4ConfigSync.syncIfLauncherChangedBlocking(app)
            return Bundle().apply { putBoolean("synced", true) }
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
