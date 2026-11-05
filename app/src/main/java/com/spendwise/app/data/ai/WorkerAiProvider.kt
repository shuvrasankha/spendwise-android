package com.spendwise.app.data.ai

import com.spendwise.app.data.worker.WorkerClient
import com.spendwise.app.data.worker.WorkerResponse
import com.spendwise.app.data.worker.aiContent
import com.spendwise.app.data.worker.aiRequestOf
import com.spendwise.app.domain.ASK_AI_MODEL
import com.spendwise.app.domain.BUDGET_AI_MODEL
import com.spendwise.app.domain.COACH_AI_MODEL
import com.spendwise.app.domain.CSV_AI_MODEL
import com.spendwise.app.domain.RECEIPT_AI_MODEL
import com.spendwise.app.domain.VOICE_AI_MODEL
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The Worker's model for each task: today's constants, which stay where they are. */
fun workerModel(task: AiTask): String = when (task) {
    AiTask.ASK -> ASK_AI_MODEL
    AiTask.COACH -> COACH_AI_MODEL
    AiTask.VOICE -> VOICE_AI_MODEL
    AiTask.RECEIPT -> RECEIPT_AI_MODEL
    AiTask.CSV -> CSV_AI_MODEL
    AiTask.BUDGET -> BUDGET_AI_MODEL
}

/** SpendWise AI: the Cloudflare Worker. Its contract never changes; NotSignedIn still reaches the caller. */
@Singleton
class WorkerAiProvider(private val post: suspend (body: JsonObject, readTimeoutMs: Int?) -> WorkerResponse) {
    @Inject constructor(worker: WorkerClient) : this({ body, timeout ->
        if (timeout != null) worker.post("/", body, timeout) else worker.post("/", body)
    })

    suspend fun complete(model: String, call: AiCall): AiResult {
        val res = try {
            post(aiRequestOf(model, call.messages, call.maxTokens, call.temperature, call.topP), call.readTimeoutMs)
        } catch (e: IOException) {
            return AiResult.Failed(AiError.Network)
        }
        if (!res.ok) return AiResult.Failed(AiError.Http(res.status), res.status)
        return aiContent(res.body)?.let { AiResult.Ok(it) } ?: AiResult.Failed(AiError.BadReply, res.status)
    }
}
