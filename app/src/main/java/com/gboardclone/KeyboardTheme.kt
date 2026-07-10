package com.gboardclone

import android.graphics.Typeface

data class KeyboardTheme(
    val backgroundColor: Int = 0xFF1E1E1E.toInt(),
    val keyBackgroundColor: Int = 0xFF333333.toInt(),
    val keyTextColor: Int = 0xFFFFFFFF.toInt(),
    val keyPressedColor: Int = 0xFF555555.toInt(),
    val specialKeyColor: Int = 0xFF2A2A2A.toInt(),
    val specialKeyTextColor: Int = 0xFFAAAAAA.toInt(),
    val spaceBarColor: Int = 0xFF333333.toInt(),
    val previewBackgroundColor: Int = 0xFF666666.toInt(),
    val previewTextColor: Int = 0xFFFFFFFF.toInt(),
    val borderColor: Int = 0xFF1E1E1E.toInt(),
    val suggestionColor: Int = 0xFFFFFFFF.toInt(),
    val suggestionBackgroundColor: Int = 0xFF252525.toInt(),
    val typeface: android.graphics.Typeface = android.graphics.Typeface.DEFAULT
) {
    companion object {
        val LIGHT = KeyboardTheme(
            backgroundColor = 0xFFE0E0E0.toInt(),
            keyBackgroundColor = 0xFFFFFFFF.toInt(),
            keyTextColor = 0xFF000000.toInt(),
            keyPressedColor = 0xFFC8C8C8.toInt(),
            specialKeyColor = 0xFFD5D5D5.toInt(),
            specialKeyTextColor = 0xFF666666.toInt(),
            spaceBarColor = 0xFFFFFFFF.toInt(),
            previewBackgroundColor = 0xFF444444.toInt(),
            previewTextColor = 0xFFFFFFFF.toInt(),
            borderColor = 0xFFE0E0E0.toInt(),
            suggestionColor = 0xFF000000.toInt(),
            suggestionBackgroundColor = 0xFFF5F5F5.toInt()
        )

        val DARK = KeyboardTheme()
    }
}