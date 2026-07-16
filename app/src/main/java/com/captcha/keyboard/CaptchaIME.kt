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
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import java.io.File

class CaptchaIME : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private val icThread = android.os.HandlerThread("captcha-ic-thread").apply { start() }
    private val icHandler = Handler(icThread.looper)
    private var fileObserver: FileObserver? = null
    private val watchFile by lazy { File("/sdcard/Android/media/com.arizona.game/captcha_input.txt") }

    private var isNumMode = false
    private var isShift = false
    private var isRu = false
    private var isSettingsOpen = false
    private var isSettingsLoading = false
    private var backspaceRunnable: Runnable? = null
    private var isHapticEnabled = true
    private var hapticStrength = 1 // 0 = weak, 1 = medium, 2 = strong
    private var lastHapticAtMs = 0L

    private val vibrator: android.os.Vibrator? by lazy {
        getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    }

    // ---------- Layout tuning ----------
    private val KEY_HEIGHT get() = if (isLandscape()) 34.dp else 44.dp
    private val KEY_MARGIN_H get() = 2.dp
    private val ROW_MARGIN_V get() = if (isLandscape()) 2.dp else 4.dp
    private val CORNER_RADIUS get() = 6f.dpF

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
        listOf("123","RU",",","space",".","↵")
    )

    private val ruKeys = listOf(
        listOf("й","ц","у","к","е","н","г","ш","щ","з","х"),
        listOf("ф","ы","в","а","п","р","о","л","д","ж","э"),
        listOf("⇧","я","ч","с","м","и","т","ь","б","ю","⌫"),
        listOf("123","EN",",","space",".","↵")
    )

    private val numKeys = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","%","&","-","+","(",")","/"),
        listOf("*","\"","'",":",";","!","?","~","`"),
        listOf("<",">","{","}","[","]","|","\\","⌫"),
        listOf("ABC",",","space",".","↵")
    )

    // Keys that are "controls" rather than characters -> smaller/bolder label, dimmer bg
    private val controlKeys = setOf("⇧","⌫","123","ABC","EN","RU","↵","⚙")

    override fun onCreateInputView(): View {
        return try {
            startFileWatcher()
            val view = buildKeyboard()
            paintSystemWindowToMatchKeyboard()
            view
        } catch (e: Exception) {
            // Never let a transient failure here brick the keyboard until a reboot —
            // fall back to a minimal but functional layout instead.
            isSettingsOpen = false
            isSettingsLoading = false
            try { buildKeyboard() } catch (e2: Exception) {
                LinearLayout(this).apply { setBackgroundColor(COLOR_BG) }
            }
        }
    }

    /** Colors the IME window itself (not just our view) so the gesture-navigation
     *  strip below the keyboard matches our background instead of showing the
     *  system's default black — this is what makes Gboard look "seamless" there. */
    private fun paintSystemWindowToMatchKeyboard() {
        try {
            window?.window?.apply {
                setBackgroundDrawable(GradientDrawable().apply { setColor(COLOR_BG) })
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    navigationBarColor = COLOR_BG
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    var flags = decorView.systemUiVisibility
                    flags = flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    decorView.systemUiVisibility = flags
                }
            }
        } catch (e: Exception) { }
    }

    private fun buildKeyboard(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            val vPad = if (isLandscape()) 2.dp else 4.dp
            setPadding(4.dp, vPad, 4.dp, vPad)
        }

        if (isSettingsOpen) {
            root.addView(buildSettingsPanel())
            return root
        }

        root.addView(buildTopBar())

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
                addView(makeButton(key, isDigitRow, keys.size))
            }
        }
    }

    /** Respects the user's haptic on/off + strength settings. Uses Vibrator with an
     *  explicit amplitude when available (real strength control), falling back to
     *  the generic system click feedback on older devices. */
    private fun triggerHaptic(view: View) {
        if (!isHapticEnabled) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHapticAtMs < 30L) return // avoid flooding the Vibrator IPC on rapid taps
        lastHapticAtMs = now
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val amplitude = when (hapticStrength) { 0 -> 60; 2 -> 255; else -> 150 }
                val durationMs = when (hapticStrength) { 0 -> 25L; 2 -> 60L; else -> 40L }
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        } catch (e: Exception) { }
    }

    /** Fires on ACTION_DOWN instead of relying on click recognition (which waits out
     *  a short delay to distinguish a tap from a scroll/long-press). This is what
     *  makes key presses feel instant instead of "thinking" for a beat. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun instantTap(view: View, onTap: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    triggerHaptic(v)
                    handler.post { onTap() }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    /** Backspace needs its own touch handling: one immediate delete on press,
     *  then fast repeated deletes for as long as the key stays held down. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setBackspaceTouchHandling(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    triggerHaptic(v)
                    icHandler.post { currentInputConnection?.let { performBackspace(it) } }
                    startBackspaceRepeat()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopBackspaceRepeat()
                    true
                }
                else -> false
            }
        }
    }

    private fun startBackspaceRepeat() {
        stopBackspaceRepeat()
        val runnable = object : Runnable {
            override fun run() {
                icHandler.post { currentInputConnection?.let { performBackspace(it) } }
                handler.postDelayed(this, 50L)
            }
        }
        backspaceRunnable = runnable
        // Wait a beat before the fast-repeat kicks in, so a normal single tap
        // doesn't accidentally delete twice.
        handler.postDelayed(runnable, 350L)
    }

    private fun stopBackspaceRepeat() {
        backspaceRunnable?.let { handler.removeCallbacks(it) }
        backspaceRunnable = null
    }


    private fun makeButton(key: String, isDigitRow: Boolean = false, rowLen: Int = 10): Button {
        return Button(this).apply {
            val isControl = key in controlKeys

            text = when (key) {
                "space" -> ""
                "⇧" -> if (isShift) "⇪" else "⇧"
                else -> if (isShift && key.length == 1 && !isDigitRow && !isNumMode) key.uppercase() else key
            }

            // Unified text sizing, but scaled down for rows with more than 10 keys
            // (Russian rows have 11) so letters never get clipped on narrow buttons.
            val crampFactor = if (rowLen > 10) 10f / rowLen else 1f
            textSize = when {
                key == "space" -> 14f
                key == "↵" -> 15f
                key == "EN" || key == "RU" -> 11f
                isDigitRow -> 14f
                isControl -> 13f * crampFactor
                else -> 16f * crampFactor
            }
            typeface = if (isControl) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            setTextColor(if (isControl && key != "↵") COLOR_TEXT_DIM else COLOR_TEXT)
            background = keyDrawable(key)
            stateListAnimator = null
            isAllCaps = false
            includeFontPadding = false
            isSingleLine = true
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)

            val weight = when (key) {
                "space" -> 4.2f
                "⇧", "⌫" -> 1.35f
                "EN", "RU" -> 0.7f
                "ABC", "123" -> 1.5f
                "↵" -> 1.5f
                else -> 1f
            }

            val effectiveHeight = if (isDigitRow) KEY_HEIGHT - 4.dp else KEY_HEIGHT
            val effectiveMarginH = if (isDigitRow) KEY_MARGIN_H + 2.dp else KEY_MARGIN_H

            layoutParams = LinearLayout.LayoutParams(0, effectiveHeight, weight).apply {
                setMargins(effectiveMarginH, 0, effectiveMarginH, 0)
            }

            if (key == "⌫") {
                setBackspaceTouchHandling(this)
            } else {
                instantTap(this) { handleKey(key) }
            }
        }
    }

    /** Thin top row: gear icon on the left, rest empty. Tapping the gear plays a
     *  left-to-right loading bar, then opens the settings panel. */
    private fun buildTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, ROW_MARGIN_V) }

            val barHeight = if (isLandscape()) 22.dp else 26.dp

            val gear = Button(this@CaptchaIME).apply {
                text = "⚙"
                textSize = 14f
                setTextColor(COLOR_TEXT_DIM)
                background = keyDrawable("⚙")
                stateListAnimator = null
                isAllCaps = false
                includeFontPadding = false
                isSingleLine = true
                minWidth = 0; minimumWidth = 0
                minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(44.dp, barHeight).apply {
                    setMargins(KEY_MARGIN_H, 0, KEY_MARGIN_H, 0)
                }
                this@CaptchaIME.instantTap(this) {
                    if (!isSettingsLoading) openSettingsWithAnimation()
                }
            }
            addView(gear)

            // Track that shows the loading bar animating across, and stays
            // empty otherwise -> keeps row height constant either way.
            val track = android.widget.FrameLayout(this@CaptchaIME).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(0, barHeight, 1f)
            }
            addView(track)

            if (isSettingsLoading) {
                val fill = View(this@CaptchaIME).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = CORNER_RADIUS
                        setColor(COLOR_ACCENT)
                    }
                    layoutParams = android.widget.FrameLayout.LayoutParams(0, barHeight)
                }
                track.addView(fill)

                val animator = android.animation.ValueAnimator.ofInt(0, 1000).apply {
                    duration = 500L
                    addUpdateListener { anim ->
                        val fraction = anim.animatedFraction
                        val trackWidth = track.width
                        if (trackWidth > 0) {
                            fill.layoutParams = fill.layoutParams.apply {
                                width = (trackWidth * fraction).toInt()
                            }
                            fill.requestLayout()
                        }
                    }
                    doOnEndCompat {
                        isSettingsLoading = false
                        isSettingsOpen = true
                        refresh()
                    }
                }
                track.post { animator.start() }
            }
        }
    }

    private fun openSettingsWithAnimation() {
        isSettingsLoading = true
        refresh()
    }

    /** Placeholder settings screen: title + back button. Add real options here later. */
    private fun buildSettingsPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val header = LinearLayout(this@CaptchaIME).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4.dp, 0, 4.dp, 8.dp) }

                val back = Button(this@CaptchaIME).apply {
                    text = "←"
                    textSize = 16f
                    setTextColor(COLOR_TEXT)
                    background = keyDrawable("⚙")
                    stateListAnimator = null
                    isAllCaps = false
                    includeFontPadding = false
                    minWidth = 0; minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(40.dp, 36.dp)
                    this@CaptchaIME.instantTap(this) {
                        isSettingsOpen = false
                        refresh()
                    }
                }
                addView(back)

                val title = android.widget.TextView(this@CaptchaIME).apply {
                    text = "Настройки"
                    textSize = 15f
                    setTextColor(COLOR_TEXT)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(title)

                // spacer to visually balance the back button on the left
                addView(View(this@CaptchaIME).apply {
                    layoutParams = LinearLayout.LayoutParams(40.dp, 36.dp)
                })
            }
            addView(header)

            addView(buildHapticSection())
        }
    }

    private fun buildHapticSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4.dp, 0, 4.dp, 0) }

            // --- Toggle row: "Вибрация" label + on/off pill ---
            val toggleRow = LinearLayout(this@CaptchaIME).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4.dp, 0, 10.dp) }
            }

            val label = android.widget.TextView(this@CaptchaIME).apply {
                text = "Вибрация"
                textSize = 14f
                setTextColor(COLOR_TEXT)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            toggleRow.addView(label)

            lateinit var toggleBtn: Button
            toggleBtn = Button(this@CaptchaIME).apply {
                text = if (isHapticEnabled) "ВКЛ" else "ВЫКЛ"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isHapticEnabled) Color.WHITE else COLOR_TEXT_DIM)
                background = pillDrawable(if (isHapticEnabled) COLOR_ACCENT else COLOR_KEY_SPECIAL)
                stateListAnimator = null
                isAllCaps = false
                includeFontPadding = false
                minWidth = 0; minimumWidth = 0
                setPadding(12.dp, 0, 12.dp, 0)
                layoutParams = LinearLayout.LayoutParams(72.dp, 34.dp)
                this@CaptchaIME.instantTap(this) {
                    isHapticEnabled = !isHapticEnabled
                    refresh()
                }
            }
            toggleRow.addView(toggleBtn)
            addView(toggleRow)

            // --- Strength row: three selectable levels ---
            val strengthLabel = android.widget.TextView(this@CaptchaIME).apply {
                text = "Сила отклика"
                textSize = 14f
                setTextColor(if (isHapticEnabled) COLOR_TEXT else COLOR_TEXT_DIM)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 6.dp) }
            }
            addView(strengthLabel)

            val strengthRow = LinearLayout(this@CaptchaIME).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val levels = listOf(0 to "Слабая", 1 to "Средняя", 2 to "Сильная")
            for ((level, name) in levels) {
                val selected = hapticStrength == level
                val btn = Button(this@CaptchaIME).apply {
                    text = name
                    textSize = 12f
                    typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    isEnabled = isHapticEnabled
                    setTextColor(
                        when {
                            !isHapticEnabled -> COLOR_TEXT_DIM
                            selected -> Color.WHITE
                            else -> COLOR_TEXT_DIM
                        }
                    )
                    background = pillDrawable(if (selected && isHapticEnabled) COLOR_ACCENT else COLOR_KEY_SPECIAL)
                    stateListAnimator = null
                    isAllCaps = false
                    includeFontPadding = false
                    minWidth = 0; minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, 36.dp, 1f).apply {
                        setMargins(3.dp, 0, 3.dp, 0)
                    }
                    if (isHapticEnabled) {
                        this@CaptchaIME.instantTap(this) {
                            hapticStrength = level
                            triggerHaptic(this)
                            refresh()
                        }
                    }
                }
                strengthRow.addView(btn)
            }
            addView(strengthRow)
        }
    }

    /** Small rounded pill background, reused for settings toggle/selector buttons. */
    private fun pillDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = CORNER_RADIUS
            setColor(color)
        }
    }

    /** ValueAnimator.doOnEnd isn't available without the KTX extension; small local shim. */
    private fun android.animation.ValueAnimator.doOnEndCompat(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action()
            }
        })
    }


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

    /** Deletes the current selection in one go if there is one; otherwise falls
     *  back to deleting a single character before the cursor as usual. */
    private fun performBackspace(ic: android.view.inputmethod.InputConnection) {
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun handleKey(key: String) {
        // Mode/shift toggles touch the UI (rebuild the keyboard) -> must stay on main thread.
        when (key) {
            "⇧" -> { isShift = !isShift; refresh(); return }
            "123" -> { isNumMode = true; refresh(); return }
            "ABC" -> { isNumMode = false; refresh(); return }
            "RU" -> { isRu = true; refresh(); return }
            "EN" -> { isRu = false; refresh(); return }
        }

        // Everything that talks to the target app's InputConnection goes on its own
        // thread, so a slow/blocking round trip to the host app never stalls our
        // own touch handling (this is what caused rapid taps to "batch up").
        icHandler.post {
            val ic = currentInputConnection ?: return@post
            ic.beginBatchEdit()
            try {
                when (key) {
                    "⌫" -> performBackspace(ic)
                    "↵" -> handleEnter(ic)
                    "space" -> ic.commitText(" ", 1)
                    else -> {
                        val ch = if (isShift && key.length == 1) key.uppercase() else key
                        ic.commitText(ch, 1)
                        if (isShift) {
                            isShift = false
                            handler.post { refresh() }
                        }
                    }
                }
            } finally {
                ic.endBatchEdit()
            }
        }
    }

    /**
     * Enter behavior that respects what the focused field actually asked for,
     * instead of always firing a hardcoded "done" action:
     *  - if the field declared a real action (search, go, send, next...) -> perform that
     *  - else if the field is multiline (e.g. chat composers) -> insert a newline
     *  - else fall back to "done"
     */
    private fun handleEnter(ic: android.view.inputmethod.InputConnection) {
        val info = currentInputEditorInfo
        val imeOptions = info?.imeOptions ?: 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterFlag = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val isMultiline = ((info?.inputType ?: 0) and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        when {
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            !noEnterFlag -> ic.performEditorAction(action)

            isMultiline -> ic.commitText("\n", 1)

            else -> ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
    }

    private fun refresh() {
        try {
            setInputView(buildKeyboard())
            paintSystemWindowToMatchKeyboard()
        } catch (e: Exception) {
            isSettingsOpen = false
            isSettingsLoading = false
            try { setInputView(buildKeyboard()) } catch (e2: Exception) { }
        }
    }

    private fun startFileWatcher() {
        if (fileObserver != null) return // already watching -> avoid duplicate observers on repeated onCreateInputView calls
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refresh()
    }

    override fun onDestroy() {
        fileObserver?.stopWatching()
        stopBackspaceRepeat()
        icThread.quitSafely()
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private val Float.dpF: Float get() = this * resources.displayMetrics.density

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
}
