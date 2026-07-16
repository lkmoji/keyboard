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

            addView(actionButton("1. Включить клавиатуру", isPrimary = true) {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            })

            addView(actionButton("2. Выбрать активную клавиатуру", isPrimary = false) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            })
        }
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
