package com.locus.core.ai.di

import javax.inject.Qualifier

/**
 * Qualifier for local offline [com.locus.core.domain.chat.ChatModelClient] implementations (M-2).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalChat

/**
 * Qualifier for cloud/remote [com.locus.core.domain.chat.ChatModelClient] implementations.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudChat
