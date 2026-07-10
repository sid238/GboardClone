package com.gboardclone

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

class KeyboardView(context: Context, private val service: KeyboardService) : View(context) {

    private var currentLayout: List<List<KeyData>> = KeyboardLayouts.QWERTY_LOWER
    private var isShifted = false
    private var isCapsLock = false
    private var currentMode = KeyboardMode.LETTERS

    private var theme: KeyboardTheme = KeyboardTheme.DARK
    private var keyHeight = 0f
    private val keyMargin = 3.dp
    private val keyRadius = 6.dp
    private val suggestionHeight = 42.dp

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val suggestionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint()

    private var pressedKey: KeyData? = null
    private var pressedRow = -1
    private var pressedCol = -1
    private var showPreview = true

    private var soundPool: SoundPool? = null
    private var clickSoundId = 0
    private var vibrator: Vibrator? = null
    private var vibrationEnabled = true
    private var soundEnabled = true

    private val keyRects = mutableMapOf<Pair<Int, Int>, RectF>()
    private val suggestions = mutableListOf("the", "and", "for")

    private val Number.dp: Float
        get() = this.toFloat() * resources.displayMetrics.density

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibrator = vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attrs)
            .build()
        clickSoundId = soundPool?.load(context, R.raw.key_click, 1) ?: 0

        dividerPaint.strokeWidth = 0.5.dp
    }

    fun setTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        invalidate()
    }

    fun setVibrationEnabled(enabled: Boolean) { vibrationEnabled = enabled }
    fun setSoundEnabled(enabled: Boolean) { soundEnabled = enabled }
    fun setPreviewEnabled(enabled: Boolean) { showPreview = enabled }

    fun setSuggestions(words: List<String>) {
        suggestions.clear()
        suggestions.addAll(words.take(3))
        invalidate()
    }

    fun setLayout(layout: List<List<KeyData>>) {
        currentLayout = layout
        keyRects.clear()
        requestLayout()
        invalidate()
    }

    fun setShifted(shifted: Boolean) {
        isShifted = shifted
        currentLayout = if (shifted) KeyboardLayouts.QWERTY_UPPER else KeyboardLayouts.QWERTY_LOWER
        keyRects.clear()
        invalidate()
    }

    fun setMode(mode: KeyboardMode) {
        currentMode = mode
        currentLayout = when (mode) {
            KeyboardMode.LETTERS -> if (isShifted) KeyboardLayouts.QWERTY_UPPER else KeyboardLayouts.QWERTY_LOWER
            KeyboardMode.NUMBERS -> KeyboardLayouts.NUMBERS_LAYOUT
            KeyboardMode.SYMBOLS -> KeyboardLayouts.SYMBOLS_LAYOUT
        }
        keyRects.clear()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val screenHeight = resources.displayMetrics.heightPixels
        val keyboardHeight = (screenHeight * 0.42f).toInt()
        setMeasuredDimension(width, keyboardHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        keyRects.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val suggestionAreaHeight = suggestionHeight
        val keyboardAreaHeight = height.toFloat() - suggestionAreaHeight

        drawSuggestionStrip(canvas, suggestionAreaHeight)

        val totalRows = currentLayout.size
        val availableHeight = keyboardAreaHeight - keyMargin * (totalRows + 1)
        keyHeight = availableHeight / totalRows

        val paddingHorizontal = 4.dp

        for (row in currentLayout.indices) {
            val rowData = currentLayout[row]
            val totalWidth = width.toFloat() - paddingHorizontal * 2
            val totalRatio = rowData.sumOf { it.widthRatio.toDouble() }.toFloat()
            val keyW = totalWidth / totalRatio

            var xOffset = paddingHorizontal
            val yOffset = suggestionHeight + keyMargin + row * (keyHeight + keyMargin)

            for (col in rowData.indices) {
                val key = rowData[col]
                val kWidth = key.widthRatio * keyW

                val rect = RectF(xOffset, yOffset, xOffset + kWidth, yOffset + keyHeight)
                keyRects[Pair(row, col)] = RectF(rect)

                val isPressed = pressedRow == row && pressedCol == col
                drawKey(canvas, key, rect, isPressed)

                xOffset += kWidth + keyMargin
            }
        }
    }

    private fun drawSuggestionStrip(canvas: Canvas, height: Float) {
        val bgPaint = Paint()
        bgPaint.color = theme.suggestionBackgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height, bgPaint)

        dividerPaint.color = theme.borderColor
        canvas.drawLine(0f, height, width.toFloat(), height, dividerPaint)

        if (suggestions.isEmpty()) return

        val itemWidth = width.toFloat() / suggestions.size
        suggestionTextPaint.color = theme.suggestionColor
        suggestionTextPaint.textSize = height * 0.4f
        suggestionTextPaint.textAlign = Paint.Align.CENTER

        for (i in suggestions.indices) {
            val x = itemWidth * i + itemWidth / 2
            val y = height / 2 + suggestionTextPaint.textSize / 3f
            canvas.drawText(suggestions[i], x, y, suggestionTextPaint)

            if (i < suggestions.size - 1) {
                dividerPaint.color = theme.borderColor
                canvas.drawLine(itemWidth * (i + 1), height * 0.2f, itemWidth * (i + 1), height * 0.8f, dividerPaint)
            }
        }
    }

    private fun drawKey(canvas: Canvas, key: KeyData, rect: RectF, isPressed: Boolean) {
        val isSpecial = key.type != KeyType.TEXT

        val bgColor = when {
            isPressed -> theme.keyPressedColor
            isSpecial -> theme.specialKeyColor
            key.type == KeyType.SPACE -> theme.spaceBarColor
            else -> theme.keyBackgroundColor
        }

        val inset = if (isPressed) 1.5.dp else 0.dp
        val drawRect = RectF(
            rect.left + inset, rect.top + inset,
            rect.right - inset, rect.bottom - inset
        )

        val drawable = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(bgColor)
            cornerRadius = keyRadius
        }
        drawable.setBounds(
            drawRect.left.toInt(), drawRect.top.toInt(),
            drawRect.right.toInt(), drawRect.bottom.toInt()
        )
        drawable.draw(canvas)

        val textColor = when {
            key.type != KeyType.TEXT -> theme.specialKeyTextColor
            else -> theme.keyTextColor
        }
        textPaint.color = textColor
        textPaint.textSize = keyHeight * 0.42f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = key.type == KeyType.TEXT

        val label = when (key.type) {
            KeyType.SHIFT -> "\u2B06"
            KeyType.BACKSPACE -> "\u232B"
            KeyType.ENTER -> "\u23CE"
            KeyType.SPACE -> ""
            KeyType.NUMBERS, KeyType.SYMBOLS -> "?123"
            KeyType.ABC -> "ABC"
            KeyType.COMMA -> ","
            KeyType.PERIOD -> "."
            else -> key.label
        }

        if (label.isNotEmpty()) {
            val textX = drawRect.centerX()
            val textY = drawRect.centerY() + textPaint.textSize / 3f
            canvas.drawText(label, textX, textY, textPaint)
        }

        if (key.type == KeyType.SPACE) {
            textPaint.textSize = keyHeight * 0.28f
            textPaint.color = theme.specialKeyTextColor
            textPaint.isFakeBoldText = false
            canvas.drawText("space", drawRect.centerX(), drawRect.centerY() + textPaint.textSize / 3f, textPaint)
        }

        if (key.secondary != null && key.type == KeyType.TEXT) {
            subTextPaint.color = (textColor and 0x00FFFFFF) or (0x80 shl 24)
            subTextPaint.textSize = keyHeight * 0.2f
            subTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(key.secondary, drawRect.right - 2.dp, drawRect.top + subTextPaint.textSize + 2.dp, subTextPaint)
        }

        if (isPressed && showPreview && key.type == KeyType.TEXT) {
            drawPreview(canvas, key, rect)
        }
    }

    private fun drawPreview(canvas: Canvas, key: KeyData, rect: RectF) {
        val previewSize = keyHeight * 1.4f
        val previewRect = RectF(
            rect.centerX() - previewSize / 2,
            rect.top - previewSize + 4.dp,
            rect.centerX() + previewSize / 2,
            rect.top - 4.dp
        )

        val drawable = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(theme.previewBackgroundColor)
            cornerRadius = 8.dp
        }
        drawable.setBounds(
            previewRect.left.toInt(), previewRect.top.toInt(),
            previewRect.right.toInt(), previewRect.bottom.toInt()
        )
        drawable.draw(canvas)

        previewTextPaint.color = theme.previewTextColor
        previewTextPaint.textSize = keyHeight * 0.55f
        previewTextPaint.textAlign = Paint.Align.CENTER
        previewTextPaint.isFakeBoldText = true
        canvas.drawText(
            key.primary,
            previewRect.centerX(),
            previewRect.centerY() + previewTextPaint.textSize / 3f,
            previewTextPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val hit = findKeyAt(x, y)
                if (hit != null) {
                    if (pressedRow != hit.first || pressedCol != hit.second) {
                        pressedRow = hit.first
                        pressedCol = hit.second
                        pressedKey = currentLayout[pressedRow][pressedCol]
                        invalidate()
                    }
                } else {
                    if (pressedKey != null) {
                        pressedKey = null
                        pressedRow = -1
                        pressedCol = -1
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (pressedKey != null) {
                    playFeedback()
                    service.onKeyPressed(pressedKey!!)
                }
                pressedKey = null
                pressedRow = -1
                pressedCol = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                pressedRow = -1
                pressedCol = -1
                invalidate()
            }
        }
        return true
    }

    private fun findKeyAt(x: Float, y: Float): Pair<Int, Int>? {
        for ((pair, rect) in keyRects) {
            if (rect.contains(x, y)) return pair
        }
        return null
    }

    private fun playFeedback() {
        if (vibrationEnabled) {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(15)
                }
            }
        }
        if (soundEnabled) {
            soundPool?.play(clickSoundId, 0.3f, 0.3f, 1, 0, 1f)
        }
    }
}

enum class KeyboardMode {
    LETTERS, NUMBERS, SYMBOLS
}
