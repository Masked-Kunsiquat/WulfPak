package com.github.maskedkunisquat.wulfpak.core.logic.embedding

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Generates 384-dim sentence embeddings using Snowflake Arctic Embed XS TFLite (float16, 33 MB).
 *
 * Call [initialize] once (e.g. in AppApplication) before [generateEmbedding].
 * Falls back to zero-vectors when the model asset is absent so the app remains
 * functional during development.
 *
 * Assets bundled in core-logic/src/main/assets/:
 *   - snowflake-arctic-embed-xs_float16.tflite  (33 MB)
 *   - vocab.txt
 */
open class EmbeddingProvider(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private var interpreter: Interpreter? = null
    private var tokenizer: WordPieceTokenizer? = null
    private val fallbackLoggedInterpreter = AtomicBoolean(false)
    private val fallbackLoggedTokenizer = AtomicBoolean(false)

    fun initialize(context: Context) {
        try {
            val vocabLines = context.assets.open(VOCAB_ASSET).bufferedReader().readLines()
            tokenizer = WordPieceTokenizer(vocabLines)

            val modelBytes = context.assets.open(MODEL_ASSET).readBytes()
            val buf = ByteBuffer.allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
                .apply { put(modelBytes); rewind() }

            // float16 TFLite models require the GPU delegate; CPU interpreter rejects mixed-precision
            // tensors (FLOAT32 vs FLOAT16 at SUB node 36). Fall back to CPU only if GPU is unavailable.
            interpreter = try {
                val opts = Interpreter.Options().apply {
                    numThreads = 4
                    addDelegate(GpuDelegate())
                }
                Interpreter(buf, opts).also { Log.i(TAG, "Initialized with GPU delegate") }
            } catch (e: Exception) {
                Log.w(TAG, "GPU delegate init failed, falling back to CPU: ${e.message}")
                buf.rewind()
                Interpreter(buf, Interpreter.Options().apply { numThreads = 4 })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize embedding model: ${e.message}")
        }
    }

    val isInitialized: Boolean get() = interpreter != null && tokenizer != null

    open suspend fun generateEmbedding(text: String): FloatArray = withContext(dispatcher) {
        val interp = interpreter ?: run {
            if (fallbackLoggedInterpreter.compareAndSet(false, true)) {
                Log.w(TAG, "generateEmbedding: model not initialized, returning zero-vector")
            }
            return@withContext FloatArray(EMBEDDING_DIM)
        }
        val tok = tokenizer ?: run {
            if (fallbackLoggedTokenizer.compareAndSet(false, true)) {
                Log.w(TAG, "generateEmbedding: tokenizer not initialized, returning zero-vector")
            }
            return@withContext FloatArray(EMBEDDING_DIM)
        }
        runInference(interp, tok, text)
    }

    private fun runInference(interp: Interpreter, tokenizer: WordPieceTokenizer, text: String): FloatArray {
        val (rawIds, rawMask) = tokenizer.encode(text)

        val inputIds = LongArray(MODEL_SEQ_LEN)
        val attnMask = LongArray(MODEL_SEQ_LEN)
        val copyLen  = minOf(rawIds.size, MODEL_SEQ_LEN)
        for (i in 0 until copyLen) {
            inputIds[i] = rawIds[i]
            attnMask[i] = rawMask[i]
        }

        val output = Array(1) { Array(MODEL_SEQ_LEN) { FloatArray(EMBEDDING_DIM) } }
        interp.runSignature(
            mapOf(
                "input_ids"      to Array(1) { inputIds },
                "attention_mask" to Array(1) { attnMask },
                "token_type_ids" to Array(1) { LongArray(MODEL_SEQ_LEN) },
            ),
            mapOf("last_hidden_state" to output)
        )
        return maskedMeanPool(output[0], attnMask)
    }

    private fun maskedMeanPool(tokenVectors: Array<FloatArray>, mask: LongArray): FloatArray {
        val pooled = FloatArray(EMBEDDING_DIM)
        var count = 0
        for (i in tokenVectors.indices) {
            if (mask[i] == 1L) {
                for (j in 0 until EMBEDDING_DIM) pooled[j] += tokenVectors[i][j]
                count++
            }
        }
        val n = if (count > 0) count.toFloat() else tokenVectors.size.toFloat()
        for (i in pooled.indices) pooled[i] /= n
        return pooled
    }

    companion object {
        const val EMBEDDING_DIM = 384
        const val MODEL_SEQ_LEN = 128
        private const val MODEL_ASSET = "snowflake-arctic-embed-xs_float16.tflite"
        private const val VOCAB_ASSET = "vocab.txt"
        private const val TAG = "EmbeddingProvider"
    }
}
