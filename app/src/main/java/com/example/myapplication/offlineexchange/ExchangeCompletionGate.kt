package com.example.myapplication.offlineexchange

internal class ExchangeCompletionGate {
    private var connected = true
    private var localAck = false
    private var remoteAck = false

    val canCommit: Boolean get() = connected && localAck && remoteAck

    fun markLocalAck() { localAck = true }
    fun markRemoteAck() { remoteAck = true }
    fun disconnect() { connected = false }
    fun reset() {
        connected = true
        localAck = false
        remoteAck = false
    }
}
