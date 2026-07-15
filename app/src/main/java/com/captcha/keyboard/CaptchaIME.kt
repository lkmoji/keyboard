package com.captcha.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import java.io.File

class CaptchaIME : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private var fileObserver: FileObserver? = null
    private val watchFile by lazy { File("/sdcard/Android/media/com.arizona.game/captcha_input.txt") }

    private var isNumMode = false
    private var isShift = false
    private var isRu = false

    // ---------- Layout tuning ----------
    private val KEY_HEIGHT get() = 40.dp
    private val KEY_MARGIN_H get() = 2.dp
    private val ROW_MARGIN_V get() = 3.dp
    private val CORNER_RADIUS get() = 6.dpF

    // ---------- Palette (dark, iOS/Gboard-inspired) ----------
    private val COLOR_BG = Color.parseColor("#202023")
    private val COLOR_KEY = Color.parseColor("#38383B")
    private val COLOR_KEY_PRESSED = Color.parseColor("#4E4E52")
    private val COLOR_KEY_SPECIAL = Color.parseColor("#28282B")
    private val COLOR_KEY_SPECIAL_PRESSED = Color.parseColor("#3A3A3D")
    private val COLOR_ACCENT = Color.parseColor("#0A84FF")
    private val COLOR_ACCENT_PRESSED = Color.parseColor("#3D9CFF")
    private val COLOR_TEXT = Color.parseColor("#F2F2F2")
    private val COLOR_TEXT_DIM = Color.parseColor("#B8B8BD")

    private val numRow = listOf("1","2","3","4","5","6","7","8","9","0")

    private val enKeys = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("⇧","z","x","c","v","b","n","m","⌫"),
        listOf("123","RU","space",".","↵")
    )

    private val ruKeys = listOf(
        listOf("й","ц","у","к","е","н","г","ш","щ","з","х"),
        listOf("ф","ы","в","а","п","р","о","л","д","ж","э"),
        listOf("⇧","я","ч","с","м","и","т","ь","б","ю","⌫"),
        listOf("123","EN","space",".","↵")
    )

    private val numKeys = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("-","/",":",";","(",")","%","&","@","\""),
        listOf("#","!","?","*","_","=","€","£","⌫"),
        listOf("ABC","space",".","↵")
    )

    // Keys that are "controls" rather than characters -> smaller/bolder label, dimmer bg
    private val controlKeys = setOf("⇧","⌫","123","ABC","EN","RU","↵")

    override fun onCreateInputView(): View {
        startFileWatcher()
        return buildKeyboard()
    }

    private fun buildKeyboard(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            setPadding(4.dp, 6.dp, 4.dp, 6.dp)
        }

        if (!isNumMode) {
            root.addView(buildRow(numRow, isDigitRow = true))
        }

        val rows = when {
            isNumMode -> numKeys
            isRu -> ruKeys
            else -> enKeys
        }

        for (row in rows) {
            root.addView(buildRow(row))
        }

        return root
    }

    private fun buildRow(keys: List<String>, isDigitRow: Boolean = false): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, ROW_MARGIN_V, 0, ROW_MARGIN_V) }

            for (key in keys) {
                addView(makeButton(key, isDigitRow))
            }
        }
    }

    private fun makeButton(key: String, isDigitRow: Boolean = false): Button {
        return Button(this).apply {
            val isControl = key in controlKeys

            text = when (key) {
                "space" -> ""
                "⇧" -> if (isShift) "⇪" else "⇧"
                else -> if (isShift && key.length == 1 && !isDigitRow && !isNumMode) key.uppercase() else key
            }

            // Unified, small set of text sizes so the row reads as one consistent grid
            textSize = when {
                key == "space" -> 14f
                key == "↵" -> 15f
                isDigitRow -> 14f
                isControl -> 13f
                else -> 16f
            }
            typeface = if (isControl) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            setTextColor(if (isControl && key != "↵") COLOR_TEXT_DIM else COLOR_TEXT)
            background = keyDrawable(key)
            stateListAnimator = null
            isAllCaps = false
            includeFontPadding = false

            val weight = when (key) {
                "space" -> 4.2f
                "⇧", "⌫" -> 1.35f
                "ABC", "123", "EN", "RU" -> 1.5f
                "↵" -> 1.5f
                else -> 1f
            }

            layoutParams = LinearLayout.LayoutParams(0, KEY_HEIGHT, weight).apply {
                setMargins(KEY_MARGIN_H, 0, KEY_MARGIN_H, 0)
            }

            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                handleKey(key)
            }
        }
    }

    /** Rounded key background with a built-in (native) pressed state — no manual timers needed. */
    private fun keyDrawable(key: String): StateListDrawable {
        val (normal, pressed) = when (key) {
            "↵" -> COLOR_ACCENT to COLOR_ACCENT_PRESSED
            in controlKeys -> COLOR_KEY_SPECIAL to COLOR_KEY_SPECIAL_PRESSED
            else -> COLOR_KEY to COLOR_KEY_PRESSED
        }
        fun pill(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = CORNER_RADIUS
            setColor(color)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pill(pressed))
            addState(intArrayOf(), pill(normal))
        }
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return
        when (key) {
            "⌫" -> ic.deleteSurroundingText(1, 0)
            "↵" -> ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
            "space" -> ic.commitText(" ", 1)
            "⇧" -> { isShift = !isShift; refresh() }
            "123" -> { isNumMode = true; refresh() }
            "ABC" -> { isNumMode = false; refresh() }
            "RU" -> { isRu = true; refresh() }
            "EN" -> { isRu = false; refresh() }
            else -> {
                val ch = if (isShift && key.length == 1) key.uppercase() else key
                ic.commitText(ch, 1)
                if (isShift) { isShift = false; refresh() }
            }
        }
    }

    private fun refresh() {
        setInputView(buildKeyboard())
    }

    private fun startFileWatcher() {
        try {
            watchFile.parentFile?.mkdirs()
            if (!watchFile.exists()) watchFile.createNewFile()
        } catch (e: Exception) { }
        fileObserver = object : FileObserver(watchFile.absolutePath, CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                val text = try { watchFile.readText().trim() } catch (e: Exception) { return }
                if (text.isEmpty()) return
                try { watchFile.writeText("") } catch (e: Exception) {}
                handler.post { typeText(text) }
            }
        }
        fileObserver?.startWatching()
    }

    private fun typeText(text: String) {
        val ic = currentInputConnection ?: return
        var delay = 0L
        for (ch in text) {
            handler.postDelayed({ ic.commitText(ch.toString(), 1) }, delay)
            delay += 100L
        }
    }

    override fun onDestroy() {
        fileObserver?.stopWatching()
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private val Float.dpF: Float get() = this * resources.displayMetrics.density
}
