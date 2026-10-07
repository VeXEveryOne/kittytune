package com.alananasss.kittytune.data.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/** Read-only, DUMP-protected development diagnostics; never expose pairing secrets or track URLs. */
class ConnectDiagnosticsProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/connect-state"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(arrayOf("device", "connected", "transport", "host", "queue", "index", "track",
            "playing", "position", "selected", "lanAvailable"))
        val local = ConnectManager.localState()
        cursor.addRow(arrayOf<Any?>("local", true, "local", "", local?.queue?.size ?: 0, local?.currentIndex ?: -1,
            local?.queue?.getOrNull(local.currentIndex)?.id ?: -1, local?.isPlaying == true,
            local?.positionMs ?: 0, ConnectManager.selectedDevice.value == null, ConnectPlatform.canUseLan()))
        SyncPeers.all().forEach { peer ->
            val live = ConnectManager.peers.value[peer.deviceId]
            val state = live?.snapshot
            cursor.addRow(arrayOf<Any?>(peer.deviceId, live?.connected == true, live?.transport.orEmpty(), peer.host,
                state?.queue?.size ?: 0, state?.currentIndex ?: -1, state?.queue?.getOrNull(state.currentIndex)?.id ?: -1,
                state?.isPlaying == true, live?.position() ?: 0, ConnectManager.selectedDevice.value == peer.deviceId,
                ConnectPlatform.canUseLan()))
        }
        return cursor
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
