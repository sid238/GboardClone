package com.example.gboardclone

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        ThemeHelper.apply(Prefs.themeMode)
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val switchNumberRow = findViewById<SwitchMaterial>(R.id.switchNumberRow)
        val switchVibration = findViewById<SwitchMaterial>(R.id.switchVibration)
        val switchSound = findViewById<SwitchMaterial>(R.id.switchSound)

        switchNumberRow.isChecked = Prefs.numberRow
        switchVibration.isChecked = Prefs.vibration
        switchSound.isChecked = Prefs.sound

        switchNumberRow.setOnCheckedChangeListener { _, isChecked -> Prefs.numberRow = isChecked }
        switchVibration.setOnCheckedChangeListener { _, isChecked -> Prefs.vibration = isChecked }
        switchSound.setOnCheckedChangeListener { _, isChecked -> Prefs.sound = isChecked }

        setupThemeChooser()

        findViewById<MaterialButton>(R.id.clearClipboard).setOnClickListener {
            Prefs.clipboard = emptyList()
        }
    }

    private fun setupThemeChooser() {
        val buttons = listOf(
            R.id.themeLight to 0,
            R.id.themeDark to 1,
            R.id.themeSystem to 2
        )
        fun refresh() {
            for ((id, mode) in buttons) {
                val b = findViewById<MaterialButton>(id)
                val selected = Prefs.themeMode == mode
                val color = if (selected) R.color.purple_500 else R.color.key_text_light
                b.strokeColor = ColorStateList.valueOf(resources.getColor(color, theme))
                b.setTextColor(resources.getColor(color, theme))
            }
        }
        for ((id, mode) in buttons) {
            findViewById<MaterialButton>(id).setOnClickListener {
                Prefs.themeMode = mode
                ThemeHelper.apply(mode)
                refresh()
            }
        }
        refresh()
    }
}
