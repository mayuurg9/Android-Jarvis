package com.mayuur.jarvis

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var secureStore: SecureKeyStore
    private lateinit var transcript: LinearLayout
    private lateinit var transcriptScroll: ScrollView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var speech: TextToSpeech
    private lateinit var agent: OpenRouterAgent
    private lateinit var core: CoreView
    private val history = JSONArray()
    private var model = "openai/gpt-4o-mini"
    private var busy = false
    private val prefs by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }

    private val bg = 0xFF050A10.toInt()
    private val panel = 0xFF0B1722.toInt()
    private val panel2 = 0xFF102231.toInt()
    private val cyan = 0xFF69DDF7.toInt()
    private val muted = 0xFF7793A5.toInt()
    private val white = 0xFFEAF7FC.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        secureStore = SecureKeyStore(this)
        agent = OpenRouterAgent(AgentTools(this))
        model = prefs.getString("model", model) ?: model
        speech = TextToSpeech(this, this)
        buildInterface()
        history.put(JSONObject().put("role", "system").put("content",
            "You are JARVIS, a careful Android personal assistant. Use available tools when useful. Never claim an action succeeded unless the tool reports success. Ask before actions not represented by an available tool. Be clear and concise."))
        setStatus(if (secureStore.read().isNullOrBlank()) "CONFIGURATION REQUIRED" else "SYSTEM STANDBY")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandStack.addView(text("J.A.R.V.I.S", 23f, white, true).apply { letterSpacing = 0.18f })
        brandStack.addView(text("PERSONAL INTELLIGENCE  /  MK-I", 9f, muted).apply {
            letterSpacing = 0.14f
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(brandStack, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("SETUP", false) { showSettings() })
        root.addView(header)

        val divider = View(this).apply { setBackgroundColor(0xFF183342.toInt()) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(12); bottomMargin = dp(8)
        })

        val systemLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        systemLine.addView(View(this).apply {
            background = rounded(cyan, 8)
        }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { rightMargin = dp(8) })
        status = text("INITIALISING", 10f, cyan, true).apply { letterSpacing = 0.12f }
        systemLine.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        systemLine.addView(text("ANDROID INTERFACE", 9f, muted).apply { letterSpacing = 0.08f })
        root.addView(systemLine, LinearLayout.LayoutParams(-1, dp(28)))

        val contentScroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(10))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(0xFF08131D.toInt(), 22, 0xFF17384A.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroTop.addView(text("NEURAL CORE", 9f, muted, true).apply { letterSpacing = 0.18f },
            LinearLayout.LayoutParams(0, -2, 1f))
        heroTop.addView(text("●  LOCAL UI", 9f, 0xFF61CFAE.toInt(), true))
        hero.addView(heroTop, LinearLayout.LayoutParams(-1, dp(22)))
        core = CoreView(this)
        hero.addView(core, LinearLayout.LayoutParams(-1, dp(178)))
        hero.addView(text("HOW CAN I ASSIST YOU TODAY?", 10f, 0xFFA5C7D7.toInt(), true).apply {
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(5))
        })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8); bottomMargin = dp(14)
        })

        val sectionHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sectionHead.addView(text("ACTIVITY LOG", 10f, white, true).apply { letterSpacing = 0.16f },
            LinearLayout.LayoutParams(0, -2, 1f))
        sectionHead.addView(text("SESSION  /  LIVE", 9f, muted).apply { letterSpacing = 0.08f })
        content.addView(sectionHead, LinearLayout.LayoutParams(-1, dp(26)))

        val logPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 16, 0xFF183342.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        transcriptScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        transcript = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        transcript.addView(messageView("JARVIS", "Interface initialised. Connect your OpenRouter key in SETUP to activate the assistant.", false))
        transcriptScroll.addView(transcript)
        logPanel.addView(transcriptScroll, LinearLayout.LayoutParams(-1, dp(178)))
        content.addView(logPanel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val capabilities = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        capabilities.addView(infoTile("VOICE", "PUSH TO TALK"), LinearLayout.LayoutParams(0, dp(58), 1f))
        capabilities.addView(space(8), LinearLayout.LayoutParams(dp(8), 1))
        capabilities.addView(infoTile("TOOLS", "ON DEVICE"), LinearLayout.LayoutParams(0, dp(58), 1f))
        capabilities.addView(space(8), LinearLayout.LayoutParams(dp(8), 1))
        capabilities.addView(infoTile("MEMORY", "SESSION"), LinearLayout.LayoutParams(0, dp(58), 1f))
        content.addView(capabilities, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        contentScroll.addView(content)
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF0C1B27.toInt(), 18, 0xFF21485C.toInt())
            setPadding(dp(8), dp(6), dp(6), dp(6))
        }
        input = EditText(this).apply {
            hint = "Message JARVIS..."
            setHintTextColor(0xFF678293.toInt())
            setTextColor(white)
            textSize = 14f
            setSingleLine(false)
            maxLines = 3
            background = null
            setPadding(dp(10), dp(8), dp(6), dp(8))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(actionButton("MIC", false) { startVoiceInput() })
        composer.addView(space(6), LinearLayout.LayoutParams(dp(6), 1))
        composer.addView(actionButton("SEND", true) { sendMessage() })
        root.addView(composer, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
        })
        root.addView(text("JARVIS  •  BUILT FOR YOUR DEVICE", 8f, 0xFF496577.toInt()).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
            setPadding(0, dp(8), 0, 0)
        })
        setContentView(root)
    }

    private fun space(width: Int) = View(this)

    private fun infoTile(title: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = rounded(0xFF0A1721.toInt(), 12, 0xFF173342.toInt())
        addView(text(title, 10f, cyan, true).apply { letterSpacing = 0.1f })
        addView(text(detail, 8f, muted).apply { setPadding(0, dp(3), 0, 0) })
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 9f
        letterSpacing = 0.08f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) 0xFF041018.toInt() else cyan)
        background = rounded(if (primary) cyan else 0xFF102635.toInt(), 12,
            if (primary) null else 0xFF285064.toInt())
        minWidth = dp(if (label == "SETUP") 72 else 54)
        minimumWidth = minWidth
        minHeight = dp(42)
        setPadding(dp(10), 0, dp(10), 0)
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun messageView(who: String, body: String, outgoing: Boolean): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (outgoing) Gravity.END else Gravity.START
            setPadding(0, dp(4), 0, dp(7))
        }
        wrapper.addView(text(who, 9f, if (outgoing) 0xFF9AB5C4.toInt() else cyan, true).apply {
            letterSpacing = 0.13f
            setPadding(dp(4), 0, dp(4), dp(4))
        })
        wrapper.addView(text(body, 13f, 0xFFD8E7EE.toInt()).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(if (outgoing) 0xFF123044.toInt() else 0xFF101F2B.toInt(), 12,
                if (outgoing) 0xFF24516A.toInt() else 0xFF1A3545.toInt())
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        return wrapper
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(4))
        }
        val keyInput = EditText(this).apply {
            hint = "OpenRouter API key"
            inputType = 129
            setText(secureStore.read().orEmpty())
        }
        val modelInput = EditText(this).apply {
            hint = "Model ID, e.g. openai/gpt-4o-mini"
            setText(model)
        }
        panel.addView(text("Your key stays encrypted with Android Keystore.", 12f, 0xFF526B78.toInt()))
        panel.addView(keyInput)
        panel.addView(modelInput)
        AlertDialog.Builder(this).setTitle("JARVIS CONFIGURATION").setView(panel)
            .setPositiveButton("SAVE") { _, _ ->
                val key = keyInput.text.toString().trim()
                if (key.isNotEmpty()) secureStore.save(key)
                model = modelInput.text.toString().trim().ifBlank { "openai/gpt-4o-mini" }
                prefs.edit().putString("model", model).apply()
                setStatus(if (secureStore.read().isNullOrBlank()) "CONFIGURATION REQUIRED" else "SYSTEM STANDBY")
                append("SYSTEM", "Configuration saved on this device.")
            }
            .setNeutralButton("CLEAR KEY") { _, _ ->
                secureStore.clear()
                setStatus("CONFIGURATION REQUIRED")
                append("SYSTEM", "Stored API key cleared.")
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty() || busy) return
        input.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        history.put(JSONObject().put("role", "user").put("content", prompt))
        append("YOU", prompt)
        busy = true
        core.setActive(true)
        setStatus("PROCESSING REQUEST")
        val key = secureStore.read().orEmpty()
        executor.execute {
            try {
                val answer = agent.run(key, model, history)
                runOnUiThread {
                    append("JARVIS", answer)
                    busy = false
                    core.setActive(false)
                    setStatus("SYSTEM STANDBY")
                    if (prefs.getBoolean("speak_replies", true))
                        speech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    append("SYSTEM ERROR", e.message ?: "Unknown connection error.")
                    busy = false
                    core.setActive(false)
                    setStatus("REQUEST FAILED  /  CHECK CONNECTION")
                }
            }
        }
    }

    private fun append(who: String, body: String) {
        transcript.addView(messageView(who, body, who == "YOU"))
        transcriptScroll.post { transcriptScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setStatus(value: String) {
        status.text = value
        status.setTextColor(if (value.contains("FAILED") || value.contains("REQUIRED")) 0xFFFFB36B.toInt() else cyan)
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 301)
            return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
            startActivityForResult(intent, 302)
        } catch (_: Exception) {
            append("SYSTEM", "Speech recognition is unavailable on this device.")
        }
    }

    @Deprecated("Deprecated by Android, retained for broad IDE compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 302 && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input.setText(spoken)
                sendMessage()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 301 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoiceInput()
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) speech.language = Locale.getDefault()
    }

    override fun onDestroy() {
        speech.stop()
        speech.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }

    private class CoreView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var active = false
        private var rotation = 0f

        fun setActive(value: Boolean) {
            active = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val scale = resources.displayMetrics.density
            val base = 48f * scale
            val pulse = if (active) 5f * scale else 2f * scale
            val radius = base + pulse
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * scale
            paint.color = 0xFF1A5268.toInt()
            canvas.drawCircle(cx, cy, radius + 31f * scale, paint)
            paint.color = 0xFF143A4D.toInt()
            canvas.drawCircle(cx, cy, radius + 21f * scale, paint)
            paint.strokeWidth = 2f * scale
            paint.color = 0xFF45CFF2.toInt()
            val arc = RectF(cx - radius - 13f * scale, cy - radius - 13f * scale,
                cx + radius + 13f * scale, cy + radius + 13f * scale)
            canvas.drawArc(arc, rotation, 82f, false, paint)
            canvas.drawArc(arc, rotation + 180f, 34f, false, paint)
            paint.strokeWidth = 5f * scale
            paint.color = 0xFF16394B.toInt()
            canvas.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.FILL
            paint.color = 0xFF0D2939.toInt()
            canvas.drawCircle(cx, cy, radius - 5f * scale, paint)
            paint.color = 0xFF8CEBFF.toInt()
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            paint.textSize = 34f * scale
            canvas.drawText("J", cx, cy + 12f * scale, paint)
            paint.color = 0xFF5B9CB3.toInt()
            paint.textSize = 8f * scale
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            canvas.drawText(if (active) "PROCESSING" else "CORE ONLINE", cx, cy + radius + 48f * scale, paint)
            rotation = (rotation + if (active) 5f else 1.4f) % 360f
            postInvalidateDelayed(32)
        }
    }
}
