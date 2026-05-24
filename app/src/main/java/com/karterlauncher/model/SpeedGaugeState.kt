package com.karterlauncher.model

/** GPS / fused location ile hız göstergesi durumu */
sealed interface SpeedGaugeState {
    data object NoPermission : SpeedGaugeState

    /** İlk düzeltme bekleniyor */
    data object WaitingForFix : SpeedGaugeState

    /** km/h — durduğunuzda 0 */
    data class Speed(val kmh: Float) : SpeedGaugeState
}
