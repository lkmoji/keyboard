package com.captcha.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.os.FileObserver
import java.io.File

class CaptchaIME : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private var fileObserver: FileObserver? = null
    private val watchFile by lazy { File("/sdcard/Android/media/com.arizona.game/captcha_input.txt") }

    private var isNumMode = false
    private var isShift = false
    private var isRu = false

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
        listOf("⌫","!","?","#","*","_","=","€","£","⌫"),
        listOf("ABC","space",".","↵")
    )

    override fun onCreateInputView(): View {
        startFileWatcher()
        return buildKeyboard()
    }

    private fun buildKeyboard(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2C2C2E"))
            setPadding(4, 8, 4, 8)
        }

        if (!isNumMode) {
            root.addView(buildRow(numRow, showAsNum = true))
        }

        val rows = when {
            isNumMode -> numKeys
            isRu     -> ruKeys
            else     -> enKeys
        }

        for (row in rows) {
            root.addView(buildRow(row))
        }

        return root
    }

    private fun buildRow(keys: List<String>, showAsNum: Boolean = false): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 3, 0, 3) }

            for (key in keys) {
                addView(makeButton(key, showAsNum))
            }
        }
    }

    private fun makeButton(key: String, isNumRow: Boolean = false): Button {
        return Button(this).apply {
            val displayText = when (key) {
                "space" -> "     "
                "⇧"    -> if (isShift) "⇪" else "⇧"
                else   -> if (isShift && key.length == 1 && !isNumRow) key.uppercase() else key
            }
            text = displayText
            textSize = when (key) {
                "space" -> 12f
                "↵"    -> 14f
                else   -> if (isNumRow) 15f else 16f
            }
            setTextColor(Color.WHITE)
            setBackgroundColor(bgColor(key))
            stateListAnimator = null
            isAllCaps = false

            val weight = when (key) {
                "space" -> 4f
                "⇧", "⌫" -> 1.5f
                "ABC", "123", "EN", "RU" -> 1.5f
                "↵"    -> 1.5f
                else   -> 1f
            }

            layoutParams = LinearLayout.LayoutParams(0, 50.dp, weight).apply {
                setMargins(3, 0, 3, 0)
            }

            setOnClickListener {
                animatePress(this, key)
                handleKey(key)
            }
        }
    }

    private fun bgColor(key: String): Int = when (key) {
        "⌫", "⇧", "ABC", "123", "EN", "RU", "↵", "space" -> Color.parseColor("#3A3A3C")
        else -> Color.parseColor("#4A4A4C")
    }

    private fun animatePress(btn: Button, key: String) {
        btn.setBackgroundColor(Color.parseColor("#636366"))
        handler.postDelayed({ btn.setBackgroundColor(bgColor(key)) }, 80)
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return
        when (key) {
            "⌫"    -> ic.deleteSurroundingText(1, 0)
            "↵"    -> ic.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
            "space" -> ic.commitText(" ", 1)
            "⇧"    -> { isShift = !isShift; refresh() }
            "123"  -> { isNumMode = true;   refresh() }
            "ABC"  -> { isNumMode = false;  refresh() }
            "RU"   -> { isRu = true;        refresh() }
            "EN"   -> { isRu = false;       refresh() }
            else   -> {
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
}
