package com.michaldrabik.ui_settings.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.core.view.children
import com.google.android.material.chip.Chip
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_settings.databinding.ViewSettingsFiltersBinding
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.BACKUP
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.GENERAL
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.MISC
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.NOTIFICATIONS
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.SPOILERS
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.TRAKT
import com.michaldrabik.ui_settings.views.SettingsFiltersView.SettingsFilter.WIDGETS

class SettingsFiltersView : FrameLayout {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewSettingsFiltersBinding.inflate(LayoutInflater.from(context), this)

  var onFilterClick: ((SettingsFilter?) -> Unit)? = null
  private var selectedFilter: SettingsFilter? = null

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    initView()
  }

  private fun initView() {
    with(binding) {
      traktChip.onClick(safe = false) {
        selectedFilter = if (selectedFilter == TRAKT) null else TRAKT
        onFilterClick?.invoke(selectedFilter)
      }
      generalChip.onClick(safe = false) {
        selectedFilter = if (selectedFilter == GENERAL) null else GENERAL
        onFilterClick?.invoke(selectedFilter)
      }
      notificationsChip.onClick(safe = false) {
        selectedFilter = if (selectedFilter == NOTIFICATIONS) null else NOTIFICATIONS
        onFilterClick?.invoke(selectedFilter)
      }
      spoilersChip.onClick(safe = false) {
        selectedFilter = if (selectedFilter == SPOILERS) null else SPOILERS
        onFilterClick?.invoke(selectedFilter)
      }
      widgetsChip.onClick(safe = false) {
        selectedFilter = if (selectedFilter == WIDGETS) null else WIDGETS
        onFilterClick?.invoke(selectedFilter)
      }
      backupChip.onClick(safe = false) {
        selectedFilter = if (selectedFilter == BACKUP) null else BACKUP
        onFilterClick?.invoke(selectedFilter)
      }
      miscChip.onClick(safe = false) {
        selectedFilter = if (selectedFilter == MISC) null else MISC
        onFilterClick?.invoke(selectedFilter)
      }
    }
  }

  fun clear() {
    selectedFilter = null
    binding.chipsGroup.children.forEach {
      (it as? Chip)?.isChecked = false
    }
  }

  override fun setEnabled(enabled: Boolean) {
    binding.chipsGroup.children.forEach { it.isEnabled = enabled }
  }

  enum class SettingsFilter {
    TRAKT,
    GENERAL,
    NOTIFICATIONS,
    SPOILERS,
    WIDGETS,
    BACKUP,
    MISC,
  }
}
