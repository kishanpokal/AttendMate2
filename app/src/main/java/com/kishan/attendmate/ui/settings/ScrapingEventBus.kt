package com.kishan.attendmate.ui.settings

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScrapingEventBus {

    // ADDED REPLAY: This "remembers" the history so when you open Fullscreen,
    // the new 3D engine can fast-forward and instantly restore existing planets!
    private val _events = MutableSharedFlow<ScrapingEvent>(
        replay = 200,
        extraBufferCapacity = 100
    )
    val events: SharedFlow<ScrapingEvent> = _events.asSharedFlow()

    suspend fun emit(event: ScrapingEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: ScrapingEvent) {
        _events.tryEmit(event)
    }

    // NEW: Call this when starting/stopping a sync to clear the 3D memory
    fun clearHistory() {
        _events.resetReplayCache()
    }
}

sealed class ScrapingEvent {
    data class SpawnSubject(val name: String) : ScrapingEvent()
    data class StartExtraction(val name: String) : ScrapingEvent()
    data class UpdateProgress(val percent: Float, val text: String) : ScrapingEvent()
    data class FinishSubject(val name: String) : ScrapingEvent()
    data class RecordExtracted(val count: Int) : ScrapingEvent()
    data class SetPhase(val phase: ScrapePhase) : ScrapingEvent()
}