package com.github.maskedkunisquat.wulfpak.core.logic.llm

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * LLM provider backed by a locally-stored Gemma 3n E4B Instruct LiteRT-LM model.
 * All inference is on-device — no data leaves the device.
 *
 * Single model file for all devices; backend is selected per-chip:
 *   - SM8750 / SM8650  — NPU (QNN) if libpenguin.so present
 *   - Qualcomm (other) — GPU (OpenCL) with CPU fallback
 *   - Everything else  — CPU
 */
class LocalFallbackProvider(
    private val context: Context,
    private val modelDownloader: ModelDownloader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalModelProvider {

    override val id = "gemma3n_e4b_litertlm"

    @Volatile private var engine: Engine? = null
    @Volatile private var initAttempted = false
    @Volatile private var initFailureReason: String? = null
    @Volatile private var openClFailed = false
    private val initLock = Any()
    private val engineLock = ReentrantReadWriteLock()

    private val _modelLoadState = MutableStateFlow(ModelLoadState.IDLE)
    override val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()

    private val _loadedModelName = MutableStateFlow<String?>(null)
    override val loadedModelName: StateFlow<String?> = _loadedModelName.asStateFlow()

    private val modelDir: java.io.File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    override fun downloadModel(): Long {
        val (modelFile, _) = selectModelAndBackends()
        return modelDownloader.enqueue(modelFile, "$HF_BASE_URL/$modelFile", MODEL_SHA256[modelFile])
    }

    fun isModelAvailable(): Boolean {
        val (modelFile, _) = selectModelAndBackends()
        return File(modelDir, modelFile).exists()
    }

    override fun initialize() {
        val (targetFile, _) = selectModelAndBackends()
        if (engine != null && _loadedModelName.value == targetFile) return

        synchronized(initLock) {
            if (engine != null && _loadedModelName.value == targetFile) return

            if (engine != null) {
                Log.i(TAG, "Closing existing engine: ${_loadedModelName.value} -> $targetFile")
                engineLock.writeLock().withLock {
                    (engine as? AutoCloseable)?.runCatching { close() }
                    engine = null
                    _loadedModelName.value = null
                }
            }

            initAttempted = true
            val (modelFileName, backendsToTry) = selectModelAndBackends()
            val modelFile = File(modelDir, modelFileName)
            Log.i(TAG, "Initialising engine — file=$modelFileName board=${Build.BOARD}")

            if (!modelFile.exists()) {
                _modelLoadState.value = ModelLoadState.ERROR
                initFailureReason = "Model not found: $modelFileName. Download from Settings."
                return
            }

            _modelLoadState.value = ModelLoadState.LOADING_SESSION

            var lastException: Exception? = null
            for (backend in backendsToTry) {
                var eng: Engine? = null
                try {
                    eng = Engine(EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                        cacheDir = context.cacheDir.path,
                    ))
                    eng.initialize()
                    engine = eng
                    _loadedModelName.value = modelFileName
                    _modelLoadState.value = ModelLoadState.READY
                    Log.i(TAG, "Engine ready — backend=${backend::class.simpleName} file=$modelFileName")
                    return
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Engine init failed with ${backend::class.simpleName}: ${e.message}")
                    (eng as? AutoCloseable)?.runCatching { close() }
                }
            }

            val e = lastException!!
            _modelLoadState.value = ModelLoadState.ERROR
            initFailureReason = "${e::class.simpleName}: ${e.message}"
            Log.w(TAG, "LocalFallbackProvider init failed", e)
            val modelFileRef = modelFile
            if (modelFileRef.exists()) modelFileRef.runCatching { delete() }
        }
    }

    override suspend fun isAvailable(): Boolean {
        if (!initAttempted) withContext(dispatcher) { initialize() }
        return engine != null
    }

    override fun process(prompt: String, systemInstruction: String?): Flow<LlmResult> =
        processInternal(prompt, systemInstruction, allowOpenClRetry = true)

    private fun processInternal(
        prompt: String,
        systemInstruction: String?,
        allowOpenClRetry: Boolean,
    ): Flow<LlmResult> = flow {
        val eng = engineLock.readLock().withLock {
            engine ?: throw IllegalStateException(
                "Model not loaded. ${initFailureReason ?: "Call initialize() first."}"
            )
        }

        engineLock.readLock().lock()
        try {
            val config = if (systemInstruction != null) {
                ConversationConfig(systemInstruction = Contents.of(systemInstruction))
            } else {
                ConversationConfig()
            }
            eng.createConversation(config).use { conversation ->
                conversation.sendMessageAsync(prompt).collect { message ->
                    val text = message.toString()
                    if (text.isNotEmpty()) emit(LlmResult.Token(text))
                }
                emit(LlmResult.Complete)
            }
        } finally {
            engineLock.readLock().unlock()
        }
    }.catch { e ->
        if (allowOpenClRetry && !openClFailed &&
            e.message?.contains("OpenCL", ignoreCase = true) == true) {
            Log.w(TAG, "GPU inference failed — OpenCL unavailable, switching to CPU")
            openClFailed = true
            withContext(dispatcher) { switchToCpu() }
            emitAll(processInternal(prompt, systemInstruction, allowOpenClRetry = false))
        } else {
            emit(LlmResult.Error(e))
        }
    }.flowOn(dispatcher)

    private fun switchToCpu() {
        synchronized(initLock) {
            engineLock.writeLock().withLock {
                (engine as? AutoCloseable)?.runCatching { close() }
                engine = null
                _loadedModelName.value = null
                initAttempted = false
                initFailureReason = null
                _modelLoadState.value = ModelLoadState.IDLE
            }
        }
        initialize()
    }

    private fun selectModelAndBackends(): Pair<String, List<Backend>> {
        val board = Build.BOARD.lowercase(java.util.Locale.ROOT)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val dispatchLibAvailable = File(nativeLibDir, "libpenguin.so").exists()
        if (!dispatchLibAvailable) Log.w(TAG, "libpenguin.so missing — NPU unavailable")
        val backends = when {
            (board == "sun" || board == "kailua" || board.startsWith("sm8750")) && dispatchLibAvailable ->
                listOf(Backend.NPU(nativeLibraryDir = nativeLibDir))
            (board == "kalama" || board.startsWith("sm8650")) && dispatchLibAvailable ->
                listOf(Backend.NPU(nativeLibraryDir = nativeLibDir))
            isQualcommDevice() ->
                if (!openClFailed) listOf(Backend.GPU(), Backend.CPU()) else listOf(Backend.CPU())
            else -> listOf(Backend.CPU())
        }
        return MODEL_FILE to backends
    }

    private fun isQualcommDevice(): Boolean {
        val hw = Build.HARDWARE.lowercase(java.util.Locale.ROOT)
        val board = Build.BOARD.lowercase(java.util.Locale.ROOT)
        return hw == "qcom" || board.contains("sm8") || board.contains("sdm") ||
            board == "sun" || board == "kailua" || board == "pineapple" || board == "kalama"
    }

    companion object {
        private const val TAG = "LocalFallbackProvider"

        private const val HF_BASE_URL =
            "https://huggingface.co/masked-kunsiquat/gemma-3n-E4B-it-litert-lm/resolve/main"

        const val MODEL_FILE = "gemma-3n-E4B-it-int4.litertlm"

        internal val MODEL_SHA256: Map<String, String?> = mapOf(
            MODEL_FILE to null,
        )
    }
}
