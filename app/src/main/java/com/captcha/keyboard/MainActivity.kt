package com.captcha.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val COLOR_BG = Color.parseColor("#202023")
    private val COLOR_KEY = Color.parseColor("#38383B")
    private val COLOR_ACCENT = Color.parseColor("#0A84FF")
    private val COLOR_TEXT = Color.parseColor("#F2F2F2")
    private val COLOR_TEXT_DIM = Color.parseColor("#B8B8BD")

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    private fun buildScreen(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            gravity = Gravity.CENTER
            setPadding(32.dp, 32.dp, 32.dp, 32.dp)

            addView(TextView(this@MainActivity).apply {
                text = "Captcha Keyboard"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_TEXT)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dp }
            })

            addView(TextView(this@MainActivity).apply {
                text = "Настройте клавиатуру в два шага"
                textSize = 14f
                setTextColor(COLOR_TEXT_DIM)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 32.dp }
            })

            addView(statusCard())

            addView(actionButton("1. Включить клавиатуру", isPrimary = true) {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            })

            addView(actionButton("2. Выбрать активную клавиатуру", isPrimary = false) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            })

            addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 16.dp)
            })

            addView(actionButton("Наш канал в Telegram", isPrimary = false) {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/lkmojii")))
            })
        }
    }

    /** Shows whether the keyboard is enabled and whether it's the currently
     *  selected default, so the user doesn't have to dig into settings just to check. */
    private fun statusCard(): LinearLayout {
        val isEnabled = isKeyboardEnabled()
        val isSelected = isKeyboardSelected()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 12f.dpF()
                setColor(COLOR_KEY)
            }
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20.dp }

            addView(statusRow("Клавиатура включена", isEnabled))
            addView(statusRow("Выбрана как активная", isSelected))
        }
    }

    private fun statusRow(label: String, ok: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp; bottomMargin = 4.dp }

            addView(TextView(this@MainActivity).apply {
                text = if (ok) "✓" else "✗"
                setTextColor(if (ok) Color.parseColor("#30D158") else Color.parseColor("#FF453A"))
                typeface = Typeface.DEFAULT_BOLD
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(24.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(COLOR_TEXT)
                textSize = 14f
            })
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val enabledIds = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
        return enabledIds.contains(packageName)
    }

    private fun isKeyboardSelected(): Boolean {
        val currentId = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        return currentId.contains(packageName)
    }

    override fun onResume() {
        super.onResume()
        // Re-check status every time the user comes back from Settings.
        setContentView(buildScreen())
    }

    private fun actionButton(label: String, isPrimary: Boolean, onTap: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            typeface = if (isPrimary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (isPrimary) Color.WHITE else COLOR_TEXT)
            isAllCaps = false
            stateListAnimator = null
            background = GradientDrawable().apply {
                cornerRadius = 10f.dpF()
                setColor(if (isPrimary) COLOR_ACCENT else COLOR_KEY)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 52.dp
            ).apply { bottomMargin = 12.dp }
            setOnClickListener { onTap() }
        }
    }

    private fun Float.dpF(): Float = this * resources.displayMetrics.density
}
