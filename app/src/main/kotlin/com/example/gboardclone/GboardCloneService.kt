package com.example.gboardclone

import android.content.Intent
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.Manifest
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
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
    private var shiftKey: ImageView? = null
    private var pendingScale = Prefs.heightScale
    private var keyPopup: PopupWindow? = null

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
        registerClipboardListener()
    }

    private fun registerClipboardListener() {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.addPrimaryClipChangedListener {
                try {
                    val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!text.isNullOrEmpty()) Prefs.addClipboard(text)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
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

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        dismissPopups()
    }

    private fun dismissPopups() {
        try { keyPopup?.dismiss() } catch (_: Exception) {}
        try { popup?.dismiss() } catch (_: Exception) {}
        try { variantPicker?.dismiss() } catch (_: Exception) {}
        keyPopup = null
        popup = null
        variantPicker = null
        variantButtons = emptyList()
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
        dismissPopups()
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

    private val bgAccent get() = themeColor(R.color.key_accent_bg, R.color.key_accent_bg)
    private val bgAccentPressed get() = themeColor(R.color.key_accent_bg_pressed, R.color.key_accent_bg_pressed)
    private val txAccent: Int get() = 0xFFFFFFFF.toInt()

    private val keyH get() = dp((48 * Prefs.heightScale).toInt().coerceAtLeast(30))
    private val fnH get() = dp((30 * Prefs.heightScale).toInt().coerceAtLeast(22))

    private fun themeColor(light: Int, dark: Int): Int =
        ContextCompat.getColor(this, if (Prefs.isDark) dark else light)

    private fun makeKeyDrawable(normal: Int, pressed: Int): StateListDrawable {
        val border = if (!Prefs.isDarkNow) resources.getColor(R.color.key_border, theme) else 0
        val bw = if (!Prefs.isDarkNow) dp(1) else 0
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), roundRect(pressed, border, bw))
            addState(intArrayOf(), roundRect(normal, border, bw))
        }
    }

    private fun roundRect(color: Int, radiusDp: Int = 8, strokeColor: Int = 0, strokeWidth: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
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
        keyRowsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(keyRowsContainer)
        container.addView(makeResizeHandle())
        return container
    }

    private fun makeResizeHandle(): View {
        val handle = View(this).apply {
            background = roundRect(bgAccent, 6)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(9)).apply {
                setMargins(dp(90), dp(3), dp(90), dp(3))
            }
        }
        var startY = 0f
        var startScale = 1f
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    startScale = Prefs.heightScale
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = startY - e.rawY
                    val s = (startScale + dy / 450f).coerceIn(0.7f, 1.6f)
                    rootView.scaleY = s
                    pendingScale = s
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    Prefs.heightScale = pendingScale
                    rootView.scaleY = 1f
                    renderKeyboard()
                    true
                }
                else -> false
            }
        }
        return handle
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
        } catch (e: Exception) {
            keyRowsContainer.removeAllViews()
            keyRowsContainer.addView(errorView("Render error: ${e.message}"))
        }
    }

    private fun functionStrip() {
        val row = newRow()
        if (Prefs.toolbarCollapsed) {
            row.addView(makeIconKey(R.drawable.ic_chevron, 0.8f, rotate = 0f) {
                animateToolbar(collapse = false)
            })
            keyRowsContainer.addView(row)
            return
        }
        // Collapse button (left corner)
        row.addView(makeIconKey(R.drawable.ic_chevron, 0.8f, rotate = 180f) {
            animateToolbar(collapse = true)
        })
        row.addView(makeIconKey(R.drawable.ic_mic, 0.8f) { startVoice() })
        row.addView(makeIconKey(R.drawable.ic_clipboard, 0.8f) { mode = KeyboardMode.CLIPBOARD; renderKeyboard() })
        row.addView(makeIconKey(R.drawable.ic_settings, 0.8f) { openSettings() })
        keyRowsContainer.addView(row)
    }

    private fun animateToolbar(collapse: Boolean) {
        val cur = keyRowsContainer.getChildAt(0) as? View ?: return
        val w = (if (cur.width > 0) cur.width else rootView.width).toFloat()
        val outX = if (collapse) w else -w
        cur.animate().translationX(outX).alpha(0f).setDuration(140).withEndAction {
            Prefs.toolbarCollapsed = collapse
            renderKeyboard()
            val nw = keyRowsContainer.getChildAt(0)
            nw.translationX = -outX
            nw.alpha = 0f
            nw.animate().translationX(0f).alpha(1f).setDuration(140)
        }
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
        shiftKey = makeShiftKey()
        row.addView(shiftKey!!)
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
        row.addView(makeSpecialKey(symbolsLabel, weight = 1.5f) {
            mode = if (mode == KeyboardMode.LETTERS) KeyboardMode.SYMBOLS_1 else KeyboardMode.LETTERS
            renderKeyboard()
        })
        row.addView(makeIconKey(R.drawable.ic_emoji, 1f, height = keyH) { mode = KeyboardMode.EMOJI; renderKeyboard() })
        row.addView(makeSpecialKey(",", weight = 1f) { typeChar(",") })
        row.addView(makeSpecialKey("space", weight = 4f, longPress = { showImePicker() }) { onSpace() })
        row.addView(makeSpecialKey(".", weight = 1f) { typeChar(".") })
        row.addView(makeEnterKey(1.5f) { onEnterPressed() })
        return row
    }

    // ---------------------------------------------------------------------------
    // Key factories
    // ---------------------------------------------------------------------------
    private fun spacer(weight: Float): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, keyH, weight)
    }

    private fun makeCharKey(char: String): View {
        val hint = KeyboardLayouts.hintMap[char.lowercase()]
            ?: KeyboardLayouts.longPressVariants[char.lowercase()]?.firstOrNull() ?: ""
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            background = makeKeyDrawable(bgKey, bgKeyPressed)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, keyH, 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            }
        }
        if (hint.isNotEmpty()) {
            container.addView(TextView(this).apply {
                text = hint
                textSize = 10f
                setTextColor(txSpecial)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            })
        }
        val label = TextView(this).apply {
            text = applyCase(char)
            textSize = 20f
            setTextColor(txKey)
            gravity = Gravity.CENTER
            isAllCaps = false
        }
        container.addView(label)
        val variants = KeyboardLayouts.longPressVariants[char.lowercase()]
        if (variants.isNullOrEmpty()) {
            container.setOnClickListener {
                haptic(container); clickSound()
                typeChar(char)
                consumeOneShotShift()
            }
            container.setOnTouchListener(keyPopTouch(container, applyCase(char)))
        } else {
            setupVariantKey(container, char, variants)
        }
        return container
    }

    private fun makeSpecialKey(
        label: String,
        weight: Float,
        repeatable: Boolean = false,
        longPress: (() -> Unit)? = null,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(txSpecial)
            background = makeKeyDrawable(bgSpecial, bgSpecialPressed)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, keyH, weight).apply {
                marginStart = dp(2); marginEnd = dp(2)
            }
            if (repeatable) setupRepeatOnHold(this, action)
            else setOnClickListener { haptic(this); clickSound(); action() }
            if (longPress != null) setOnLongClickListener {
                haptic(this); longPress(); true
            }
        }
    }

    private fun makeShiftKey(): ImageView {
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = makeKeyDrawable(bgSpecial, bgSpecialPressed)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, keyH, 1.5f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            }
            setOnClickListener { haptic(this); clickSound(); onShiftPressed() }
            updateShiftKey(this)
        }
    }

    private fun makeEnterKey(weight: Float, action: () -> Unit): ImageView {
        return ImageView(this).apply {
            setImageResource(R.drawable.ic_enter)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setColorFilter(txAccent, PorterDuff.Mode.SRC_IN)
            background = makeKeyDrawable(bgAccent, bgAccentPressed)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, keyH, weight).apply {
                marginStart = dp(2); marginEnd = dp(2)
            }
            setOnClickListener { haptic(this); clickSound(); action() }
        }
    }

    private fun makeIconKey(
        drawableId: Int,
        weight: Float,
        rotate: Float = 0f,
        height: Int = fnH,
        tintAccent: Boolean = false,
        action: () -> Unit
    ): ImageView {
        return ImageView(this).apply {
            setImageResource(drawableId)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            rotation = rotate
            setColorFilter(
                if (tintAccent) bgAccent else txSpecial,
                PorterDuff.Mode.SRC_IN
            )
            background = makeKeyDrawable(bgSpecial, bgSpecialPressed)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, height, weight).apply {
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
        try {
            keyPopup?.dismiss()
            keyPopup = null
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
        } catch (_: Exception) {
        }
    }

    private var popup: PopupWindow? = null

    private var variantPicker: PopupWindow? = null
    private var variantButtons: List<View> = emptyList()
    private var variantPickerX = 0
    private var variantCellW = 0

    private fun keyPopTouch(view: View, text: String): View.OnTouchListener {
        return View.OnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    try { showKeyPopup(v, text) } catch (_: Exception) {}
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    try { keyPopup?.dismiss() } catch (_: Exception) {}
                    keyPopup = null
                    false
                }
                else -> false
            }
        }
    }

    private fun setupVariantKey(view: View, char: String, variants: List<String>) {
        var longPressed = false
        var active = false
        var selected = -1
        val handler = Handler(Looper.getMainLooper())
        var runnable: Runnable? = null
        view.setOnClickListener {
            if (!longPressed) {
                haptic(view); clickSound(); typeChar(char); consumeOneShotShift()
            }
        }
        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressed = false
                    active = false
                    selected = -1
                    try { showKeyPopup(v, applyCase(char)) } catch (_: Exception) {}
                    runnable = Runnable {
                        longPressed = true
                        try { keyPopup?.dismiss() } catch (_: Exception) {}
                        keyPopup = null
                        showVariantPicker(v, variants)
                        active = true
                    }
                    handler.postDelayed(runnable!!, 350)
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (active) updateVariantSelection(e.rawX, e.rawY)
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(runnable!!)
                    try { keyPopup?.dismiss() } catch (_: Exception) {}
                    keyPopup = null
                    if (active) {
                        val chosen = if (selected in variants.indices) variants[selected] else applyCase(char)
                        typeCharRaw(chosen)
                        dismissVariantPicker()
                        active = false
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun showVariantPicker(anchor: View, variants: List<String>) {
        try {
            val cell = dp(44)
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = makeKeyDrawable(bgPopup, bgPopup)
            }
            val buttons = variants.map { sym ->
                val b = TextView(this).apply {
                    text = sym
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(txPopup)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    background = makeKeyDrawable(bgPopup, bgPopup)
                    layoutParams = LinearLayout.LayoutParams(cell, cell).apply {
                        marginStart = dp(2); marginEnd = dp(2)
                    }
                }
                container.addView(b)
                b
            }
            val pw = PopupWindow(this).apply {
                contentView = container
                isOutsideTouchable = true
                setBackgroundDrawable(roundRect(bgPopup, 12))
            }
            variantPicker = pw
            variantButtons = buttons
            variantCellW = cell + dp(4)
            pw.showAsDropDown(anchor, 0, -(anchor.height + dp(6)))
            val loc = IntArray(2)
            container.getLocationOnScreen(loc)
            variantPickerX = loc[0]
            highlightVariant(-1)
        } catch (_: Exception) {
        }
    }

    private fun updateVariantSelection(rawX: Float, rawY: Float) {
        val idx = if (rawX >= variantPickerX)
            ((rawX - variantPickerX) / variantCellW).toInt().coerceIn(0, variantButtons.lastIndex)
        else -1
        highlightVariant(idx)
    }

    private fun highlightVariant(idx: Int) {
        variantButtons.forEachIndexed { i, b ->
            val sel = i == idx
            b.background = makeKeyDrawable(
                if (sel) bgAccent else bgPopup,
                if (sel) bgAccentPressed else bgPopup
            )
            (b as TextView).setTextColor(if (sel) txAccent else txPopup)
        }
    }

    private fun dismissVariantPicker() {
        try { variantPicker?.dismiss() } catch (_: Exception) {}
        variantPicker = null
        variantButtons = emptyList()
    }

    private fun showKeyPopup(anchor: View, text: String) {
        try {
            val tv = TextView(this).apply {
                this.text = text
                textSize = 28f
                setTextColor(txPopup)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = makeKeyDrawable(bgPopup, bgPopup)
            }
            val p = PopupWindow(this).apply {
                contentView = tv
                isOutsideTouchable = true
                setBackgroundDrawable(roundRect(bgPopup, 12))
            }
            keyPopup = p
            p.showAsDropDown(anchor, 0, -(anchor.height + dp(10)))
        } catch (_: Exception) {
        }
    }

    private fun showImePicker() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------------------
    // Key actions / text
    // ---------------------------------------------------------------------------
    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun typeCharRaw(ch: String) {
        commitText(ch)
    }

    private fun typeChar(ch: String) {
        commitText(applyCase(ch))
    }

    private fun onSpace() {
        val w = currentWord()
        commitText(" ")
        if (w.isNotEmpty()) {
            SuggestionEngine.learn(w)
            saveLearned()
        }
        voicePartialLength = 0
    }

    private fun onDeletePressed() {
        currentInputConnection?.deleteSurroundingText(1, 0)
        voicePartialLength = 0
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

    private fun consumeOneShotShift() {
        if (shiftState == ShiftState.SHIFT) {
            shiftState = ShiftState.OFF
            shiftKey?.let { updateShiftKey(it) } ?: renderKeyboard()
        }
    }

    private fun updateShiftKey(iv: ImageView) {
        val active = shiftState != ShiftState.OFF
        iv.setImageResource(
            if (shiftState == ShiftState.CAPS_LOCK) R.drawable.ic_shift_lock
            else R.drawable.ic_shift
        )
        iv.setColorFilter(
            if (active) bgAccent else txSpecial,
            PorterDuff.Mode.SRC_IN
        )
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

    private fun saveLearned() {
        Prefs.learnedWords = SuggestionEngine.getLearned()
    }

    // ---------------------------------------------------------------------------
    // Emoji panel
    // ---------------------------------------------------------------------------
    private fun emojiPanel() {
        val pageW = resources.displayMetrics.widthPixels
        val names = listOf(
            "Recents", "Smileys", "Animals", "Food", "Activity",
            "Travel", "Objects", "Symbols", "Flags"
        )
        val icons = listOf(
            R.drawable.ic_cat_recent, R.drawable.ic_emoji, R.drawable.ic_cat_animal,
            R.drawable.ic_cat_food, R.drawable.ic_cat_sport, R.drawable.ic_cat_travel,
            R.drawable.ic_cat_object, R.drawable.ic_cat_symbol, R.drawable.ic_cat_flag
        )
        val lists: List<List<String>> =
            listOf(Prefs.emojiRecents) + EmojiData.categories.map { it.second }

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val catLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(txKey)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = roundRect(bgPopup, 16)
            visibility = View.GONE
        }

        val pagesLL = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(pageW * lists.size, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        for (list in lists) {
            val page = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(pageW, dp(250))
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            page.addView(buildEmojiGrid(list))
            pagesLL.addView(page)
        }
        val pager = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250))
            isHorizontalScrollBarEnabled = false
            fillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        pager.addView(pagesLL)

        val pagerFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250))
        }
        pagerFrame.addView(pager)
        catLabel.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(10)
        }
        pagerFrame.addView(catLabel)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val tabViews = mutableListOf<ImageView>()
        for (i in lists.indices) {
            val iv = ImageView(this).apply {
                setImageResource(icons[i])
                setColorFilter(txSpecial, PorterDuff.Mode.SRC_IN)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    marginStart = dp(2); marginEnd = dp(2)
                }
                setOnClickListener { pager.smoothScrollTo(i * pageW, 0); showCat(i) }
            }
            tabViews.add(iv)
            tabs.addView(iv)
        }

        val curCat = intArrayOf(0)
        fun showCat(i: Int) {
            if (i != curCat[0]) {
                curCat[0] = i
                tabViews.forEachIndexed { idx, v ->
                    v.setBackgroundColor(if (idx == i) bgSpecialPressed else 0)
                    v.setColorFilter(if (idx == i) txAccent else txSpecial, PorterDuff.Mode.SRC_IN)
                }
            }
            catLabel.text = names[i]
            catLabel.visibility = View.VISIBLE
            catLabel.alpha = 1f
            catLabel.animate().cancel()
            catLabel.animate().alpha(0f).setStartDelay(600).setDuration(400)
                .withEndAction { catLabel.visibility = View.GONE }
        }
        showCat(0)

        var settle: Runnable? = null
        pager.setOnScrollChangeListener { _, x, _, _, _ ->
            val idx = ((x + pageW / 2) / pageW).toInt().coerceIn(0, lists.lastIndex)
            if (idx != curCat[0]) showCat(idx)
            settle?.let { pager.removeCallbacks(it) }
            settle = Runnable { pager.smoothScrollTo(idx * pageW, 0) }
            pager.postDelayed(settle!!, 120)
        }

        val bar = newRow()
        bar.addView(makeSpecialKey("ABC", weight = 2f) {
            mode = KeyboardMode.LETTERS
            renderKeyboard()
        })
        bar.addView(makeSpecialKey("⌫", weight = 2f, repeatable = true) { onDeletePressed() })

        wrap.addView(pagerFrame)
        wrap.addView(tabs)
        wrap.addView(bar)
        keyRowsContainer.addView(wrap)
    }

    private fun buildEmojiGrid(list: List<String>): View {
        if (list.isEmpty()) {
            return TextView(this).apply {
                text = "No recent emojis"
                setTextColor(txSpecial)
                gravity = Gravity.CENTER
                textSize = 15f
                setPadding(dp(12), dp(40), dp(12), dp(12))
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val rows = list.chunked(7)
        for (rowItems in rows) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (e in rowItems) {
                val b = TextView(this).apply {
                    text = e
                    textSize = 24f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                    setOnClickListener {
                        haptic(this); clickSound()
                        commitText(e)
                        Prefs.addEmojiRecent(e)
                    }
                }
                r.addView(b)
            }
            for (k in rowItems.size until 7) {
                r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f) })
            }
            col.addView(r)
        }
        return col
    }

    // ---------------------------------------------------------------------------
    // Clipboard panel
    // ---------------------------------------------------------------------------
    private fun clipboardPanel() {
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
        val pinned = Prefs.pinned
        val recent = Prefs.clipboard.filter { !pinned.contains(it) }
        if (pinned.isEmpty() && recent.isEmpty()) {
            col.addView(TextView(this).apply {
                text = getString(R.string.clipboard_empty)
                setTextColor(txSpecial)
                textSize = 15f
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
        }
        fun addItem(text: String, isPinned: Boolean) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = makeKeyDrawable(bgKey, bgKeyPressed)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            val tv = TextView(this).apply {
                this.text = text
                setTextColor(txKey)
                textSize = 15f
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    haptic(this); clickSound()
                    commitText(text)
                    toast("Pasted")
                }
            }
            val pin = ImageView(this).apply {
                setImageResource(R.drawable.ic_pin)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(
                    if (isPinned) themeColor(R.color.key_accent_bg, R.color.key_accent_bg)
                    else txSpecial,
                    PorterDuff.Mode.SRC_IN
                )
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setOnClickListener {
                    Prefs.togglePin(text)
                    renderKeyboard()
                }
            }
            row.addView(tv)
            row.addView(pin)
            col.addView(row)
        }
        for (text in pinned) addItem(text, true)
        for (text in recent) addItem(text, false)
        scroll.addView(col)

        val bar = newRow()
        bar.addView(makeSpecialKey("ABC", weight = 2f) {
            mode = KeyboardMode.LETTERS
            renderKeyboard()
        })
        bar.addView(makeSpecialKey("Clear", weight = 2f) {
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            val i = Intent(this, PermissionActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            toast("Grant microphone permission for voice typing")
            return
        }
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
