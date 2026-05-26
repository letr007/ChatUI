package com.letr.chatui.settings

import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionProviderAdapterFactory
import com.letr.chatui.network.chatcompletions.OpenAiProviderConfig

interface OpenAiModelsCatalogClient {
    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String>
}

class RealOpenAiModelsCatalogClient(
    private val adapterFactory: OpenAiChatCompletionProviderAdapterFactory,
) : OpenAiModelsCatalogClient {
    override suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> {
        return adapterFactory.create(
            OpenAiProviderConfig(
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelId = "",
            )
        ).listModels().data
            .map { it.id.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }
}
