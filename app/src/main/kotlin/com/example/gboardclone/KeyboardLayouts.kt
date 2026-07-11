package com.example.gboardclone

/**
 * Special (non-character) key codes, following the classic negative-code
 * convention used by Android's old KeyboardView.
 */
object KeyCode {
    const val SHIFT = -1
    const val SYMBOLS = -2
    const val LETTERS = -3
    const val ENTER = -4
    const val DELETE = -5
    const val SPACE = -6
    const val SYMBOLS_PAGE_2 = -7
    const val SYMBOLS_PAGE_1 = -8
}

enum class KeyboardMode {
    LETTERS,
    SYMBOLS_1,
    SYMBOLS_2
}

enum class ShiftState {
    OFF,
    SHIFT,
    CAPS_LOCK
}

/** Static row definitions (lowercase letters; shift-casing handled at render time). */
object KeyboardLayouts {
    val lettersRow1 = "qwertyuiop".map { it.toString() }
    val lettersRow2 = "asdfghjkl".map { it.toString() }
    val lettersRow3 = "zxcvbnm".map { it.toString() }

    val symbols1Row1 = "1234567890".map { it.toString() }
    val symbols1Row2 = "@#$_&-+()".map { it.toString() }
    val symbols1Row3 = listOf("*", "\"", "'", ":", ";", "!", "?")

    val symbols2Row1 = "~`|•√π÷×".map { it.toString() }
    val symbols2Row2 = "£¢€¥^°={}".map { it.toString() }
    val symbols2Row3 = listOf("\\", "©", "®", "™", "✓", "[", "]")
}
