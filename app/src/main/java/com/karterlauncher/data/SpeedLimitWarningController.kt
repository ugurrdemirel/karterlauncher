package com.karterlauncher.data

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.karterlauncher.R
import com.karterlauncher.model.SpeedLimitState
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
 * Hız sınırı aşıldığında sesli uyarı veren TTS denetleyicisi.
 *
 * Akış: speed + speedLimit + enabled => [SpeedLimitState.Known] ile tutarlı bir limit biliniyorsa
 * ve [SpeedGaugeState.Speed] hızı limit + [OVERSPEED_HEADROOM_KMH] üzerinde tutarlı biçimde
 * [SUSTAINED_ABOVE_MS] boyunca kalırsa uyarı konuşulur.
 *
 * Limit bilinmiyorsa (Unknown / Error / Loading) hiçbir uyarı tetiklenmez.
 * Tekrar için [REARM_SLOW_PHASE_MS] boyunca yavaşlamak gerekir (sürücü bir kez uyarıldıktan
 * sonra uzun süre susturulur).
 */
class SpeedLimitWarningController(
    private val application: Application,
) : TextToSpeech.OnInitListener {
    private val job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(job + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private val audioManager: AudioManager =
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var tts: TextToSpeech? = null
    private var ttsInitializing = false
    private var ttsReady = false
    /** Aktif olarak hız sınırı uyarısı için audio focus tutuluyor mu? */
    @Volatile
    private var holdingAudioFocus = false

    private var armedForWarning = true
    private var sustainedSinceElapsedMs: Long? = null
    private var slowPhaseStartElapsedMs: Long? = null
    /**
     * En son uyarı verildiğindeki hız sınırı değeri. Yeni bir değer gelirse (farklı
     * yola girildi) re-arm edilir — aynı yolda tekrar tekrar uyarılmaz.
     */
    private var lastWarnedLimitKmh: Int? = null
    private var chimeEnabled: Boolean = true
    private val chime = ChimePlayer()

    /**
     * Başka uygulama focus aldığında (örn. telefon çağrısı) TTS'i durdurur; TTS
     * bittiğinde focus'u serbest bırakırız, böylece müzik uygulamaları
     * otomatik olarak devam eder.
     */
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                synchronized(this) { tts?.stop() }
                releaseAudioFocus()
            }
        }
    }

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
                engine.setOnUtteranceProgressListener(progressListener)
            }
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) {
            if (utteranceId?.startsWith(UTTERANCE_PREFIX) == true) {
                releaseAudioFocus()
            }
        }
        override fun onError(utteranceId: String?) {
            if (utteranceId?.startsWith(UTTERANCE_PREFIX) == true) {
                releaseAudioFocus()
            }
        }
        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId?.startsWith(UTTERANCE_PREFIX) == true) {
                releaseAudioFocus()
            }
        }
    }

    private fun requestAudioFocusOrSkip(): Boolean {
        if (holdingAudioFocus) return true
        val result = audioManager.requestAudioFocus(
            audioFocusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        )
        holdingAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holdingAudioFocus
    }

    private fun releaseAudioFocus() {
        if (!holdingAudioFocus) return
        audioManager.abandonAudioFocus(audioFocusListener)
        holdingAudioFocus = false
    }

    fun startObserving(
        speedStates: StateFlow<SpeedGaugeState>,
        speedLimitStates: StateFlow<SpeedLimitState>,
        enabledFlow: Flow<Boolean>,
        chimeEnabledFlow: Flow<Boolean>,
    ) {
        collectJob?.cancel()
        collectJob = scope.launch {
            combine(
                speedStates,
                speedLimitStates,
                enabledFlow,
                chimeEnabledFlow,
            ) { speed, limit, enabled, chime ->
                Inputs(speed, limit, enabled, chime)
            }
                .collect { (speed, limit, enabled, chimeOn) ->
                    chimeEnabled = chimeOn
                    if (!enabled) {
                        resetWhenDisabled()
                        return@collect
                    }
                    ensureTtsInitializing()
                    val kmh = (speed as? SpeedGaugeState.Speed)?.kmh
                    if (kmh == null) {
                        sustainedSinceElapsedMs = null
                        return@collect
                    }
                    val limitKmh = (limit as? SpeedLimitState.Known)?.kmh
                    if (limitKmh == null) {
                        // Limit bilinmiyor: uyarı tetikleme, sustained sayaç reset.
                        sustainedSinceElapsedMs = null
                        return@collect
                    }
                    onSmoothedKmh(kmh = kmh, limitKmh = limitKmh)
                }
        }
    }

    private data class Inputs(
        val speed: SpeedGaugeState,
        val limit: SpeedLimitState,
        val enabled: Boolean,
        val chime: Boolean,
    )

    private fun resetWhenDisabled() {
        armedForWarning = true
        sustainedSinceElapsedMs = null
        slowPhaseStartElapsedMs = null
        lastWarnedLimitKmh = null
        releaseAudioFocus()
        synchronized(this) { tts?.stop() }
    }

    fun stopObserving() {
        collectJob?.cancel()
        collectJob = null
        synchronized(this) { tts }?.stop()
        sustainedSinceElapsedMs = null
    }

    fun destroy() {
        stopObserving()
        job.cancelChildren()
        releaseAudioFocus()
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

    private fun onSmoothedKmh(kmh: Float, limitKmh: Int) {
        val now = SystemClock.elapsedRealtime()
        val overThreshold = limitKmh + OVERSPEED_HEADROOM_KMH

        if (!armedForWarning) {
            // Daha önce uyardık. İki koşulla re-arm edilebilir:
            //  1) Farklı bir hız sınırına girdik (yeni yol) — hemen re-arm
            //  2) Limit altında uzun süre kalındı — uzun bir slow phase sonrası re-arm
            val previousLimit = lastWarnedLimitKmh
            if (previousLimit != null && limitKmh != previousLimit) {
                armedForWarning = true
                lastWarnedLimitKmh = null
                slowPhaseStartElapsedMs = null
                sustainedSinceElapsedMs = null
                return
            }
            if (kmh > overThreshold) {
                slowPhaseStartElapsedMs = null
                return
            }
            if (kmh <= limitKmh - REARM_HEADROOM_KMH) {
                if (slowPhaseStartElapsedMs == null) {
                    slowPhaseStartElapsedMs = now
                }
                val belowFor = now - slowPhaseStartElapsedMs!!
                if (belowFor >= REARM_SLOW_PHASE_MS) {
                    armedForWarning = true
                    slowPhaseStartElapsedMs = null
                    sustainedSinceElapsedMs = null
                    lastWarnedLimitKmh = null
                }
            } else {
                slowPhaseStartElapsedMs = null
            }
            return
        }

        slowPhaseStartElapsedMs = null

        if (kmh < overThreshold - OVERSPEED_RESET_BAND_KMH) {
            // Limit + marjın epey altına düştük — sustained sayacı sıfırla.
            sustainedSinceElapsedMs = null
            return
        }

        if (kmh > overThreshold) {
            if (sustainedSinceElapsedMs == null) {
                sustainedSinceElapsedMs = now
            }
            val aboveFor = now - sustainedSinceElapsedMs!!
            if (aboveFor >= SUSTAINED_ABOVE_MS) {
                scope.launch { trySpeakWarning(limitKmh) }
                armedForWarning = false
                lastWarnedLimitKmh = limitKmh
                sustainedSinceElapsedMs = null
            }
        } else {
            sustainedSinceElapsedMs = null
        }
    }

    private suspend fun trySpeakWarning(limitKmh: Int) {
        // Audio focus isteği: başarılıysa müzik uygulamaları (Spotify, YouTube Music
        // vb.) kendileri durur; TTS bitince releaseAudioFocus ile devam ettirilir.
        if (!requestAudioFocusOrSkip()) return

        // Önce dikkat çekme tonu (ayarlanabilir), sonra TTS konuşması.
        if (chimeEnabled) {
            runCatching { chime.play() }
        }
        val engine: TextToSpeech?
        val ready: Boolean
        synchronized(this) {
            engine = tts
            ready = ttsReady
        }
        if (!ready || engine == null) {
            releaseAudioFocus()
            return
        }
        val text = application.getString(R.string.speed_limit_tts_warning, limitKmh)
        val utteranceId = "$UTTERANCE_PREFIX${SystemClock.elapsedRealtime()}"
        val code = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
        if (code == TextToSpeech.ERROR) {
            releaseAudioFocus()
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        private const val UTTERANCE_PREFIX = "speedlimit_"

        /** Limitin kaç km/h üzeri "aşırı hız" sayılır. */
        private const val OVERSPEED_HEADROOM_KMH = 10f

        /** Sustained sayacı sıfırlansın diye limit+headroom'un ne kadar altına düşmeli. */
        private const val OVERSPEED_RESET_BAND_KMH = 4f

        /** Limitten ne kadar aşağı inince "yavaşladı" sayılır (re‑arm öncesi). */
        private const val REARM_HEADROOM_KMH = 5f

        /** Sustained eşik: limitin +10 km/h üzerinde bu kadar süre kalınca uyarı verilir. */
        private const val SUSTAINED_ABOVE_MS = 6_000L

        /**
         * Re‑arm slow phase: aynı yolda uyarı verdikten sonra bu kadar süre yavaş
         * kalınmadan yeniden uyarı verilmez. 5 dk, şehir içi salınımlı hız
         * senaryolarında can sıkımayacak kadar uzun; uzun yolda da makul.
         */
        private const val REARM_SLOW_PHASE_MS = 5L * 60L * 1000L

        private const val REMINDER_SPEECH_RATE = 1.18f
    }
}
