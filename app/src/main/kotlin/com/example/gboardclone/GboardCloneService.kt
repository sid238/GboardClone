package com.example.gboardclone

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.inputmethodservice.InputMethodService

class GboardCloneService : InputMethodService() {

    private var mode: KeyboardMode = KeyboardMode.LETTERS
    private var shiftState: ShiftState = ShiftState.OFF
    private var lastShiftTapTime = 0L

    private lateinit var rootView: LinearLayout
    private lateinit var keyRowsContainer: LinearLayout
    private lateinit var suggestionStrip: LinearLayout

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    private var recognizer: SpeechRecognizer? = null
    private var voicePartialLength = 0

    private var tone: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        SuggestionEngine.loadLearned(Prefs.learnedWords)
        if (Prefs.sound) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40)
    }

    override fun onCreateInputView(): View {
        try {
            rootView = buildRootView()
            mode = KeyboardMode.LETTERS
            shiftState = ShiftState.OFF
            renderKeyboard()
            return rootView
        } catch (e: Exception) {
            return errorView("Keyboard init error: ${e.message}")
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::keyRowsContainer.isInitialized) return
        mode = KeyboardMode.LETTERS
        shiftState = ShiftState.OFF
        SuggestionEngine.resetContext()
        renderKeyboard()
    }

    private fun errorView(msg: String): View {
        return TextView(this).apply {
            text = msg
            setTextColor(0xFFB00020.toInt())
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        tone?.release()
    }

    // ---------------------------------------------------------------------------
    // Theme-aware colors
    // ---------------------------------------------------------------------------
    private val bgKeyboard get() = themeColor(R.color.keyboard_bg, R.color.keyboard_bg_dark)
    private val bgSuggestion get() = themeColor(R.color.suggestion_bg, R.color.suggestion_bg_dark)
    private val bgKey get() = themeColor(R.color.key_bg, R.color.key_bg_dark)
    private val bgKeyPressed get() = themeColor(R.color.key_bg_pressed, R.color.key_bg_pressed_dark)
    private val bgSpecial get() = themeColor(R.color.key_special_bg, R.color.key_special_bg_dark)
    private val bgSpecialPressed get() = themeColor(R.color.key_special_bg_pressed, R.color.key_special_bg_pressed_dark)
    private val txKey get() = themeColor(R.color.key_text, R.color.key_text_dark)
    private val txSpecial get() = themeColor(R.color.key_text_light, R.color.key_text_light_dark)
    private val bgPopup get() = themeColor(R.color.popup_bg, R.color.popup_bg_dark)
    private val txPopup get() = themeColor(R.color.popup_text, R.color.popup_text_dark)

    private fun themeColor(light: Int, dark: Int): Int =
        ContextCompat.getColor(this, if (Prefs.isDark) dark else light)

    private fun makeKeyDrawable(normal: Int, pressed: Int): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), roundRect(pressed))
            addState(intArrayOf(), roundRect(normal))
        }
    }

    private fun roundRect(color: Int, radiusDp: Int = 8): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun haptic(v: View) {
        if (Prefs.vibration) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun clickSound() {
        if (Prefs.sound) {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40)
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, 15)
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------------------
    // View construction
    // ---------------------------------------------------------------------------
    private fun buildRootView(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgKeyboard)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        suggestionStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bgSuggestion)
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
        try {
            rootView.setBackgroundColor(bgKeyboard)
            keyRowsContainer.removeAllViews()
            when (mode) {
            KeyboardMode.LETTERS -> {
                functionStrip()
                if (Prefs.numberRow) numberRowView()
                keyRowsContainer.addView(buildLetterRow(KeyboardLayouts.lettersRow1, 0f))
                keyRowsContainer.addView(buildLetterRow(KeyboardLayouts.lettersRow2, 0.5f))
                keyRowsContainer.addView(buildThirdLetterRow())
                keyRowsContainer.addView(buildBottomRow())
            }
            KeyboardMode.SYMBOLS_1 -> {
                functionStrip()
                keyRowsContainer.addView(buildSymbolRow(KeyboardLayouts.symbols1Row1))
                keyRowsContainer.addView(buildSymbolRow(KeyboardLayouts.symbols1Row2))
                keyRowsContainer.addView(buildSymbolThirdRow(KeyboardLayouts.symbols1Row3, "2/2", KeyboardMode.SYMBOLS_2))
                keyRowsContainer.addView(buildBottomRow())
            }
            KeyboardMode.SYMBOLS_2 -> {
                functionStrip()
                keyRowsContainer.addView(buildSymbolRow(KeyboardLayouts.symbols2Row1))
                keyRowsContainer.addView(buildSymbolRow(KeyboardLayouts.symbols2Row2))
                keyRowsContainer.addView(buildSymbolThirdRow(KeyboardLayouts.symbols2Row3, "1/2", KeyboardMode.SYMBOLS_1))
                keyRowsContainer.addView(buildBottomRow())
            }
            KeyboardMode.EMOJI -> emojiPanel()
            KeyboardMode.CLIPBOARD -> clipboardPanel()
        }
        updateSuggestions()
        } catch (e: Exception) {
            keyRowsContainer.removeAllViews()
            keyRowsContainer.addView(errorView("Render error: ${e.message}"))
        }
    }

    private fun renderSuggestionStrip(words: List<String>) {
        suggestionStrip.removeAllViews()
        suggestionStrip.setBackgroundColor(bgSuggestion)
        for (word in words) {
            val tv = TextView(this).apply {
                text = word
                gravity = Gravity.CENTER
                setTextColor(txKey)
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { replaceWord(word) }
            }
            suggestionStrip.addView(tv)
        }
    }

    private fun functionStrip() {
        val row = newRow()
        row.addView(makeFnKey("🎤", 1.2f) { startVoice() })
        row.addView(makeFnKey("😊", 1.2f) { mode = KeyboardMode.EMOJI; renderKeyboard() })
        row.addView(makeFnKey("📋", 1.2f) { mode = KeyboardMode.CLIPBOARD; renderKeyboard() })
        row.addView(makeFnKey("⚙️", 1.2f) { openSettings() })
        keyRowsContainer.addView(row)
    }

    private fun numberRowView() {
        val row = newRow()
        for (d in "1234567890") row.addView(makeCharKey(d.toString()))
        keyRowsContainer.addView(row)
    }

    // ---------------------------------------------------------------------------
    // Row builders
    // ---------------------------------------------------------------------------
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
        for (letter in letters) row.addView(makeCharKey(letter))
        if (sideInset > 0f) row.addView(spacer(sideInset))
        return row
    }

    private fun buildThirdLetterRow(): LinearLayout {
        val row = newRow()
        row.addView(makeSpecialKey(shiftLabel(), weight = 1.5f, isToggle = true) { onShiftPressed() })
        for (letter in KeyboardLayouts.lettersRow3) row.addView(makeCharKey(letter))
        row.addView(makeSpecialKey("⌫", weight = 1.5f, repeatable = true) { onDeletePressed() })
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
        row.addView(makeSpecialKey("⌫", weight = 1.5f, repeatable = true) { onDeletePressed() })
        return row
    }

    private fun buildBottomRow(): LinearLayout {
        val row = newRow()
        val symbolsLabel = if (mode == KeyboardMode.LETTERS) "?123" else "ABC"
        row.addView(makeSpecialKey("🌐", weight = 1.2f) { onGlobePressed() })
        row.addView(makeSpecialKey(symbolsLabel, weight = 1.5f) {
            mode = if (mode == KeyboardMode.LETTERS) KeyboardMode.SYMBOLS_1 else KeyboardMode.LETTERS
            renderKeyboard()
        })
        row.addView(makeSpecialKey(",", weight = 1f) { typeChar(",") })
        row.addView(makeSpecialKey("space", weight = 4f) { onSpace() })
        row.addView(makeSpecialKey(".", weight = 1f) { typeChar(".") })
        row.addView(makeSpecialKey("⏎", weight = 1.5f) { onEnterPressed() })
        return row
    }

    // ---------------------------------------------------------------------------
    // Key factories
    // ---------------------------------------------------------------------------
    private fun spacer(weight: Float): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(48), weight)
    }

    private fun makeCharKey(char: String): Button {
        val display = applyCase(char)
        return Button(this).apply {
            text = display
            isAllCaps = false
            textSize = 20f
            setTextColor(txKey)
            background = makeKeyDrawable(bgKey, bgKeyPressed)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            }
            setOnClickListener {
                haptic(this); clickSound()
                typeChar(char)
                consumeOneShotShift()
            }
            val variants = KeyboardLayouts.longPressVariants[char.lowercase()]
            if (!variants.isNullOrEmpty()) {
                setOnLongClickListener {
                    showVariantPopup(this, variants) { picked ->
                        typeCharRaw(applyCase(picked))
                    }
                    true
                }
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
            setTextColor(txSpecial)
            if (isToggle && shiftState != ShiftState.OFF) {
                setTextColor(themeColor(R.color.key_accent_bg, R.color.key_accent_bg))
            }
            background = makeKeyDrawable(bgSpecial, bgSpecialPressed)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply {
                marginStart = dp(2); marginEnd = dp(2)
            }
            if (repeatable) setupRepeatOnHold(this, action)
            else setOnClickListener { haptic(this); clickSound(); action() }
        }
    }

    private fun makeFnKey(label: String, weight: Float, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 18f
            setTextColor(txSpecial)
            background = makeKeyDrawable(bgSpecial, bgSpecialPressed)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, dp(40), weight).apply {
                marginStart = dp(2); marginEnd = dp(2)
            }
            setOnClickListener { haptic(this); clickSound(); action() }
        }
    }

    private fun setupRepeatOnHold(view: View, action: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    haptic(v); clickSound()
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

    private fun showVariantPopup(anchor: View, variants: List<String>, onPick: (String) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        for (v in variants) {
            val b = Button(this).apply {
                text = v
                textSize = 20f
                setTextColor(txPopup)
                background = makeKeyDrawable(bgPopup, bgKeyPressed)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    marginStart = dp(2); marginEnd = dp(2)
                }
                setOnClickListener {
                    haptic(this); clickSound()
                    onPick(v)
                    popup?.dismiss()
                }
            }
            container.addView(b)
        }
        val popup = PopupWindow(this).apply {
            contentView = container
            isOutsideTouchable = true
            setBackgroundDrawable(roundRect(bgPopup, 12))
        }
        this.popup = popup
        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val ph = container.measuredHeight
        popup.showAsDropDown(anchor, 0, -(anchor.height + ph + dp(6)))
    }

    private var popup: PopupWindow? = null

    // ---------------------------------------------------------------------------
    // Key actions / text
    // ---------------------------------------------------------------------------
    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun typeCharRaw(ch: String) {
        commitText(ch)
        updateSuggestions()
    }

    private fun typeChar(ch: String) {
        commitText(applyCase(ch))
        updateSuggestions()
    }

    private fun onSpace() {
        val w = currentWord()
        commitText(" ")
        if (w.isNotEmpty()) {
            SuggestionEngine.learn(w)
            saveLearned()
        }
        voicePartialLength = 0
        updateSuggestions()
    }

    private fun onDeletePressed() {
        currentInputConnection?.deleteSurroundingText(1, 0)
        voicePartialLength = 0
        updateSuggestions()
    }

    private fun onEnterPressed() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
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

    private fun onGlobePressed() {
        toast("Language switching is not configured")
    }

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

    private fun applyCase(char: String): String {
        if (mode != KeyboardMode.LETTERS) return char
        return if (shiftState != ShiftState.OFF && char.all { it.isLetter() }) char.uppercase() else char
    }

    private fun currentWord(): String {
        val ic = currentInputConnection ?: return ""
        val txt = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        val m = Regex("[a-zA-Z]+$").find(txt)
        return m?.value ?: ""
    }

    private fun replaceWord(suggestion: String) {
        val w = currentWord()
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(w.length, 0)
        ic.commitText("$suggestion ", 1)
        SuggestionEngine.learn(suggestion)
        saveLearned()
        voicePartialLength = 0
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (mode == KeyboardMode.EMOJI || mode == KeyboardMode.CLIPBOARD) return
        val sugg = SuggestionEngine.suggestions(currentWord())
        renderSuggestionStrip(sugg.take(3))
    }

    private fun saveLearned() {
        Prefs.learnedWords = SuggestionEngine.getLearned()
    }

    // ---------------------------------------------------------------------------
    // Emoji panel
    // ---------------------------------------------------------------------------
    private fun emojiPanel() {
        suggestionStrip.removeAllViews()
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val cats: List<Pair<String, List<String>>> =
            listOf("🕘" to Prefs.emojiRecents) + EmojiData.categories

        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val grid = GridView(this).apply {
            numColumns = 7
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { height = dp(250) }
        }

        fun setGrid(list: List<String>) {
            val safe = if (list.isEmpty()) EmojiData.all else list
            grid.adapter = ArrayAdapter(this, R.layout.emoji_item, R.id.emojiText, safe)
        }

        for ((icon, list) in cats) {
            val tab = Button(this).apply {
                text = icon
                textSize = 20f
                setTextColor(txSpecial)
                background = makeKeyDrawable(bgSpecial, bgSpecialPressed)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    marginStart = dp(2); marginEnd = dp(2)
                }
                setOnClickListener {
                    haptic(this); clickSound()
                    setGrid(list)
                }
            }
            tabRow.addView(tab)
        }
        setGrid(Prefs.emojiRecents)

        grid.setOnItemClickListener { _, _, position, _ ->
            val emoji = grid.adapter.getItem(position) as String
            haptic(grid); clickSound()
            commitText(emoji)
            Prefs.addEmojiRecent(emoji)
        }

        val bar = newRow()
        bar.addView(makeSpecialKey("ABC", weight = 2f) {
            mode = KeyboardMode.LETTERS
            renderKeyboard()
        })
        bar.addView(makeSpecialKey("⌫", weight = 2f, repeatable = true) { onDeletePressed() })

        wrap.addView(tabRow)
        wrap.addView(grid)
        wrap.addView(bar)
        keyRowsContainer.addView(wrap)
    }

    // ---------------------------------------------------------------------------
    // Clipboard panel
    // ---------------------------------------------------------------------------
    private fun clipboardPanel() {
        suggestionStrip.removeAllViews()
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val items = Prefs.clipboard
        if (items.isEmpty()) {
            col.addView(TextView(this).apply {
                text = getString(R.string.clipboard_empty)
                setTextColor(txSpecial)
                textSize = 15f
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
        }
        for (text in items) {
            val tv = TextView(this).apply {
                this.text = text
                setTextColor(txKey)
                textSize = 15f
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = makeKeyDrawable(bgKey, bgKeyPressed)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    haptic(this); clickSound()
                    commitText(text)
                    toast("Pasted")
                }
            }
            col.addView(tv)
        }
        scroll.addView(col)

        val bar = newRow()
        bar.addView(makeSpecialKey("ABC", weight = 2f) {
            mode = KeyboardMode.LETTERS
            renderKeyboard()
        })
        bar.addView(makeSpecialKey("🗑", weight = 2f) {
            Prefs.clipboard = emptyList()
            renderKeyboard()
        })
        wrap.addView(scroll)
        wrap.addView(bar)
        keyRowsContainer.addView(wrap)
    }

    // ---------------------------------------------------------------------------
    // Voice typing
    // ---------------------------------------------------------------------------
    private fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Voice typing unavailable on this device")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                voicePartialLength = 0
                if (error != SpeechRecognizer.ERROR_NO_MATCH) toast("Voice error: $error")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrEmpty()) {
                    val ic = currentInputConnection ?: return
                    if (voicePartialLength > 0) ic.deleteSurroundingText(voicePartialLength, 0)
                    ic.commitText(text, 1)
                    updateSuggestions()
                }
                voicePartialLength = 0
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                val ic = currentInputConnection ?: return
                if (voicePartialLength > 0) ic.deleteSurroundingText(voicePartialLength, 0)
                ic.commitText(text, 1)
                voicePartialLength = text.length
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    // ---------------------------------------------------------------------------
    // Settings launch
    // ---------------------------------------------------------------------------
    private fun openSettings() {
        val i = Intent(this, SettingsActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
