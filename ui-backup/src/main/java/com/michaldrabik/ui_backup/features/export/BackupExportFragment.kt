package com.michaldrabik.ui_backup.features.export

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toLocalZone
import com.michaldrabik.ui_backup.R
import com.michaldrabik.ui_backup.databinding.FragmentBackupExportBinding
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.SnackbarHost
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.showErrorSnackbar
import com.michaldrabik.ui_base.utilities.extensions.showInfoSnackbar
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class BackupExportFragment : BaseFragment<BackupExportViewModel>(R.layout.fragment_backup_export) {

  override val viewModel by viewModels<BackupExportViewModel>()
  private val binding by viewBinding(FragmentBackupExportBinding::bind)

  private val createFileContract =
    registerForActivityResult(CreateDocument("application/json")) { uri ->
      uri?.let {
        viewModel.runExport(uri)
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
    val dateFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    val currentDate = nowUtc().toLocalZone()

    createFileContract.launch("showly_export_${dateFormat.format(currentDate)}.json")
  }

  private fun saveNewExport(
    uri: Uri,
    content: String,
  ) {
    context?.contentResolver?.openOutputStream(uri, "wt")?.use { stream ->
      stream.write(content.trim().toByteArray())
    }
  }

  private fun validateExportFile(uri: Uri) {
    var inputStream: InputStream? = null
    var reader: BufferedReader? = null

    try {
      inputStream = requireContext().contentResolver.openInputStream(uri)
      reader = BufferedReader(InputStreamReader(inputStream))

      val stringBuilder = StringBuilder()
      var line: String?

      while (reader.readLine().also { line = it } != null) {
        stringBuilder.append(line)
      }

      val jsonInput = stringBuilder.toString()
      viewModel
        .validateExportData(jsonInput)
        .onSuccess { showShareSnack(uri) }
        .onFailure {
          showErrorSnack(it)
          Timber.e(it)
        }
    } catch (error: Throwable) {
      showErrorSnack(error)
      Timber.e(error)
    } finally {
      inputStream?.close()
      reader?.close()
    }
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
      }
      exportContent?.let {
        saveNewExport(it.exportUri, it.exportContent)
        validateExportFile(it.exportUri)
        viewModel.clearState()
      }
      if (error != null) {
        showErrorSnack(error)
        viewModel.clearState()
      }
    }
  }
}
