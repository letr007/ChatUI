package com.letr.chatui.data.repository

import com.letr.chatui.data.model.ActiveChatRuntimeConfig
import kotlinx.coroutines.flow.Flow

interface ActiveChatConfigSource {
    fun observeActiveConfig(): Flow<ActiveChatRuntimeConfig>

    suspend fun getActiveConfig(): ActiveChatRuntimeConfig
}
