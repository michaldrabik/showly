package com.michaldrabik.ui_base.utilities.extensions

import java.text.Normalizer

private val REGEX_UNACCENT = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun CharSequence.removeDiacritics(): String {
  val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
  return REGEX_UNACCENT.replace(temp, "")
}
