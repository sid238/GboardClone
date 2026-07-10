package com.gboardclone

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardService : InputMethodService() {

    private lateinit var keyboardView: KeyboardView
    private var isShifted = false
    private var capsLock = false
    private var currentMode = KeyboardMode.LETTERS

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this, this)
        keyboardView.setTheme(KeyboardTheme.DARK)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isShifted = true
        keyboardView.setShifted(true)
        keyboardView.setSuggestions(listOf("the", "and", "for"))
    }

    fun onKeyPressed(key: KeyData) {
        val ic = currentInputConnection ?: return

        when (key.type) {
            KeyType.TEXT -> {
                val text = if (isShifted && !capsLock) {
                    isShifted = false
                    keyboardView.setShifted(false)
                    key.primary
                } else {
                    key.primary
                }
                ic.commitText(text, 1)
            }
            KeyType.SHIFT -> {
                when {
                    capsLock -> {
                        capsLock = false
                        isShifted = false
                    }
                    isShifted -> capsLock = true
                    else -> isShifted = true
                }
                keyboardView.setShifted(isShifted || capsLock)
            }
            KeyType.BACKSPACE -> {
                ic.deleteSurroundingText(1, 0)
            }
            KeyType.ENTER -> {
                ic.commitText("\n", 1)
            }
            KeyType.SPACE -> {
                ic.commitText(" ", 1)
            }
            KeyType.NUMBERS -> {
                keyboardView.setMode(KeyboardMode.NUMBERS)
            }
            KeyType.SYMBOLS -> {
                keyboardView.setMode(KeyboardMode.SYMBOLS)
            }
            KeyType.ABC -> {
                isShifted = false
                keyboardView.setMode(KeyboardMode.LETTERS)
            }
            KeyType.COMMA -> {
                ic.commitText(",", 1)
            }
            KeyType.PERIOD -> {
                ic.commitText(".", 1)
            }
            KeyType.DONE -> {
                currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            else -> {
                ic.commitText(key.primary, 1)
            }
        }
    }
}