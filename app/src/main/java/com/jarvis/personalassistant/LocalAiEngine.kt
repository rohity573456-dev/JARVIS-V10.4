package com.jarvis.personalassistant

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** On-device LLM bridge. The .litertlm model is selected by the user and stored privately. */
object LocalAiEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: com.google.ai.edge.litertlm.Conversation? = null

    @JvmStatic fun load(modelPath: String, callback: (String) -> Unit) {
        scope.launch {
            try {
                closeEngineOnly()
                val newEngine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
                newEngine.initialize()
                engine = newEngine
                conversation = newEngine.createConversation()
                callback("Offline AI model loaded successfully. JARVIS local brain is ready.")
            } catch (t: Throwable) {
                closeEngineOnly()
                callback("Offline model could not be loaded: ${t.message ?: "unsupported model or runtime error"}")
            }
        }
    }

    @JvmStatic fun ask(modelPath: String, prompt: String, callback: (String) -> Unit) {
        scope.launch {
            try {
                if (engine == null || conversation == null) {
                    val newEngine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
                    newEngine.initialize()
                    engine = newEngine
                    conversation = newEngine.createConversation()
                }
                val output = StringBuilder()
                conversation!!.sendMessageAsync(prompt).collect { chunk -> output.append(chunk.toString()) }
                val answer = output.toString().trim().ifEmpty { "The local model returned an empty response." }
                callback(answer)
            } catch (t: Throwable) {
                callback("Offline AI error: ${t.message ?: "model inference failed"}")
            }
        }
    }

    @JvmStatic fun close() { scope.launch { closeEngineOnly() } }

    private fun closeEngineOnly() {
        try { conversation?.close() } catch (_: Throwable) {}
        conversation = null
        try { engine?.close() } catch (_: Throwable) {}
        engine = null
    }
}
