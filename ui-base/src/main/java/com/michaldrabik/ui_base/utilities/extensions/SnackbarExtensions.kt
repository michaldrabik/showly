package com.michaldrabik.ui_base.utilities.extensions

import android.view.ViewGroup
import androidx.annotation.ColorInt
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.michaldrabik.ui_base.R

fun ViewGroup.showSnackbar(
  message: String,
  actionText: Int,
  textColor: Int,
  @ColorInt backgroundColor: Int,
  length: Int,
  action: (() -> Unit)? = null,
): Snackbar =
  Snackbar.make(this, message, length).apply {
    setTextMaxLines(10)
    setTextColor(textColor)
    setBackgroundTint(backgroundColor)
    setActionTextColor(textColor)
    if (action != null) {
      setAction(actionText) {
        dismiss()
        action()
      }
    }
    show()
  }

fun ViewGroup.showInfoSnackbar(
  message: String,
  actionText: Int = R.string.textOk,
  length: Int = LENGTH_SHORT,
  action: (() -> Unit)? = null,
): Snackbar =
  showSnackbar(
    message = message,
    actionText = actionText,
    textColor = context.colorFromAttr(R.attr.textColorInfoSnackbar),
    backgroundColor = context.colorFromAttr(R.attr.colorInfoSnackbar),
    length = length,
    action = action,
  )

fun ViewGroup.showErrorSnackbar(
  message: String,
  actionText: Int = R.string.textOk,
  action: () -> Unit = {},
): Snackbar =
  showSnackbar(
    message = message,
    actionText = actionText,
    textColor = context.colorFromAttr(R.attr.textColorErrorSnackbar),
    backgroundColor = context.colorFromAttr(R.attr.colorErrorSnackbar),
    length = LENGTH_INDEFINITE,
    action = action,
  )
