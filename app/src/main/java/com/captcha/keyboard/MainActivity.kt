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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.view.inputmethod.InputMethodManager

class MainActivity : Activity() {

    private val COLOR_BG      = Color.parseColor("#202023")
    private val COLOR_KEY     = Color.parseColor("#38383B")
    private val COLOR_KEY2    = Color.parseColor("#2C2C2F")
    private val COLOR_ACCENT  = Color.parseColor("#0A84FF")
    private val COLOR_TEXT    = Color.parseColor("#F2F2F2")
    private val COLOR_TEXT_DIM = Color.parseColor("#B8B8BD")

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpF(): Float = this * resources.displayMetrics.density

    // ---- SharedPreferences shortcut (тот же файл, что читает CaptchaIME) ----
    private fun lp() = getSharedPreferences(CaptchaIME.PREFS_LAYOUT, MODE_PRIVATE)
    private fun lpInt(key: String, def: Int) = lp().getInt(key, def)
    private fun lpSave(key: String, value: Int) = lp().edit().putInt(key, value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        setContentView(buildScreen())
    }

    // ==========================================================================
    //  Главный экран
    // ==========================================================================
    private fun buildScreen(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            setPadding(20.dp, 24.dp, 20.dp, 24.dp)

            val scroll = android.widget.ScrollView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            scroll.addView(inner)

            inner.apply {
                // Заголовок
                addView(sectionTitle("Captcha Keyboard"))
                addView(subLabel("Настройте клавиатуру в два шага"))
                addView(spacer(20))

                // Статус
                addView(statusCard())

                // Кнопки активации
                addView(actionButton("1. Включить клавиатуру", isPrimary = true) {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                })
                addView(actionButton("2. Выбрать активную клавиатуру", isPrimary = false) {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                })

                addView(spacer(24))

                // ---- Настройки размеров ----
                addView(sectionTitle("Размеры клавиш"))
                addView(spacer(12))

                // Портрет
                addView(groupCard("📱 Портрет (вертикальное положение)").also { card ->
                    card.addView(seekRow(
                        label    = "Высота клавиш",
                        hint     = "Как высоко каждая кнопка букв/символов",
                        key      = CaptchaIME.P_KEY_H_PORT,
                        default  = CaptchaIME.DEF_KEY_H_PORT,
                        rangeMin = 28, rangeMax = 64
                    ))
                    card.addView(divider())
                    card.addView(seekRow(
                        label    = "Высота ряда цифр",
                        hint     = "Ряд 1–0 сверху клавиатуры",
                        key      = CaptchaIME.P_DIG_H_PORT,
                        default  = CaptchaIME.DEF_DIG_H_PORT,
                        rangeMin = 20, rangeMax = 56
                    ))
                })

                addView(spacer(12))

                // Ландшафт
                addView(groupCard("🖥 Ландшафт (горизонтальное положение)").also { card ->
                    card.addView(seekRow(
                        label    = "Высота клавиш",
                        hint     = "Как высоко каждая кнопка букв/символов",
                        key      = CaptchaIME.P_KEY_H_LAND,
                        default  = CaptchaIME.DEF_KEY_H_LAND,
                        rangeMin = 20, rangeMax = 52
                    ))
                    card.addView(divider())
                    card.addView(seekRow(
                        label    = "Высота ряда цифр",
                        hint     = "Ряд 1–0 сверху клавиатуры",
                        key      = CaptchaIME.P_DIG_H_LAND,
                        default  = CaptchaIME.DEF_DIG_H_LAND,
                        rangeMin = 16, rangeMax = 44
                    ))
                })

                addView(spacer(12))

                // Отступы
                addView(groupCard("↔ Отступы").also { card ->
                    card.addView(seekRow(
                        label    = "Между клавишами (горизонт.)",
                        hint     = "Промежуток слева и справа от каждой кнопки",
                        key      = CaptchaIME.P_MARGIN_H,
                        default  = CaptchaIME.DEF_MARGIN_H,
                        rangeMin = 0, rangeMax = 8
                    ))
                    card.addView(divider())
                    card.addView(seekRow(
                        label    = "Между рядами (вертикаль.)",
                        hint     = "Промежуток сверху и снизу каждого ряда",
                        key      = CaptchaIME.P_MARGIN_V,
                        default  = CaptchaIME.DEF_MARGIN_V,
                        rangeMin = 0, rangeMax = 12
                    ))
                })

                addView(spacer(12))

                // Сброс
                addView(actionButton("Сбросить размеры по умолчанию", isPrimary = false) {
                    lp().edit()
                        .putInt(CaptchaIME.P_KEY_H_PORT,  CaptchaIME.DEF_KEY_H_PORT)
                        .putInt(CaptchaIME.P_KEY_H_LAND,  CaptchaIME.DEF_KEY_H_LAND)
                        .putInt(CaptchaIME.P_DIG_H_PORT,  CaptchaIME.DEF_DIG_H_PORT)
                        .putInt(CaptchaIME.P_DIG_H_LAND,  CaptchaIME.DEF_DIG_H_LAND)
                        .putInt(CaptchaIME.P_MARGIN_H,    CaptchaIME.DEF_MARGIN_H)
                        .putInt(CaptchaIME.P_MARGIN_V,    CaptchaIME.DEF_MARGIN_V)
                        .apply()
                    // перерисовать экран чтобы ползунки встали на дефолт
                    setContentView(buildScreen())
                })

                addView(spacer(24))

                // Телеграм
                addView(actionButton("Наш канал в Telegram", isPrimary = false) {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/lkmojii")))
                })

                addView(spacer(16))
            }

            addView(scroll)
        }
    }

    // ==========================================================================
    //  Компоненты
    // ==========================================================================

    /** Карточка-контейнер с заголовком группы настроек */
    private fun groupCard(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 12f.dpF()
                setColor(COLOR_KEY)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_TEXT_DIM)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10.dp }
            })
        }
    }

    /**
     * Строка с ползунком:
     *   label — название параметра
     *   hint  — короткое пояснение что это такое
     *   key   — ключ в SharedPreferences
     *   min/max — диапазон в dp
     */
    private fun seekRow(
        label: String,
        hint: String,
        key: String,
        default: Int,
        rangeMin: Int,
        rangeMax: Int
    ): LinearLayout {
        val currentVal = lpInt(key, default)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp; bottomMargin = 4.dp }

            // Строка: название  +  текущее значение
            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val labelView = TextView(this@MainActivity).apply {
                text = label
                textSize = 14f
                setTextColor(COLOR_TEXT)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val valueView = TextView(this@MainActivity).apply {
                text = "${currentVal}dp"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_ACCENT)
                gravity = Gravity.END
                minWidth = 44.dp
            }

            topRow.addView(labelView)
            topRow.addView(valueView)
            addView(topRow)

            // Пояснение
            addView(TextView(this@MainActivity).apply {
                text = hint
                textSize = 11f
                setTextColor(COLOR_TEXT_DIM)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.dp; bottomMargin = 6.dp }
            })

            // Ползунок
            addView(SeekBar(this@MainActivity).apply {
                max = rangeMax - rangeMin
                progress = (currentVal - rangeMin).coerceIn(0, rangeMax - rangeMin)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                        val newVal = progress + rangeMin
                        valueView.text = "${newVal}dp"
                        lpSave(key, newVal)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            })
        }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#3A3A3D"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { topMargin = 10.dp; bottomMargin = 10.dp }
    }

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
            ).apply { bottomMargin = 16.dp }
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

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(COLOR_TEXT)
        gravity = Gravity.START
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 4.dp }
    }

    private fun subLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(COLOR_TEXT_DIM)
    }

    private fun spacer(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h.dp)
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
            ).apply { bottomMargin = 10.dp }
            setOnClickListener { onTap() }
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        return try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.any { it.packageName == packageName }
        } catch (e: Exception) { false }
    }

    private fun isKeyboardSelected(): Boolean {
        return try {
            val id = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
            id.contains(packageName)
        } catch (e: Exception) { false }
    }
}
