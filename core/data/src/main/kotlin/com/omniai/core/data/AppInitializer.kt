package com.omniai.core.data

import android.content.Context
import com.omniai.core.common.ApplicationScope
import com.omniai.core.data.seed.DefaultContent
import com.omniai.core.data.work.MaintenanceWorker
import com.omniai.core.domain.repository.PersonaRepository
import com.omniai.core.domain.repository.PromptRepository
import com.omniai.core.domain.repository.ToolRepository
import com.omniai.core.domain.tools.ToolExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** First-launch seeding and scheduling of local maintenance. Makes no network requests. */
@Singleton
class AppInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val prompts: PromptRepository,
    private val personas: PersonaRepository,
    private val tools: ToolRepository,
    private val toolExecutor: ToolExecutor,
) {
    fun initialize() {
        scope.launch {
            prompts.seedDefaultsIfEmpty()
            personas.seedDefaultsIfEmpty()
            tools.seedDefaultsIfEmpty(DefaultContent.tools(toolExecutor.available().map { it.name to it.description }))
        }
        MaintenanceWorker.schedule(context)
    }
}
