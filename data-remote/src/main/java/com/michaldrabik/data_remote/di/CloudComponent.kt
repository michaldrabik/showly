package com.michaldrabik.data_remote.di

import com.michaldrabik.data_remote.di.module.AwsModule
import com.michaldrabik.data_remote.di.module.OkHttpModule
import com.michaldrabik.data_remote.di.module.RetrofitModule
import com.michaldrabik.data_remote.di.module.TmdbModule
import com.michaldrabik.data_remote.di.module.TraktModule
import dagger.Component

@CloudScope
@Component(
  modules = [
    RetrofitModule::class,
    OkHttpModule::class,
    TraktModule::class,
    TmdbModule::class,
    AwsModule::class
  ]
)
interface CloudComponent : CloudMarker