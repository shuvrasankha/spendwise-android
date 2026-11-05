package com.spendwise.app.data.ai

import javax.inject.Inject
import javax.inject.Singleton

/** Every AI feature calls this, never a provider. Phase 1: always the Worker; consent and quota stay with the features. */
@Singleton
class AiGateway @Inject constructor(private val worker: WorkerAiProvider) {
    suspend fun complete(call: AiCall): AiResult = worker.complete(workerModel(call.task), call)
}
