package com.karterlauncher.model

enum class ThemeMode {
    Light,
    Dark,
    System,
    ;

    val storageKey: String get() = name

    companion object {
        fun fromStorage(value: String?): ThemeMode = when (value) {
            Light.storageKey -> Light
            Dark.storageKey -> Dark
            else -> System
        }
    }
}
