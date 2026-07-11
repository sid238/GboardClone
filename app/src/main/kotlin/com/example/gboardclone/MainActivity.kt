package com.example.gboardclone

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        } catch (e: Exception) {
            showError(e)
        }
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
