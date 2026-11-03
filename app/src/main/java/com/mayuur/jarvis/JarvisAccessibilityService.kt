package com.mayuur.jarvis

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun visibleScreenText(): String {
        val root = rootInActiveWindow ?: return "No accessible active window is available."
        val output = StringBuilder()
        appendNode(root, output, 0)
        return output.toString().take(6000).ifBlank { "The active screen exposes no readable text." }
    }

    private fun appendNode(node: AccessibilityNodeInfo, output: StringBuilder, depth: Int) {
        if (depth > 18 || output.length > 6000) return
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() || description.isNotEmpty()) {
            output.append("  ".repeat(depth.coerceAtMost(8)))
            output.append(text.ifEmpty { description }).append('\n')
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            appendNode(child, output, depth + 1)
            child.recycle()
        }
    }

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
}
