package com.gboardclone

import android.graphics.Color
import android.graphics.Typeface

data class KeyboardTheme(
    val backgroundColor: Int = 0xFF1E1E1E.toInt(),
    val keyBackgroundColor: Int = 0xFF333333.toInt(),
    val keyTextColor: Int = 0xFFFFFFFF.toInt(),
    val keyPressedColor: Int = 0xFF555555.toInt(),
    val specialKeyColor: Int = 0xFF2A2A2A.toInt(),
    val specialKeyTextColor: Int = 0xFFFFFFFF.toInt(),
    val spaceBarColor: Int = 0xFF333333.toInt(),
    val previewBackgroundColor: Int = 0xFF555555.toInt(),
    val previewTextColor: Int = 0xFFFFFFFF.toInt(),
    val borderColor: Int = 0xFF444444.toInt(),
    val suggestionColor: Int = 0xFFFFFFFF.toInt(),
    val suggestionBackgroundColor: Int = 0xFF2A2A2A.toInt(),
    val typeface: Typeface = Typeface.DEFAULT
) {
    companion object {
        val LIGHT = KeyboardTheme(
            backgroundColor = 0xFFD1D5DB.toInt(),
            keyBackgroundColor = 0xFFFFFFFF.toInt(),
            keyTextColor = 0xFF000000.toInt(),
            keyPressedColor = 0xFFB0B0B0.toInt(),
            specialKeyColor = 0xFFC4C7CC.toInt(),
            specialKeyTextColor = 0xFF000000.toInt(),
            spaceBarColor = 0xFFFFFFFF.toInt(),
            previewBackgroundColor = 0xFF333333.toInt(),
            previewTextColor = 0xFFFFFFFF.toInt(),
            borderColor = 0xFFB0B0B0.toInt(),
            suggestionColor = 0xFF000000.toInt(),
            suggestionBackgroundColor = 0xFFE0E0E0.toInt()
        )

    val DARK = KeyboardTheme()
    }
}
