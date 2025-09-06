package com.michaldrabik.ui_base.utilities.extensions

import android.graphics.drawable.Drawable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

inline fun RequestBuilder<Drawable>.withFailListener(crossinline action: () -> Unit) =
  addListener(object : RequestListener<Drawable?> {
    override fun onLoadFailed(
      e: GlideException?,
      model: Any?,
      target: Target<Drawable?>,
      isFirstResource: Boolean,
    ): Boolean {
      action()
      return false
    }

    override fun onResourceReady(
      resource: Drawable?,
      model: Any,
      target: Target<Drawable?>?,
      dataSource: DataSource,
      isFirstResource: Boolean,
    ): Boolean = false
  })

inline fun RequestBuilder<Drawable>.withSuccessListener(crossinline action: () -> Unit) =
  addListener(object : RequestListener<Drawable?> {
    override fun onLoadFailed(
      e: GlideException?,
      model: Any?,
      target: Target<Drawable?>,
      isFirstResource: Boolean,
    ): Boolean = false

    override fun onResourceReady(
      resource: Drawable?,
      model: Any,
      target: Target<Drawable?>?,
      dataSource: DataSource,
      isFirstResource: Boolean,
    ): Boolean {
      action()
      return false
    }
  })
