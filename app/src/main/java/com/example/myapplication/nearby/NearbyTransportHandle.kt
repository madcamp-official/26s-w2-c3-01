package com.example.myapplication.nearby

import android.annotation.SuppressLint
import android.ranging.oob.TransportHandle
import androidx.annotation.RequiresApi
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Payload
import java.util.concurrent.Executor

@RequiresApi(36)
class NearbyTransportHandle(
    private val client: ConnectionsClient,
    private val endpointId: String,
) : TransportHandle {
    @Volatile private var executor: Executor? = null
    @Volatile private var callback: TransportHandle.ReceiveCallback? = null

    override fun registerReceiveCallback(
        executor: Executor,
        callback: TransportHandle.ReceiveCallback,
    ) {
        this.executor = executor
        this.callback = callback
    }

    @SuppressLint("MissingPermission")
    override fun sendData(data: ByteArray) {
        client.sendPayload(endpointId, Payload.fromBytes(data))
            .addOnFailureListener { dispatch(TransportHandle.ReceiveCallback::onSendFailed) }
    }

    fun receive(data: ByteArray) = dispatch { it.onReceiveData(data) }

    fun disconnected() = dispatch(TransportHandle.ReceiveCallback::onDisconnect)

    fun reconnected() = dispatch(TransportHandle.ReceiveCallback::onReconnect)

    override fun close() {
        dispatch(TransportHandle.ReceiveCallback::onClose)
        callback = null
        executor = null
    }

    private fun dispatch(block: (TransportHandle.ReceiveCallback) -> Unit) {
        val currentCallback = callback ?: return
        val currentExecutor = executor ?: return
        runCatching {
            currentExecutor.execute {
                // Android 16 ranging may reject a framework callback after the app-side
                // transport has already disconnected. That framework race must not crash app.
                runCatching { block(currentCallback) }
            }
        }
    }
}
