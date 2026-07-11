package com.example.gboardclone

import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class GboardCloneService : InputMethodService() {

    private var mode: KeyboardMode = KeyboardMode.LETTERS
    private var shiftState: ShiftState = ShiftState.OFF
    private var lastShiftTapTime = 0L

    private lateinit var rootView: LinearLayout
    private lateinit var keyRowsContainer: LinearLayout
    private lateinit var suggestionStrip: LinearLayout

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    // ---- Placeholder "predictive" suggestions (cosmetic only, no real dictionary) ----
    private val placeholderSuggestions = listOf("I", "the", "you")

    override fun onCreateInputView(): View {
        rootView = buildRootView()
        renderKeyboard()
        return rootView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        mode = KeyboardMode.LETTERS
        shiftState = ShiftState.OFF
        renderKeyboard()
    }

    // -------------------------------------------------------------------------------
    // View construction
    // -------------------------------------------------------------------------------

    private fun buildRootView(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@GboardCloneService, R.color.keyboard_bg))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        suggestionStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ContextCompat.getColor(this@GboardCloneService, R.color.suggestion_bg))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            )
        }
        container.addView(suggestionStrip)

        keyRowsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(keyRowsContainer)

        return container
    }

    private fun renderKeyboard() {
        keyRowsContainer.removeAllViews()
        renderSuggestionStrip()

        when (mode) {
            KeyboardMode.LETTERS -> {
                keyRowsContainer.addView(buildLetterRow(KeyboardLayouts.lettersRow1, sideInset = 0f))
                keyRowsContainer.addView(buildLetterRow(KeyboardLayouts.lettersRow2, sideInset = 0.5f))
                keyRowsContainer.addView(buildThirdLetterRow())
                keyRowsContainer.addView(buildBottomRow())
            }
            KeyboardMode.SYMBOLS_1 -> {
                keyRowsContainer.addView(buildSymbolRow(KeyboardLayouts.symbols1Row1))
                keyRowsContainer.addView(buildSymbolRow(KeyboardLayouts.symbols1Row2))
                keyRowsContainer.addView(buildSymbolThirdRow(KeyboardLayouts.symbols1Row3, nextLabel = "2/2", nextMode = KeyboardMode.SYMBOLS_2))
                keyRowsContainer.addView(buildBottomRow())
            }
            KeyboardMode.SYMBOLS_2 -> {
                keyRowsContainer.addView(buildSymbolRow(KeyboardLayouts.symbols2Row1))
                keyRowsContainer.addView(buildSymbolRow(KeyboardLayouts.symbols2Row2))
                keyRowsContainer.addView(buildSymbolThirdRow(KeyboardLayouts.symbols2Row3, nextLabel = "1/2", nextMode = KeyboardMode.SYMBOLS_1))
                keyRowsContainer.addView(buildBottomRow())
            }
        }
    }

    private fun renderSuggestionStrip() {
        suggestionStrip.removeAllViews()
        for (word in placeholderSuggestions) {
            val tv = TextView(this).apply {
                text = word
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@GboardCloneService, R.color.key_text_light))
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    commitTextAndSpace(word)
                }
            }
            suggestionStrip.addView(tv)
        }
    }

    // -------------------------------------------------------------------------------
    // Row builders
    // -------------------------------------------------------------------------------

    private fun newRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) }
    }

    private fun buildLetterRow(letters: List<String>, sideInset: Float): LinearLayout {
        val row = newRow()
        if (sideInset > 0f) row.addView(spacer(sideInset))
        for (letter in letters) {
            row.addView(makeCharKey(letter))
        }
        if (sideInset > 0f) row.addView(spacer(sideInset))
        return row
    }

    private fun buildThirdLetterRow(): LinearLayout {
        val row = newRow()
        row.addView(makeSpecialKey(shiftLabel(), weight = 1.5f, isToggle = true) {
            onShiftPressed()
        })
        for (letter in KeyboardLayouts.lettersRow3) {
            row.addView(makeCharKey(letter))
        }
        row.addView(makeSpecialKey("⌫", weight = 1.5f, repeatable = true) {
            onDeletePressed()
        })
        return row
    }

    private fun buildSymbolRow(symbols: List<String>): LinearLayout {
        val row = newRow()
        for (s in symbols) row.addView(makeCharKey(s))
        return row
    }

    private fun buildSymbolThirdRow(symbols: List<String>, nextLabel: String, nextMode: KeyboardMode): LinearLayout {
        val row = newRow()
        row.addView(makeSpecialKey(nextLabel, weight = 1.5f) {
            mode = nextMode
            renderKeyboard()
        })
        for (s in symbols) row.addView(makeCharKey(s))
        row.addView(makeSpecialKey("⌫", weight = 1.5f, repeatable = true) {
            onDeletePressed()
        })
        return row
    }

    private fun buildBottomRow(): LinearLayout {
        val row = newRow()
        val symbolsLabel = if (mode == KeyboardMode.LETTERS) "?123" else "ABC"
        row.addView(makeSpecialKey(symbolsLabel, weight = 1.5f) {
            mode = if (mode == KeyboardMode.LETTERS) KeyboardMode.SYMBOLS_1 else KeyboardMode.LETTERS
            renderKeyboard()
        })
        row.addView(makeSpecialKey(",", weight = 1f) {
            commitText(",")
        })
        row.addView(makeSpecialKey("space", weight = 4f) {
            commitText(" ")
        })
        row.addView(makeSpecialKey(".", weight = 1f) {
            commitText(".")
        })
        row.addView(makeSpecialKey("⏎", weight = 1.5f) {
            onEnterPressed()
        })
        return row
    }

    // -------------------------------------------------------------------------------
    // Key factories
    // -------------------------------------------------------------------------------

    private fun spacer(weight: Float): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(48), weight)
    }

    private fun makeCharKey(char: String): Button {
        val displayChar = applyShiftToChar(char)
        return Button(this).apply {
            text = displayChar
            isAllCaps = false
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@GboardCloneService, R.color.key_text))
            background = ContextCompat.getDrawable(this@GboardCloneService, R.drawable.key_bg)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                commitText(applyShiftToChar(char))
                consumeOneShotShift()
            }
        }
    }

    private fun makeSpecialKey(
        label: String,
        weight: Float,
        isToggle: Boolean = false,
        repeatable: Boolean = false,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@GboardCloneService, R.color.key_text_light))
            if (isToggle && shiftState != ShiftState.OFF) {
                setTextColor(ContextCompat.getColor(this@GboardCloneService, R.color.key_accent_bg))
                typeface = Typeface.DEFAULT_BOLD
            }
            background = ContextCompat.getDrawable(this@GboardCloneService, R.drawable.special_key_bg)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply {
                marginStart = dp(2); marginEnd = dp(2)
            }

            if (repeatable) {
                setupRepeatOnHold(this, action)
            } else {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    action()
                }
            }
        }
    }

    /** Enables press-and-hold auto-repeat, used for the backspace key. */
    private fun setupRepeatOnHold(view: View, action: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    action()
                    repeatRunnable = object : Runnable {
                        override fun run() {
                            action()
                            repeatHandler.postDelayed(this, 60)
                        }
                    }
                    repeatHandler.postDelayed(repeatRunnable!!, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repeatRunnable?.let { repeatHandler.removeCallbacks(it) }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    // -------------------------------------------------------------------------------
    // Key actions
    // -------------------------------------------------------------------------------

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun commitTextAndSpace(word: String) {
        currentInputConnection?.commitText("$word ", 1)
    }

    private fun onDeletePressed() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun onEnterPressed() {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun onShiftPressed() {
        val now = System.currentTimeMillis()
        shiftState = when {
            now - lastShiftTapTime < 300 -> ShiftState.CAPS_LOCK
            shiftState == ShiftState.OFF -> ShiftState.SHIFT
            else -> ShiftState.OFF
        }
        lastShiftTapTime = now
        renderKeyboard()
    }

    /** A one-shot SHIFT (not caps-lock) reverts to OFF after a single character is typed. */
    private fun consumeOneShotShift() {
        if (shiftState == ShiftState.SHIFT) {
            shiftState = ShiftState.OFF
            renderKeyboard()
        }
    }

    private fun shiftLabel(): String = when (shiftState) {
        ShiftState.OFF -> "⇧"
        ShiftState.SHIFT -> "⬆"
        ShiftState.CAPS_LOCK -> "⇪"
    }

    private fun applyShiftToChar(char: String): String {
        if (mode != KeyboardMode.LETTERS) return char
        return if (shiftState != ShiftState.OFF) char.uppercase() else char
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }
}
