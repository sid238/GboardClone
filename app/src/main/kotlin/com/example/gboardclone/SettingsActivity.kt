package com.example.gboardclone

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        Prefs.init(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val switchTheme = findViewById<SwitchMaterial>(R.id.switchTheme)
        val switchNumberRow = findViewById<SwitchMaterial>(R.id.switchNumberRow)
        val switchVibration = findViewById<SwitchMaterial>(R.id.switchVibration)
        val switchSound = findViewById<SwitchMaterial>(R.id.switchSound)

        switchTheme.isChecked = Prefs.isDark
        switchNumberRow.isChecked = Prefs.numberRow
        switchVibration.isChecked = Prefs.vibration
        switchSound.isChecked = Prefs.sound

        switchTheme.setOnCheckedChangeListener { _, isChecked -> Prefs.isDark = isChecked }
        switchNumberRow.setOnCheckedChangeListener { _, isChecked -> Prefs.numberRow = isChecked }
        switchVibration.setOnCheckedChangeListener { _, isChecked -> Prefs.vibration = isChecked }
        switchSound.setOnCheckedChangeListener { _, isChecked ->
            Prefs.sound = isChecked
        }

        findViewById<MaterialButton>(R.id.clearClipboard).setOnClickListener {
            Prefs.clipboard = emptyList()
        }
    }
}
