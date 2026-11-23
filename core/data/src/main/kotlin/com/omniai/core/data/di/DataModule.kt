package com.omniai.core.data.di

import com.omniai.core.common.ApplicationScope
import com.omniai.core.common.Dispatcher
import com.omniai.core.common.OmniDispatcher
import com.omniai.core.data.AdapterProviderImpl
import com.omniai.core.data.ApiLogBuffer
import com.omniai.core.data.attachments.AttachmentProcessor
import com.omniai.core.data.repository.ConversationRepositoryImpl
import com.omniai.core.data.repository.CredentialRepositoryImpl
import com.omniai.core.data.repository.ModelRepositoryImpl
import com.omniai.core.data.repository.PersonaRepositoryImpl
import com.omniai.core.data.repository.PromptRepositoryImpl
import com.omniai.core.data.repository.ProviderRepositoryImpl
import com.omniai.core.data.repository.SettingsRepositoryImpl
import com.omniai.core.data.repository.ToolRepositoryImpl
import com.omniai.core.data.repository.UsageRepositoryImpl
import com.omniai.core.domain.chat.ChatEngine
import com.omniai.core.domain.compare.CompareEngine
import com.omniai.core.domain.repository.AdapterProvider
import com.omniai.core.domain.repository.ConversationRepository
import com.omniai.core.domain.repository.CredentialRepository
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.PersonaRepository
import com.omniai.core.domain.repository.PromptRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.domain.repository.ToolRepository
import com.omniai.core.domain.repository.UsageRepository
import com.omniai.core.domain.router.ModelRouter
import com.omniai.core.domain.tools.BuiltInTools
import com.omniai.core.domain.tools.ToolExecutor
import com.omniai.core.provider.AdapterFactory
import com.omniai.core.provider.http.HttpClientFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun providers(impl: ProviderRepositoryImpl): ProviderRepository
    @Binds abstract fun credentials(impl: CredentialRepositoryImpl): CredentialRepository
    @Binds abstract fun models(impl: ModelRepositoryImpl): ModelRepository
    @Binds abstract fun conversations(impl: ConversationRepositoryImpl): ConversationRepository
    @Binds abstract fun usage(impl: UsageRepositoryImpl): UsageRepository
    @Binds abstract fun prompts(impl: PromptRepositoryImpl): PromptRepository
    @Binds abstract fun personas(impl: PersonaRepositoryImpl): PersonaRepository
    @Binds abstract fun tools(impl: ToolRepositoryImpl): ToolRepository
    @Binds abstract fun adapters(impl: AdapterProviderImpl): AdapterProvider
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides @Dispatcher(OmniDispatcher.IO) fun io(): CoroutineDispatcher = Dispatchers.IO
    @Provides @Dispatcher(OmniDispatcher.DEFAULT) fun default(): CoroutineDispatcher = Dispatchers.Default

    @Provides @Singleton @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    /** Live network policy flags read by OkHttp on every DNS lookup. */
    class NetworkPolicy(val allowLocal: StateFlow<Boolean>, val apiLogging: StateFlow<Boolean>)

    @Provides @Singleton
    fun networkPolicy(settings: SettingsRepository, @ApplicationScope scope: CoroutineScope) = NetworkPolicy(
        settings.settings.map { it.allowLocalNetworkEndpoints }.stateIn(scope, SharingStarted.Eagerly, false),
        settings.settings.map { it.apiLogging && it.debugMode }.stateIn(scope, SharingStarted.Eagerly, false),
    )

    @Provides @Singleton
    fun httpClients(policy: NetworkPolicy, log: ApiLogBuffer) =
        HttpClientFactory(allowLocalNetwork = { policy.allowLocal.value }, logSink = log, loggingEnabled = { policy.apiLogging.value })

    @Provides @Singleton fun adapterFactory(clients: HttpClientFactory) = AdapterFactory(clients)

    @Provides @Singleton fun toolExecutor(tools: ToolRepository) = ToolExecutor(BuiltInTools.all(), tools)

    @Provides @Singleton fun router() = ModelRouter()

    @Provides @Singleton
    fun chatEngine(
        conversations: ConversationRepository, providers: ProviderRepository, models: ModelRepository, personas: PersonaRepository,
        settings: SettingsRepository, adapters: AdapterProvider, usage: UsageRepository, tools: ToolExecutor,
        attachments: AttachmentProcessor, @ApplicationScope scope: CoroutineScope, approvals: com.omniai.core.data.ToolApprovalBridge,
    ) = ChatEngine(conversations, providers, models, personas, settings, adapters, usage, tools, attachments, scope).also { it.toolApprover = approvals }

    @Provides @Singleton
    fun compareEngine(
        providers: ProviderRepository, models: ModelRepository, adapters: AdapterProvider, usage: UsageRepository,
        @ApplicationScope scope: CoroutineScope,
    ) = CompareEngine(providers, models, adapters, usage, scope)
}
