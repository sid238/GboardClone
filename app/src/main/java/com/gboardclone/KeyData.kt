package com.gboardclone

data class KeyData(
    val label: String,
    val primary: String,
    val secondary: String? = null,
    val type: KeyType = KeyType.TEXT,
    val widthRatio: Float = 1f
)

enum class KeyType {
    TEXT,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    NUMBERS,
    SYMBOLS,
    ABC,
    DONE,
    COMMA,
    PERIOD,
    GLIDE_DOT
}
