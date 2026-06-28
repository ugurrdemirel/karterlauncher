package com.karterlauncher.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * TTS uyarısı öncesi kısa bir dikkat çekme tonu çalar. İki-tonlu alçalır ses —
 * araç kapı/uyarı zili formunda, yumuşak envelope ile tıklama/cırlama yok.
 *
 * Ses `STREAM_MUSIC` üzerinden gider; kullanıcının medya ses seviyesi ile
 * orantılıdır. Üretim tamamen programatik — harici ses dosyası gerekmez.
 */
class ChimePlayer {

    /** Ton çal, tamamlanana kadar bekle. TTS'i bundan sonra çağırın. */
    suspend fun play(volume: Float = DEFAULT_VOLUME) = withContext(Dispatchers.IO) {
        val sampleRate = SAMPLE_RATE
        val totalMs = TONES.sumOf { it.durationMs } + GAP_MS * (TONES.size - 1)
        val totalSamples = (sampleRate * totalMs) / 1000
        val samples = ShortArray(totalSamples)

        var offset = 0
        for ((idx, tone) in TONES.withIndex()) {
            val toneSamples = (sampleRate * tone.durationMs) / 1000
            generateTone(
                samples = samples,
                offset = offset,
                count = toneSamples,
                frequencyHz = tone.frequencyHz,
                sampleRate = sampleRate,
                volume = volume,
            )
            offset += toneSamples
            if (idx < TONES.size - 1) {
                // Zaten sıfır dolu (ShortArray default), ekstra gap olarak boş bırakıldı
                offset += (sampleRate * GAP_MS) / 1000
            }
        }

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrElse {
            // AudioTrack oluşturulamazsa sessizce atla (TTS yine çalışır).
            return@withContext
        }

        try {
            track.write(samples, 0, samples.size)
            track.play()
            delay(totalMs + 50L)
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun generateTone(
        samples: ShortArray,
        offset: Int,
        count: Int,
        frequencyHz: Int,
        sampleRate: Int,
        volume: Float,
    ) {
        val twoPiFOverSr = 2.0 * PI * frequencyHz / sampleRate
        for (i in 0 until count) {
            val angle = i * twoPiFOverSr
            val envelope = smoothEnvelope(i = i, total = count)
            val sample = sin(angle) * Short.MAX_VALUE * volume * envelope
            samples[offset + i] = sample.toInt().toShort()
        }
    }

    /**
     * Başta ve sonda yumuşak attack/release uygular — tıklama/cırlama olmaz.
     * İlk 8 ms attack, son 30 ms release; geri kalanı tam genlik.
     */
    private fun smoothEnvelope(i: Int, total: Int): Double {
        val attackSamples = (SAMPLE_RATE * 8) / 1000
        val releaseSamples = (SAMPLE_RATE * 30) / 1000
        return when {
            i < attackSamples -> i.toDouble() / attackSamples
            i > total - releaseSamples -> (total - i).toDouble() / releaseSamples
            else -> 1.0
        }
    }

    companion object {
        private const val SAMPLE_RATE = 22050
        private const val GAP_MS = 25
        private const val DEFAULT_VOLUME = 0.32f

        /**
         * İki-tonlu alçalır chime: yüksek frekans (kapı zili hissi) → alçak frekans.
         * Frekanslar araç bilgi-eğlence sistemlerindeki "uyarı" tonlarına yakın.
         */
        private val TONES = listOf(
            Tone(frequencyHz = 1180, durationMs = 110),  // yüksek "ding"
            Tone(frequencyHz = 820, durationMs = 170),   // alçak "dong"
        )
    }

    private data class Tone(val frequencyHz: Int, val durationMs: Int)
}
