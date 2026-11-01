package io.github.soanssandha.sidearm.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log
import io.github.libxposed.service.XposedProvider

/**
 * Where Vector hands the app its service binder. It replaces libxposed's own exported provider (removed in the
 * manifest), which takes a binder from any caller: any app on the phone could hand Sidearm a fake service, read what
 * the app writes, and feed it a fake status. The hook in system_server never reads from here, so it was never at risk.
 *
 * libxposed's provider is final, so this one checks the caller and then hands the call to an instance of it. That
 * instance is attached alongside this one, because its `onCreate` sets up libxposed's remote preferences for the
 * app's target SDK.
 */
class VectorBinderProvider : ContentProvider() {
    private val delegate = XposedProvider()

    override fun attachInfo(context: Context, info: ProviderInfo?) {
        super.attachInfo(context, info)
        delegate.attachInfo(context, info)
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val uid = Binder.getCallingUid()
        if (!isTrustedCaller(uid)) {
            Log.w(TAG, "Ignored a call from uid $uid")
            return null
        }
        return delegate.call(method, arg, extras)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0

    companion object {
        private const val TAG = "Sidearm"

        /**
         * Vector's daemon, `vectord`, is started as root, runs as the system uid, and calls the provider itself (seen
         * on Vector 2.2). Root is accepted too, since root can do anything anyway. No third-party app can hold either
         * uid; if a later Vector calls from another, the app shows "Not connected".
         */
        internal fun isTrustedCaller(uid: Int): Boolean = uid == Process.SYSTEM_UID || uid == Process.ROOT_UID
    }
}
