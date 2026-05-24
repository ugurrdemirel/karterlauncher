package com.karterlauncher.data

import android.app.Application
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import com.karterlauncher.R
import com.karterlauncher.model.SpeedGaugeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Eğlence amaçlı TTS: hız eşiğinin üzerinde bir süre kalınca emniyet kemeri hatırlatması.
 *
 * GPS için: eşik üstünde süreklilik kontrolü ve alt bantta sıfırlama; tekrar için uzun yavaş süre.
 */
class SeatbeltReminderController(
    private val application: Application,
) : TextToSpeech.OnInitListener {
    private val job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(job + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsInitializing = false
    private var ttsReady = false

    private var armedForReminder = true
    private var sustainedAboveSinceElapsedMs: Long? = null
    private var slowPhaseStartElapsedMs: Long? = null

    override fun onInit(status: Int) {
        synchronized(this) {
            ttsInitializing = false
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                ttsReady = false
                return
            }
            var langOk = engine.setLanguage(Locale.forLanguageTag("tr-TR"))
            if (langOk == TextToSpeech.LANG_MISSING_DATA || langOk == TextToSpeech.LANG_NOT_SUPPORTED) {
                langOk = engine.setLanguage(Locale.getDefault())
            }
            ttsReady =
                langOk != TextToSpeech.LANG_MISSING_DATA && langOk != TextToSpeech.LANG_NOT_SUPPORTED
            if (ttsReady) {
                engine.setSpeechRate(REMINDER_SPEECH_RATE)
            }
        }
    }

    fun startObserving(
        speedStates: StateFlow<SpeedGaugeState>,
        seatbeltReminderEnabled: Flow<Boolean>,
    ) {
        collectJob?.cancel()
        collectJob = scope.launch {
            combine(speedStates, seatbeltReminderEnabled) { state, enabled -> state to enabled }
                .collect { (state, enabled) ->
                    if (!enabled) {
                        resetWhenSeatbeltDisabled()
                        return@collect
                    }
                    ensureTtsInitializing()
                    when (state) {
                        is SpeedGaugeState.Speed -> onSmoothedKmh(state.kmh)
                        else -> sustainedAboveSinceElapsedMs = null
                    }
                }
        }
    }

    private fun resetWhenSeatbeltDisabled() {
        armedForReminder = true
        sustainedAboveSinceElapsedMs = null
        slowPhaseStartElapsedMs = null
        synchronized(this) { tts?.stop() }
    }

    fun stopObserving() {
        collectJob?.cancel()
        collectJob = null
        synchronized(this) { tts }?.stop()
        sustainedAboveSinceElapsedMs = null
    }

    fun destroy() {
        stopObserving()
        job.cancelChildren()
        synchronized(this) {
            try {
                tts?.stop()
                tts?.shutdown()
            } finally {
                tts = null
                ttsReady = false
            }
        }
    }

    private fun ensureTtsInitializing() {
        synchronized(this) {
            if (tts != null || ttsInitializing) return
            ttsInitializing = true
            tts = TextToSpeech(application.applicationContext, this)
        }
    }

    private fun onSmoothedKmh(kmh: Float) {
        val now = SystemClock.elapsedRealtime()

        if (!armedForReminder) {
            if (kmh > BREAK_ABOVE_KMH) {
                slowPhaseStartElapsedMs = null
                return
            }
            if (kmh <= SLOW_BAND_MAX_KMH) {
                if (slowPhaseStartElapsedMs == null) {
                    slowPhaseStartElapsedMs = now
                }
                val belowFor = now - slowPhaseStartElapsedMs!!
                if (belowFor >= SLOW_PHASE_NEEDS_MS) {
                    armedForReminder = true
                    slowPhaseStartElapsedMs = null
                    sustainedAboveSinceElapsedMs = null
                }
            } else {
                slowPhaseStartElapsedMs = null
            }
            return
        }

        slowPhaseStartElapsedMs = null

        if (kmh < STREAK_RESET_BELOW_KMH) {
            sustainedAboveSinceElapsedMs = null
            return
        }

        if (kmh > TRIGGER_KMH) {
            if (sustainedAboveSinceElapsedMs == null) {
                sustainedAboveSinceElapsedMs = now
            }
            val aboveFor = now - sustainedAboveSinceElapsedMs!!
            if (aboveFor >= SUSTAINED_ABOVE_MS && trySpeakReminder()) {
                armedForReminder = false
                sustainedAboveSinceElapsedMs = null
                slowPhaseStartElapsedMs = null
            }
        } else {
            sustainedAboveSinceElapsedMs = null
        }
    }

    private fun trySpeakReminder(): Boolean {
        ensureTtsInitializing()
        val engine: TextToSpeech?
        val ready: Boolean
        synchronized(this) {
            engine = tts
            ready = ttsReady
        }
        if (!ready || engine == null) {
            return false
        }
        val text = application.getString(R.string.seatbelt_tts_reminder)
        val code = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "seatbelt_${SystemClock.elapsedRealtime()}",
        )
        return code != TextToSpeech.ERROR
    }

    companion object {
        private const val TRIGGER_KMH = 5f
        private const val STREAK_RESET_BELOW_KMH = 3f
        private const val SLOW_BAND_MAX_KMH = 4f
        private const val BREAK_ABOVE_KMH = 6f
        private const val SUSTAINED_ABOVE_MS = 4_800L
        private const val SLOW_PHASE_NEEDS_MS = 30 * 60 * 1000L

        /** Varsayılan TTS 1.0; yükseltmek konuşmayı hızlandırır (engine limiti değişebilir). */
        private const val REMINDER_SPEECH_RATE = 1.28f
    }
}
