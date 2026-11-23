package com.jarvis.personalassistant

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** On-device LLM bridge. The .litertlm model is selected by the user and stored privately. */
object LocalAiEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // LiteRT-LM conversations are stateful; never send two turns through one instance concurrently.
    private val engineMutex = Mutex()
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: com.google.ai.edge.litertlm.Conversation? = null

    @JvmStatic fun load(modelPath: String, callback: (String) -> Unit) {
        scope.launch {
            val result = engineMutex.withLock {
                try {
                    closeEngineOnly()
                    val newEngine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
                    newEngine.initialize()
                    engine = newEngine
                    conversation = newEngine.createConversation()
                    "Offline AI model loaded successfully. JARVIS local brain is ready."
                } catch (t: Throwable) {
                    closeEngineOnly()
                    "Offline model could not be loaded: ${t.message ?: "unsupported model or runtime error"}"
                }
            }
            callback(result)
        }
    }

    @JvmStatic fun ask(modelPath: String, prompt: String, callback: (String) -> Unit) {
        scope.launch {
            val answer = engineMutex.withLock {
                try {
                    if (engine == null || conversation == null) {
                        val newEngine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
                        newEngine.initialize()
                        engine = newEngine
                        conversation = newEngine.createConversation()
                    }
                    val output = StringBuilder()
                    conversation!!.sendMessageAsync(prompt).collect { chunk -> output.append(chunk.toString()) }
                    output.toString().trim().ifEmpty { "The local model returned an empty response." }
                } catch (t: Throwable) {
                    "Offline AI error: ${t.message ?: "model inference failed"}"
                }
            }
            callback(answer)
        }
    }

    @JvmStatic fun close() {
        scope.launch { engineMutex.withLock { closeEngineOnly() } }
    }

    private fun closeEngineOnly() {
        try { conversation?.close() } catch (_: Throwable) {}
        conversation = null
        try { engine?.close() } catch (_: Throwable) {}
        engine = null
    }
}