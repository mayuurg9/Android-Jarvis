package com.mayuur.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OpenRouterAgent(private val tools: AgentTools) {
    fun run(apiKey: String, model: String, history: JSONArray): String {
        if (apiKey.isBlank()) return "Add your OpenRouter API key in SETUP first."
        repeat(4) {
            val response = request(apiKey, model, history)
            val message = response.getJSONObject("choices").getJSONObject(0).getJSONObject("message")
            val calls = message.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                val answer = message.optString("content", "The model returned an empty response.")
                history.put(JSONObject().put("role", "assistant").put("content", answer))
                return answer
            }
            history.put(message)
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i)
                val fn = call.getJSONObject("function")
                val args = try { JSONObject(fn.optString("arguments", "{}")) } catch (_: Exception) { JSONObject() }
                history.put(JSONObject().put("role", "tool").put("tool_call_id", call.optString("id"))
                    .put("content", tools.execute(fn.optString("name"), args)))
            }
        }
        return "I stopped after four tool rounds to keep execution bounded. Give me a more focused instruction."
    }

    private fun request(apiKey: String, model: String, history: JSONArray): JSONObject {
        val connection = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 20000
        connection.readTimeout = 45000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer " + apiKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("HTTP-Referer", "https://github.com/mayuurg9/Android-Jarvis")
        connection.setRequestProperty("X-Title", "JARVIS Android")
        val body = JSONObject().put("model", model).put("messages", history)
            .put("tools", toolDefinitions()).put("tool_choice", "auto")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            val detail = try { JSONObject(responseText).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
            throw IllegalStateException("OpenRouter HTTP " + code + ": " + (detail ?: responseText.take(400)))
        }
        return JSONObject(responseText)
    }

    private fun field(type: String, description: String) =
        JSONObject().put("type", type).put("description", description)

    private fun tool(name: String, description: String, parameters: JSONObject) =
        JSONObject().put("type", "function").put("function",
            JSONObject().put("name", name).put("description", description).put("parameters", parameters))

    private fun toolDefinitions(): JSONArray {
        val definitions = JSONArray()
        definitions.put(tool("launch_app", "Launch an installed Android app by its exact visible app name.",
            JSONObject().put("type", "object").put("properties", JSONObject().put("app_name", field("string", "Exact app name"))).put("required", JSONArray().put("app_name"))))
        definitions.put(tool("open_url", "Open an HTTP or HTTPS URL in the system browser.",
            JSONObject().put("type", "object").put("properties", JSONObject().put("url", field("string", "HTTP or HTTPS URL"))).put("required", JSONArray().put("url"))))
        definitions.put(tool("save_note", "Save a private text note inside JARVIS app storage.",
            JSONObject().put("type", "object").put("properties", JSONObject().put("title", field("string", "Short note title")).put("content", field("string", "Note content"))).put("required", JSONArray().put("content"))))
        definitions.put(tool("read_screen", "Read text exposed by the active screen. Requires the user to enable JARVIS Accessibility.",
            JSONObject().put("type", "object").put("properties", JSONObject())))
        return definitions
    }
}
