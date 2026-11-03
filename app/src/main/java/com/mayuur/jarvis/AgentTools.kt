package com.mayuur.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import org.json.JSONObject

class AgentTools(private val context: Context) {
    fun execute(name: String, args: JSONObject): String = try {
        when (name) {
            "launch_app" -> launchApp(args.optString("app_name"))
            "open_url" -> openUrl(args.optString("url"))
            "save_note" -> saveNote(args.optString("title"), args.optString("content"))
            "read_screen" -> JarvisAccessibilityService.instance?.visibleScreenText()
                ?: "Screen reading unavailable. Enable JARVIS Screen Access in Android Accessibility settings."
            else -> "Tool rejected: unknown tool."
        }
    } catch (e: Exception) { "Tool failed: " + (e.message ?: e.javaClass.simpleName) }

    private fun launchApp(label: String): String {
        if (label.isBlank()) return "App name is required."
        val pm = context.packageManager
        val info = pm.getInstalledApplications(0).firstOrNull {
            pm.getApplicationLabel(it).toString().equals(label, ignoreCase = true)
        } ?: return "No launchable app matched that exact name."
        val intent = pm.getLaunchIntentForPackage(info.packageName)
            ?: return "The selected app has no launch activity."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Launch intent sent for " + label + "."
    }

    private fun openUrl(raw: String): String {
        val uri = Uri.parse(raw.trim())
        if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank())
            return "Rejected URL. Only valid HTTP or HTTPS links can be opened."
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Opened URL in the system browser."
    }

    private fun saveNote(title: String, content: String): String {
        if (content.isBlank()) return "Note content cannot be empty."
        val directory = File(context.filesDir, "notes").apply { mkdirs() }
        val safeTitle = (title.ifBlank { "Note" }).replace(Regex("[^A-Za-z0-9 _-]"), "").take(50)
        val file = File(directory, System.currentTimeMillis().toString() + "_" + safeTitle + ".txt")
        file.writeText(content.take(12000), Charsets.UTF_8)
        return "Saved a private note in app storage as " + file.name + "."
    }
}
