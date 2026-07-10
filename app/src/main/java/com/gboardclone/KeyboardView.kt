package com.gboardclone

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

class KeyboardView(context: Context, private val service: KeyboardService) : View(context) {

    private var currentLayout: List<List<KeyData>> = KeyboardLayouts.QWERTY_LOWER
    private var isShifted = false
    private var isCapsLock = false
    private var currentMode = KeyboardMode.LETTERS

    private var theme: KeyboardTheme = KeyboardTheme.DARK
    private var keyWidth = 0f
    private var keyHeight = 0f
    private var keyMargin = 2.dp
    private var keyRadius = 6.dp

    private val keyRect = RectF()
    private val previewRect = RectF()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 0.5.dp
    }

    fun setTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        invalidate()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    fun setPreviewEnabled(enabled: Boolean) {
        showPreview = enabled
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        keyRects.clear()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val totalRows = currentLayout.size
        val height = (keyHeight * totalRows).toInt() + (keyMargin * (totalRows + 1)).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        backgroundPaint.color = theme.backgroundColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val totalRows = currentLayout.size
        val availableHeight = height.toFloat() - keyMargin * (totalRows + 1)
        keyHeight = availableHeight / totalRows

        val paddingHorizontal = keyMargin

        for (row in currentLayout.indices) {
            val rowData = currentLayout[row]
            val totalWidth = width.toFloat() - paddingHorizontal * 2
            val totalRatio = rowData.sumOf { it.widthRatio.toDouble() }.toFloat()
            keyWidth = totalWidth / totalRatio

            var xOffset = paddingHorizontal
            val yOffset = keyMargin + row * (keyHeight + keyMargin)

            for (col in rowData.indices) {
                val key = rowData[col]
                val keyW = key.widthRatio * keyWidth

                keyRect.set(xOffset, yOffset, xOffset + keyW, yOffset + keyHeight)
                keyRects[Pair(row, col)] = RectF(keyRect)

                val isPressed = pressedRow == row && pressedCol == col
                drawKey(canvas, key, keyRect, isPressed)

                xOffset += keyW + keyMargin
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

        val drawable = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(bgColor)
            cornerRadius = keyRadius
        }
        drawable.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        drawable.draw(canvas)

        borderPaint.color = theme.borderColor
        canvas.drawRoundRect(rect, keyRadius, keyRadius, borderPaint)

        val textColor = when {
            isSpecial -> theme.specialKeyTextColor
            else -> theme.keyTextColor
        }
        textPaint.color = textColor
        textPaint.textSize = when (key.type) {
            KeyType.SPACE -> keyHeight * 0.3f
            KeyType.ENTER -> keyHeight * 0.35f
            KeyType.BACKSPACE -> keyHeight * 0.4f
            KeyType.SHIFT -> keyHeight * 0.4f
            else -> keyHeight * 0.45f
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = theme.typeface

        val label = when (key.type) {
            KeyType.SHIFT -> if (isCapsLock) "\u21E8" else if (isShifted) "\u21E7" else "\u21E9"
            KeyType.BACKSPACE -> "\u232B"
            KeyType.ENTER -> "\u23CE"
            KeyType.SPACE -> "space"
            KeyType.NUMBERS, KeyType.SYMBOLS -> "?123"
            KeyType.ABC -> "ABC"
            KeyType.COMMA -> ","
            KeyType.PERIOD -> "."
            else -> key.label
        }

        val textX = rect.centerX()
        val textY = rect.centerY() + textPaint.textSize / 3f
        canvas.drawText(label, textX, textY, textPaint)

        if (isPressed && showPreview && key.type == KeyType.TEXT) {
            drawPreview(canvas, key, rect)
        }
    }

    private fun drawPreview(canvas: Canvas, key: KeyData, rect: RectF) {
        val previewSize = keyHeight * 1.5f
        previewRect.set(
            rect.centerX() - previewSize / 2,
            rect.top - previewSize + keyMargin,
            rect.centerX() + previewSize / 2,
            rect.top - keyMargin
        )

        val drawable = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(theme.previewBackgroundColor)
            cornerRadius = keyRadius
        }
        drawable.setBounds(
            previewRect.left.toInt(), previewRect.top.toInt(),
            previewRect.right.toInt(), previewRect.bottom.toInt()
        )
        drawable.draw(canvas)

        previewTextPaint.color = theme.previewTextColor
        previewTextPaint.textSize = keyHeight * 0.6f
        previewTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(key.primary, previewRect.centerX(), previewRect.centerY() + previewTextPaint.textSize / 3f, previewTextPaint)
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
            if (rect.contains(x, y)) {
                return pair
            }
        }
        return null
    }

    private fun playFeedback() {
        if (vibrationEnabled) {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(20)
                }
            }
        }
        if (soundEnabled) {
            soundPool?.play(clickSoundId, 0.5f, 0.5f, 1, 0, 1f)
        }
    }
}

enum class KeyboardMode {
    LETTERS, NUMBERS, SYMBOLS
}
