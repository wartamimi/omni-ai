package com.omniai.core.common

import com.omniai.core.model.ProviderError

sealed interface OmniResult<out T> {
    data class Success<T>(val value: T) : OmniResult<T>
    data class Failure(val error: ProviderError) : OmniResult<Nothing>
}

inline fun <T, R> OmniResult<T>.map(transform: (T) -> R): OmniResult<R> = when (this) {
    is OmniResult.Success -> OmniResult.Success(transform(value))
    is OmniResult.Failure -> this
}

fun <T> OmniResult<T>.getOrNull(): T? = (this as? OmniResult.Success)?.value
