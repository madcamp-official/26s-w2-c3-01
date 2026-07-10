package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.Track
import com.example.myapplication.data.DemoMelodyRepository
import com.example.myapplication.data.MelodyRepository

class MelodyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MelodyRepository = DemoMelodyRepository(application)
    val uiState = repository.state

    fun completeOnboarding() = repository.completeOnboarding()
    fun selectTab(tab: MainTab) = repository.selectTab(tab)
    fun selectNearby(handle: String?) = repository.selectNearby(handle)
    fun selectLounge(roomId: String?) = repository.selectLounge(roomId)
    fun startSharing() = repository.startSharing()
    fun stopSharing() = repository.stopSharing()
    fun sharingPermissionRequired() = repository.setSharingPermissionRequired()
    fun follow(handle: String) = repository.follow(handle)
    fun react(handle: String, reactionLabel: String) = repository.react(handle, reactionLabel)
    fun block(handle: String) = repository.block(handle)
    fun report(handle: String) = repository.report(handle)
    fun joinLounge(roomId: String) = repository.joinLounge(roomId)
    fun vote(roomId: String, optionId: String) = repository.vote(roomId, optionId)
    fun sendMusicCard(roomId: String) = repository.sendMusicCard(roomId)
    fun reactToMusicCard(roomId: String, cardId: String) =
        repository.reactToMusicCard(roomId, cardId)
    fun sendChat(roomId: String, content: String) = repository.sendChat(roomId, content)
    fun selectTrack(track: Track) = repository.selectTrack(track)
    fun markInboxRead() = repository.markInboxRead()
    fun setDiscoverable(enabled: Boolean) = repository.setDiscoverable(enabled)
    fun setAllowReactions(enabled: Boolean) = repository.setAllowReactions(enabled)
    fun setOfflineExchangeEnabled(enabled: Boolean) = repository.setOfflineExchangeEnabled(enabled)
    fun createDemoExchange(peerAlias: String) = repository.createDemoExchange(peerAlias)
    fun syncExchange(exchangeId: String) = repository.syncExchange(exchangeId)
    fun clearFeedback() = repository.clearFeedback()

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
