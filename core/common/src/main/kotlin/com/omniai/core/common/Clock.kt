package com.omniai.core.common

import java.util.UUID

fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}
