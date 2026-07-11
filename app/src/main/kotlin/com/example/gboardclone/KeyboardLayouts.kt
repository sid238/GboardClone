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
    const val EMOJI = -9
    const val CLIPBOARD = -10
    const val VOICE = -11
    const val SETTINGS = -12
    const val GLOBE = -13
}

enum class KeyboardMode {
    LETTERS,
    SYMBOLS_1,
    SYMBOLS_2,
    EMOJI,
    CLIPBOARD
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
    val symbols1Row2 = "@#\$&-+()".map { it.toString() }
    val symbols1Row3 = listOf("*", "\"", "'", ":", ";", "!", "?")

    val symbols2Row1 = "~`|•√π÷×".map { it.toString() }
    val symbols2Row2 = "£¢€¥^°={}".map { it.toString() }
    val symbols2Row3 = listOf("\\", "©", "®", "™", "✓", "[", "]")

    /** Long-press variants (Gboard-style popups). */
    val longPressVariants: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ã", "ä", "å", "æ", "@"),
        "e" to listOf("è", "é", "ê", "ë", "€"),
        "i" to listOf("ì", "í", "î", "ï"),
        "o" to listOf("ò", "ó", "ô", "õ", "ö", "ø", "œ"),
        "u" to listOf("ù", "ú", "û", "ü"),
        "c" to listOf("ç", "©"),
        "n" to listOf("ñ"),
        "s" to listOf("ß", "$"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź"),
        "g" to listOf("ğ"),
        "l" to listOf("ł"),
        "d" to listOf("ð"),
        "t" to listOf("þ"),
        "?" to listOf("¿"),
        "!" to listOf("¡"),
        "\$" to listOf("€", "£", "¥"),
        "." to listOf(",", "?", "!", ";", ":"),
        "," to listOf(".", ";", ":")
    )

    /** Small hint shown on top of each letter key (phone-style number/symbol map). */
    val hintMap: Map<String, String> = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "a" to "@", "s" to "#", "d" to "\$", "f" to "%", "g" to "^",
        "h" to "&", "j" to "*", "k" to "(", "l" to ")",
        "z" to "*", "x" to "(", "c" to ")", "v" to "?", "b" to "-",
        "n" to "_", "m" to "+"
    )
}
