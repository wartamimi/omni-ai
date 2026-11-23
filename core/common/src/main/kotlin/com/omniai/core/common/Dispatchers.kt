package com.omniai.core.common

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: OmniDispatcher)

enum class OmniDispatcher { DEFAULT, IO }

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
