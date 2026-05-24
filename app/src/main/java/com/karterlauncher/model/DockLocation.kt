package com.karterlauncher.model

enum class DockLocation {
    Left,
    Right,
    ;

    val storageKey: String get() = name

    companion object {
        fun fromStorage(value: String?): DockLocation = when (value) {
            Right.storageKey -> Right
            else -> Left
        }
    }
}
