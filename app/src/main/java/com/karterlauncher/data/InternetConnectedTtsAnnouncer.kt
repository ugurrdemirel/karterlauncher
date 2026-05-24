package com.karterlauncher.data

import android.app.Application
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import com.karterlauncher.R
import java.util.Locale

/**
 * Ağ doğrulanıp bağlantı yenilendiğinde kısa bir TTS bildirimi.
 * Tünellerde sık çık‑giris için tekrarı sınırlar.
 */
class InternetConnectedTtsAnnouncer(
    private val application: Application,
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsInitializing = false
    private var ttsReady = false
    private var speakAfterReady = false
    private var lastSpeakAtElapsedMs = 0L

    override fun onInit(status: Int) {
        synchronized(this) {
            ttsInitializing = false
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                ttsReady = false
                speakAfterReady = false
                return
            }
            var langOk = engine.setLanguage(Locale.forLanguageTag("tr-TR"))
            if (langOk == TextToSpeech.LANG_MISSING_DATA || langOk == TextToSpeech.LANG_NOT_SUPPORTED) {
                langOk = engine.setLanguage(Locale.getDefault())
            }
            ttsReady =
                langOk != TextToSpeech.LANG_MISSING_DATA && langOk != TextToSpeech.LANG_NOT_SUPPORTED
            if (ttsReady) {
                engine.setSpeechRate(ANNOUNCEMENT_SPEECH_RATE)
            }
            if (ttsReady && speakAfterReady) {
                announceLocked(engine)
                speakAfterReady = false
            } else if (!ttsReady) {
                speakAfterReady = false
            }
        }
    }

    /** Bağlantı önce kopuk doğrulanmışken sonra tekrar [true] olduğunda çağırın (örn. kopuktan çevrimiçi geçiş). */
    fun speakConnectivityRestored() {
        synchronized(this) {
            ensureInitializingLocked()
            val engine = tts
            if (ttsReady && engine != null) {
                announceLocked(engine)
                return
            }
            speakAfterReady = true
        }
    }

    fun shutdown() {
        synchronized(this) {
            speakAfterReady = false
            try {
                tts?.stop()
                tts?.shutdown()
            } finally {
                tts = null
                ttsReady = false
                ttsInitializing = false
            }
        }
    }

    private fun ensureInitializingLocked() {
        if (tts != null || ttsInitializing) return
        ttsInitializing = true
        tts = TextToSpeech(application.applicationContext, this)
    }

    private fun announceLocked(engine: TextToSpeech): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSpeakAtElapsedMs < THROTTLE_BETWEEN_ANNOUNCEMENTS_MS) return false
        if (!queueSpeak(engine)) return false
        lastSpeakAtElapsedMs = SystemClock.elapsedRealtime()
        return true
    }

    private fun queueSpeak(engine: TextToSpeech): Boolean {
        val text = application.getString(R.string.internet_connected_tts)
        val code = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "net_ok_${SystemClock.elapsedRealtime()}",
        )
        return code != TextToSpeech.ERROR
    }

    private companion object {
        private const val THROTTLE_BETWEEN_ANNOUNCEMENTS_MS = 15_000L
        private const val ANNOUNCEMENT_SPEECH_RATE = 1.12f
    }
}
