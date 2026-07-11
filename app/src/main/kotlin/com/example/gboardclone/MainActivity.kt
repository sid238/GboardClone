package com.example.gboardclone

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        ThemeHelper.apply(Prefs.themeMode)
        try {
            setContentView(R.layout.activity_main)

            findViewById<MaterialButton>(R.id.enableButton).setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }

            findViewById<MaterialButton>(R.id.switchButton).setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }

            findViewById<MaterialButton>(R.id.settingsButton).setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            setupThemeChooser()
        } catch (e: Exception) {
            showError(e)
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

    private fun showError(e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        AlertDialog.Builder(this)
            .setTitle("GboardClone error")
            .setMessage("${e.javaClass.simpleName}: ${e.message}\n\n${sw.toString().take(1500)}")
            .setPositiveButton("OK", null)
            .show()
    }
}
