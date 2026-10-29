package com.spendwise.app.data.worker

import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.domain.AiFeature
import com.spendwise.app.domain.aiUsedToday
import com.spendwise.app.domain.todayStr
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** The per-feature daily caps (domain/AiLimits.kt). Only answers the AI gave count, so a failed try costs nothing. */
@Singleton
class AiAllowance @Inject constructor(private val prefs: DevicePrefs, private val auth: AuthRepository) {
    suspend fun left(f: AiFeature): Boolean {
        val uid = auth.currentUid ?: return true // signed out: the Worker refuses anyway
        return aiUsedToday(prefs.aiDay(f.key, uid).first(), todayStr()) < f.perDay
    }

    suspend fun used(f: AiFeature) {
        auth.currentUid?.let { prefs.countAiDay(f.key, it, todayStr()) }
    }
}
