package com.michaldrabik.ui_backup.features.export

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.michaldrabik.common.extensions.dateFromMillis
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toLocalZone
import com.michaldrabik.ui_backup.R
import com.michaldrabik.ui_backup.databinding.FragmentBackupExportBinding
import com.michaldrabik.ui_backup.features.export.cases.ReadBackupJsonFromFileUseCase
import com.michaldrabik.ui_backup.features.export.cases.WriteBackupJsonToFileUseCase
import com.michaldrabik.ui_backup.features.export.model.BackupExportSchedule
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.SnackbarHost
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.capitalizeWords
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.showErrorSnackbar
import com.michaldrabik.ui_base.utilities.extensions.showInfoSnackbar
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class BackupExportFragment : BaseFragment<BackupExportViewModel>(R.layout.fragment_backup_export) {

  @Inject
  lateinit var writeBackupJsonToFileUseCase: WriteBackupJsonToFileUseCase

  @Inject
  lateinit var readBackupJsonFromFileUseCase: ReadBackupJsonFromFileUseCase

  override val viewModel by viewModels<BackupExportViewModel>()
  private val binding by viewBinding(FragmentBackupExportBinding::bind)

  private var selectedSchedule: BackupExportSchedule? = null

  private val createFileContract =
    registerForActivityResult(CreateDocument("application/json")) { uri ->
      uri?.let {
        viewModel.runOneOffExport(uri)
      }
    }

  /**
   * For automatic backups we need access to a whole folder rather then just one file.
   */
  private val createFolderContract =
    registerForActivityResult(OpenDocumentTree()) { directoryUri ->
      directoryUri?.let {
        // Take persistable permissions so you can use this URI across app restarts.
        val permissionsFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        requireActivity().contentResolver.takePersistableUriPermission(directoryUri, permissionsFlags)
        selectedSchedule?.let {
          selectedSchedule = null
          viewModel.saveExportBackupSchedule(directoryUri, it)
        }
      }
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
      exportButton.onClick { createNewExport() }
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

  private fun createNewExport() {
    val fileName = BackupFileName.create()
    createFileContract.launch(fileName)
  }

  private fun saveNewExport(uri: Uri, content: String) {
    writeBackupJsonToFileUseCase(requireContext(), uri, content).fold(
      onSuccess = { /* NO-OP */ },
      onFailure = {
        showErrorSnack(it)
        Timber.e(it)
      }
    )
  }

  private fun validateExportFile(uri: Uri) {
    readBackupJsonFromFileUseCase(requireContext(), uri).fold(
      onSuccess = { json ->
        viewModel
          .validateExportData(json)
          .onSuccess {
            viewModel.onExportValidationSuccess()
            showShareSnack(uri)
          }
          .onFailure {
            showErrorSnack(it)
            Timber.e(it)
          }
      },
      onFailure = {
        showErrorSnack(it)
        Timber.e(it)
      }
    )
  }

  private fun shareNewExport(uri: Uri) {
    val intent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_STREAM, uri)
      type = "application/json"
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context?.startActivity(Intent.createChooser(intent, "Share"))
  }

  private fun showShareSnack(uri: Uri) {
    val host = (requireActivity() as SnackbarHost).provideSnackbarLayout()
    snackbar = host.showInfoSnackbar(
      message = getString(R.string.textBackupExportSuccess),
      actionText = R.string.textShare,
      length = 10.seconds.inWholeMilliseconds.toInt(),
      action = { shareNewExport(uri) },
    )
  }

  private fun showErrorSnack(error: Throwable) {
    val host = (requireActivity() as SnackbarHost).provideSnackbarLayout()
    snackbar = host.showErrorSnackbar(
      message = error.localizedMessage ?: getString(R.string.errorGeneral),
    )
  }

  override fun onDestroyView() {
    snackbar?.dismiss()
    super.onDestroyView()
  }

  private fun render(uiState: BackupExportUiState) {
    uiState.run {
      with(binding) {
        progressBar.visibleIf(isLoading)
        statusText.visibleIf(isLoading)
        exportButton.visibleIf(!isLoading, gone = false)
        exportButton.isEnabled = !isLoading
        exportScheduleButton.visibleIf(!isLoading, gone = false)
        exportScheduleButton.isEnabled = !isLoading
        exportScheduleButton.setText(backupExportSchedule.buttonStringRes)
        exportScheduleButton.onClick { showScheduleDialog(backupExportSchedule) }
        lastExportTimestamp.visibleIf(!isLoading && lastBackupExportTimestamp != 0L)
        if (lastBackupExportTimestamp != 0L) {
          val date = dateFormat?.format(dateFromMillis(lastBackupExportTimestamp).toLocalZone())?.capitalizeWords()
          lastExportTimestamp.text = getString(R.string.textBackupExportLastTimestamp, date)
        }
      }
      exportContent?.let {
        saveNewExport(it.exportUri, it.exportContent)
        validateExportFile(it.exportUri)
        viewModel.clearOneOffState()
      }
      if (error != null) {
        showErrorSnack(error)
        viewModel.clearOneOffState()
      }
    }
  }

  /**
   * Displays a dialog to select the backup export schedule.
   *
   * @param currentSchedule The currently selected backup export schedule.
   */
  private fun showScheduleDialog(currentSchedule: BackupExportSchedule) {
    val options = BackupExportSchedule.entries
    val optionsStrings = options.map { getString(it.stringRes) }.toTypedArray()
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setSingleChoiceItems(optionsStrings, options.indexOf(currentSchedule)) { dialog, index ->
        selectedSchedule = options[index]
        createFolderContract.launch(null)
        showSnack(MessageEvent.Info(currentSchedule.confirmationStringRes))
        dialog.dismiss()
      }.show()
  }
}
