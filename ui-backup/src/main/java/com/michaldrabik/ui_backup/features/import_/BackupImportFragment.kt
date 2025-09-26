package com.michaldrabik.ui_backup.features.import_

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.michaldrabik.ui_backup.R
import com.michaldrabik.ui_backup.databinding.FragmentBackupImportBinding
import com.michaldrabik.ui_backup.features.export.cases.ReadBackupJsonFromFileUseCase
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Idle
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Importing
import com.michaldrabik.ui_backup.features.import_.model.BackupImportStatus.Initializing
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.SnackbarHost
import com.michaldrabik.ui_base.utilities.events.MessageEvent.Error
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.showErrorSnackbar
import com.michaldrabik.ui_base.utilities.extensions.showInfoSnackbar
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@AndroidEntryPoint
class BackupImportFragment : BaseFragment<BackupImportViewModel>(R.layout.fragment_backup_import) {

  @Inject
  lateinit var readBackupJsonFromFileUseCase: ReadBackupJsonFromFileUseCase

  override val viewModel by viewModels<BackupImportViewModel>()
  private val binding by viewBinding(FragmentBackupImportBinding::bind)

  private val pickFileContract = registerForActivityResult(
    OpenDocument(),
  ) { uri ->
    uri?.let { readImportFile(it) }
  }

  private var snackbar: Snackbar? = null

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupInsets()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
    )
  }

  private fun setupView() {
    with(binding) {
      toolbar.onClick { activity?.onBackPressed() }
      importButton.onClick { openNewImport() }
    }
  }

  private fun setupInsets() {
    with(binding) {
      root.doOnApplyWindowInsets { view, insets, padding, _ ->
        val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
          top = padding.top + inset.top,
          bottom = padding.bottom + inset.bottom,
        )
      }
    }
  }

  private fun openNewImport() {
    pickFileContract.launch(arrayOf("application/json"))
  }

  private fun readImportFile(uri: Uri) {
    readBackupJsonFromFileUseCase(requireContext(), uri).fold(
      onSuccess = { viewModel.runImport(it) },
      onFailure = {
        showErrorSnack(it)
        Timber.e(it)
      },
    )
  }

  private fun showSuccessSnack() {
    val host = (requireActivity() as SnackbarHost).provideSnackbarLayout()
    snackbar = host.showInfoSnackbar(
      message = getString(R.string.textBackupImportSuccess),
    )
  }

  private fun showErrorSnack(error: Throwable) {
    if (error is CancellationException) {
      return
    }
    val host = (requireActivity() as SnackbarHost).provideSnackbarLayout()
    snackbar = host.showErrorSnackbar(
      message = error.localizedMessage ?: getString(R.string.errorGeneral),
    )
  }

  override fun onDestroyView() {
    if (viewModel.uiState.value.isImporting != Idle) {
      showSnack(Error(R.string.errorImportCancelled))
    } else {
      snackbar?.dismiss()
    }
    super.onDestroyView()
  }

  private fun render(uiState: BackupImportUiState) {
    uiState.run {
      with(binding) {
        progressBar.visibleIf(isImporting != Idle)
        importButton.visibleIf(isImporting == Idle, gone = false)
        importButton.isEnabled = isImporting == Idle
      }
      renderImportStatus(uiState)

      if (isSuccess) {
        showSuccessSnack()
        viewModel.clearState()
      }

      if (isError != null) {
        showErrorSnack(isError)
        viewModel.clearState()
      }
    }
  }

  private fun renderImportStatus(uiState: BackupImportUiState) {
    with(binding) {
      statusText.visibleIf(uiState.isImporting != Idle)
      statusText.text = when (uiState.isImporting) {
        is Idle -> ""
        is Initializing -> "Importing..."
        is Importing -> "Importing...\n\n\"${uiState.isImporting.title}\""
      }
    }
  }
}
