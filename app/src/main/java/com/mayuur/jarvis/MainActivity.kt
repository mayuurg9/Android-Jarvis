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
    private lateinit var transcript: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var speech: TextToSpeech
    private lateinit var agent: OpenRouterAgent
    private val history = JSONArray()
    private var model = "openai/gpt-4o-mini"
    private val prefs by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureKeyStore(this)
        agent = OpenRouterAgent(AgentTools(this))
        model = prefs.getString("model", model) ?: model
        speech = TextToSpeech(this, this)
        buildInterface()
        history.put(JSONObject().put("role", "system").put("content",
            "You are JARVIS, a careful Android personal assistant. Use available tools when useful. Never claim an action succeeded unless the tool reports success. Ask before actions not represented by an available tool. Be clear and concise."))
        setStatus(if (secureStore.read().isNullOrBlank()) "SETUP REQUIRED • ADD OPENROUTER KEY" else "SYSTEM READY • " + model)
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 16)
            setBackgroundColor(0xFF070D14.toInt())
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "J A R V I S"; textSize = 22f; letterSpacing = 0.22f
            setTextColor(0xFFE5F7FF.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("SETUP") { showSettings() })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "PERSONAL INTELLIGENCE SYSTEM  /  ANDROID"
            textSize = 10f; letterSpacing = 0.12f; setTextColor(0xFF6D91A8.toInt())
            setPadding(0, 5, 0, 12)
        })
        root.addView(CoreView(this), LinearLayout.LayoutParams(-1, 150))
        status = TextView(this).apply {
            textSize = 10f; letterSpacing = 0.08f; gravity = Gravity.CENTER
            setTextColor(0xFF69D9F5.toInt()); setPadding(0, 8, 0, 12)
        }
        root.addView(status)
        val scroll = ScrollView(this).apply {
            setFillViewport(true); setBackgroundColor(0xFF0B1520.toInt()); setPadding(12, 12, 12, 12)
        }
        transcript = TextView(this).apply {
            text = "JARVIS ONLINE\n\nConnect an OpenRouter key in SETUP to begin."
            textSize = 14f; setTextColor(0xFFD4E5EF.toInt()); setLineSpacing(5f, 1f)
        }
        scroll.addView(transcript)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply {
            hint = "Give JARVIS an instruction..."
            setHintTextColor(0xFF658094.toInt()); setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f; setSingleLine(false); maxLines = 3
            setPadding(14, 10, 14, 10); setBackgroundColor(0xFF122333.toInt())
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 10, 0, 0)
        }
        controls.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button("VOICE") { startVoiceInput() })
        controls.addView(button("SEND") { sendMessage() })
        root.addView(controls)
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 10f; setTextColor(0xFF75E3FF.toInt())
        setBackgroundColor(0xFF142B3A.toInt()); setOnClickListener { action() }
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 12, 22, 4) }
        val keyInput = EditText(this).apply {
            hint = "OpenRouter API key"; inputType = 129; setText(secureStore.read().orEmpty())
        }
        val modelInput = EditText(this).apply {
            hint = "Model ID, e.g. openai/gpt-4o-mini"; setText(model)
        }
        panel.addView(TextView(this).apply { text = "API key is encrypted with Android Keystore." })
        panel.addView(keyInput); panel.addView(modelInput)
        AlertDialog.Builder(this).setTitle("JARVIS CONFIGURATION").setView(panel)
            .setPositiveButton("SAVE") { _, _ ->
                val key = keyInput.text.toString().trim()
                if (key.isNotEmpty()) secureStore.save(key)
                model = modelInput.text.toString().trim().ifBlank { "openai/gpt-4o-mini" }
                prefs.edit().putString("model", model).apply()
                setStatus(if (secureStore.read().isNullOrBlank()) "SETUP REQUIRED • ADD OPENROUTER KEY" else "SYSTEM READY • " + model)
                append("SYSTEM", "Configuration saved on this device.")
            }
            .setNeutralButton("CLEAR KEY") { _, _ ->
                secureStore.clear(); setStatus("SETUP REQUIRED • ADD OPENROUTER KEY")
                append("SYSTEM", "Stored API key cleared.")
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        input.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        history.put(JSONObject().put("role", "user").put("content", prompt))
        append("YOU", prompt); setStatus("PROCESSING REQUEST...")
        val key = secureStore.read().orEmpty()
        executor.execute {
            try {
                val answer = agent.run(key, model, history)
                runOnUiThread {
                    append("JARVIS", answer); setStatus("SYSTEM READY • " + model)
                    if (prefs.getBoolean("speak_replies", true)) speech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    append("ERROR", e.message ?: "Unknown connection error.")
                    setStatus("REQUEST FAILED • CHECK KEY / NETWORK / MODEL")
                }
            }
        }
    }

    private fun append(who: String, text: String) { transcript.append("\n\n" + who + "\n" + text) }
    private fun setStatus(value: String) { status.text = value }

    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 301); return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
            startActivityForResult(intent, 302)
        } catch (_: Exception) { append("SYSTEM", "Speech recognition is unavailable on this device.") }
    }

    @Deprecated("Deprecated by Android, retained for broad IDE compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 302 && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) { input.setText(spoken); sendMessage() }
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
        speech.stop(); speech.shutdown(); executor.shutdownNow(); super.onDestroy()
    }

    private class CoreView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f; val radius = 48f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f; paint.color = 0xFF1B607B.toInt()
            canvas.drawCircle(cx, cy, radius + 22f, paint)
            paint.color = 0xFF35C9EF.toInt(); paint.strokeWidth = 2f
            canvas.drawArc(RectF(cx-radius-10, cy-radius-10, cx+radius+10, cy+radius+10), -90f, 235f, false, paint)
            paint.color = 0xFF16384C.toInt(); paint.strokeWidth = 5f
            canvas.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.FILL; paint.color = 0xFF0D2939.toInt()
            canvas.drawCircle(cx, cy, radius-5f, paint)
            paint.color = 0xFF8CEBFF.toInt(); paint.textAlign = Paint.Align.CENTER; paint.textSize = 13f
            canvas.drawText("J", cx, cy+5f, paint)
            paint.color = 0xFF4E9AB4.toInt(); paint.textSize = 8f
            canvas.drawText("CORE SYSTEM", cx, cy+radius+42f, paint)
        }
    }
}
