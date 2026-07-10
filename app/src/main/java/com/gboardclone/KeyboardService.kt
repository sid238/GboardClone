package com.gboardclone

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardService : InputMethodService() {

    private lateinit var keyboardView: KeyboardView
    private var isShifted = false
    private var isCapsLock = false

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this, this)
        keyboardView.setTheme(KeyboardTheme.DARK)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.setLayout(KeyboardLayouts.QWERTY_LOWER)
        isShifted = true
        keyboardView.setShifted(true)
    }

    fun onKeyPressed(key: KeyData) {
        val ic = currentInputConnection ?: return

        when (key.type) {
            KeyType.TEXT -> {
                val text = if (isShifted && !isCapsLock) {
                    isShifted = false
                    keyboardView.setShifted(false)
                    key.primary
                } else {
                    key.primary
                }
                ic.commitText(text, 1)
            }
            KeyType.SHIFT -> {
                if (isCapsLock) {
                    isCapsLock = false
                    isShifted = false
                } else if (isShifted) {
                    isCapsLock = true
                } else {
                    isShifted = true
                }
                keyboardView.setShifted(isShifted || isCapsLock)
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
