package com.captcha.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.GridLayout
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.os.FileObserver
import java.io.File

class CaptchaIME : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private var fileObserver: FileObserver? = null
    private val watchFile = File("/sdcard/captcha_input.txt")

    private val keys = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("⇧","z","x","c","v","b","n","m","⌫"),
        listOf("123","space",".")
    )

    private val numKeys = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("-","/",":",";","(",")","$","&","@","\""),
        listOf("ABC",",","?","!","'","⌫"),
        listOf("ABC","space",".")
    )

    private var isNumMode = false
    private var isShift = false
    private lateinit var rootLayout: LinearLayout

    override fun onCreateInputView(): View {
        rootLayout = buildKeyboard()
        startFileWatcher()
        return rootLayout
    }

    private fun buildKeyboard(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2C2C2E"))
            setPadding(4, 4, 4, 4)
        }

        val rows = if (isNumMode) numKeys else keys

        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 3, 0, 3) }
            }

            for (key in row) {
                val btn = Button(this).apply {
                    text = when (key) {
                        "space" -> " "
                        else -> if (isShift && key.length == 1) key.uppercase() else key
                    }
                    textSize = if (key == "space") 14f else 16f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(keyColor(key))
                    stateListAnimator = null

                    val w = when (key) {
                        "space" -> 0
                        "⌫"    -> 80
                        "⇧"    -> 80
                        "ABC"  -> 80
                        "123"  -> 80
                        else   -> 0
                    }
                    val weight = when (key) {
                        "space" -> 3f
                        else    -> 1f
                    }

                    layoutParams = LinearLayout.LayoutParams(w, 52.dp, weight).apply {
                        setMargins(3, 0, 3, 0)
                    }

                    setOnClickListener {
                        animatePress(this)
                        handleKey(key)
                    }
                }
                rowLayout.addView(btn)
            }
            root.addView(rowLayout)
        }

        return root
    }

    private fun keyColor(key: String): Int = when (key) {
        "⌫", "⇧", "ABC", "123", "space" -> Color.parseColor("#3A3A3C")
        else -> Color.parseColor("#4A4A4C")
    }

    private fun animatePress(btn: Button) {
        btn.setBackgroundColor(Color.parseColor("#636366"))
        handler.postDelayed({
            btn.setBackgroundColor(keyColor(btn.text.toString().lowercase().trim().let {
                when {
                    it == " " -> "space"
                    else -> it
                }
            }))
        }, 80)
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return
        when (key) {
            "⌫"    -> ic.deleteSurroundingText(1, 0)
            "space" -> ic.commitText(" ", 1)
            "⇧"    -> { isShift = !isShift; rootLayout.removeAllViews(); rebuildInto(rootLayout) }
            "123"  -> { isNumMode = true;   rootLayout.removeAllViews(); rebuildInto(rootLayout) }
            "ABC"  -> { isNumMode = false;  rootLayout.removeAllViews(); rebuildInto(rootLayout) }
            else   -> {
                val ch = if (isShift && key.length == 1) key.uppercase() else key
                ic.commitText(ch, 1)
                if (isShift) { isShift = false; rootLayout.removeAllViews(); rebuildInto(rootLayout) }
            }
        }
    }

    private fun rebuildInto(root: LinearLayout) {
        val rows = if (isNumMode) numKeys else keys
        val newKb = buildKeyboard()
        for (i in 0 until newKb.childCount) {
            root.addView(newKb.getChildAt(0))
        }
    }

    // Автоввод из файла
    private fun startFileWatcher() {
        watchFile.parentFile?.mkdirs()
        fileObserver = object : FileObserver(watchFile.absolutePath, CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                val text = watchFile.readText().trim()
                if (text.isEmpty()) return
                watchFile.writeText("")
                handler.post { typeText(text) }
            }
        }
        fileObserver?.startWatching()
    }

    private fun typeText(text: String) {
        val ic = currentInputConnection ?: return
        var delay = 0L
        for (ch in text) {
            handler.postDelayed({
                ic.commitText(ch.toString(), 1)
            }, delay)
            delay += 100L
        }
    }

    override fun onDestroy() {
        fileObserver?.stopWatching()
        super.onDestroy()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
