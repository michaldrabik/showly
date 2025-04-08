package com.michaldrabik.ui_trakt_sync

import BaseMockTest
import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.michaldrabik.repository.UserTraktManager
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.R.string
import com.michaldrabik.ui_base.dates.DateFormatProvider
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.TraktSyncAuthError
import com.michaldrabik.ui_base.events.TraktSyncError
import com.michaldrabik.ui_base.events.TraktSyncProgress
import com.michaldrabik.ui_base.events.TraktSyncStart
import com.michaldrabik.ui_base.events.TraktSyncSuccess
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_model.Settings
import com.michaldrabik.ui_model.TraktSyncSchedule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("EXPERIMENTAL_API_USAGE")
class TraktSyncViewModelTest : BaseMockTest() {

  @RelaxedMockK lateinit var workManager: WorkManager
  @MockK lateinit var miscPreferences: SharedPreferences
  @MockK lateinit var userTraktManager: UserTraktManager
  @MockK lateinit var settingsRepository: SettingsRepository
  @MockK lateinit var eventsManager: EventsManager
  @MockK lateinit var dateFormatProvider: DateFormatProvider

  private lateinit var SUT: TraktSyncViewModel

  private val stateResult = mutableListOf<TraktSyncUiState>()
  private val messagesResult = mutableListOf<MessageEvent>()

  @Before
  override fun setUp() {
    super.setUp()

    mockkObject(Logger)
    coEvery { userTraktManager.revokeToken() } just Runs
    coEvery { eventsManager.events } returns MutableSharedFlow()
    coEvery { Logger.record(any(), any()) } just Runs

    SUT = TraktSyncViewModel(
      miscPreferences,
      userTraktManager,
      workManager,
      settingsRepository,
      dateFormatProvider,
      eventsManager,
    )
  }

  @After
  fun tearDown() {
    stateResult.clear()
    messagesResult.clear()
    SUT.viewModelScope.cancel()
  }

  @Test
  internal fun `Should invalidate properly`() =
    runTest {
      val job = launch(UnconfinedTestDispatcher()) { SUT.uiState.toList(stateResult) }
      val job2 = launch(UnconfinedTestDispatcher()) { SUT.messageFlow.toList(messagesResult) }

      coEvery { settingsRepository.load() } returns Settings.createInitial()
      coEvery { userTraktManager.isAuthorized() } returns true
      coEvery { dateFormatProvider.loadFullHourFormat() } returns DateTimeFormatter.ISO_DATE
      coEvery { miscPreferences.getLong(any(), 0) } returns 0

      SUT.invalidate()

      assertThat(stateResult.last().isAuthorized).isTrue()
      assertThat(stateResult.last().traktSyncSchedule).isEqualTo(TraktSyncSchedule.OFF)
      assertThat(stateResult.last().quickSyncEnabled).isEqualTo(false)
      assertThat(stateResult.last().dateFormat).isEqualTo(DateTimeFormatter.ISO_DATE)
      assertThat(stateResult.last().lastTraktSyncTimestamp).isEqualTo(0L)
      coVerify(exactly = 1) { settingsRepository.load() }

      job.cancel()
      job2.cancel()
    }

  @Test
  internal fun `Should not authorize trakt if URI is null`() =
    runTest {
      val job = launch(UnconfinedTestDispatcher()) { SUT.uiState.toList(stateResult) }
      val job2 = launch(UnconfinedTestDispatcher()) { SUT.messageFlow.toList(messagesResult) }

      SUT.authorizeTrakt(null)
      coVerify(exactly = 0) { userTraktManager.authorize(any()) }
      assertThat(messagesResult.last().consume()).isEqualTo(string.errorAuthorization)

      job.cancel()
      job2.cancel()
    }

  @Test
  internal fun `Should authorize trakt properly`() =
    runTest {
      val job = launch(UnconfinedTestDispatcher()) { SUT.uiState.toList(stateResult) }
      val job2 = launch(UnconfinedTestDispatcher()) { SUT.messageFlow.toList(messagesResult) }

      coEvery { settingsRepository.load() } returns Settings.createInitial()
      coEvery { settingsRepository.update(any()) } just Runs
      coEvery { userTraktManager.isAuthorized() } returns true
      coEvery { userTraktManager.authorize(any()) } just Runs
      coEvery { dateFormatProvider.loadFullHourFormat() } returns DateTimeFormatter.ISO_DATE
      coEvery { miscPreferences.getLong(any(), 0) } returns 0

      SUT.authorizeTrakt("testCode")

      assertThat(messagesResult.last().consume()).isEqualTo(R.string.textTraktLoginSuccess)

      job.cancel()
      job2.cancel()
    }

  @Test
  internal fun `Should save Trakt Sync Schedule`() =
    runTest {
      val job = launch(UnconfinedTestDispatcher()) { SUT.uiState.toList(stateResult) }
      val job2 = launch(UnconfinedTestDispatcher()) { SUT.messageFlow.toList(messagesResult) }

      coEvery { settingsRepository.load() } returns Settings.createInitial()
      coEvery { settingsRepository.update(any()) } just Runs

      val schedule = TraktSyncSchedule.EVERY_6_HOURS
      SUT.saveTraktSyncSchedule(schedule)

      assertThat(stateResult.last().traktSyncSchedule).isEqualTo(schedule)
      assertThat(messagesResult).isEmpty()
      coVerify(exactly = 1) { settingsRepository.load() }
      coVerify(exactly = 1) { settingsRepository.update(any()) }
      coVerify(exactly = 1) { workManager.cancelUniqueWork(any()) }
      coVerify(exactly = 1) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }

      job.cancel()
      job2.cancel()
    }

  @Test
  internal fun `Should handle TraktSyncStart event`() =
    runTest {
      val job = launch(UnconfinedTestDispatcher()) { SUT.uiState.toList(stateResult) }
      val job2 = launch(UnconfinedTestDispatcher()) { SUT.messageFlow.toList(messagesResult) }

      SUT.handleEvent(TraktSyncStart)

      assertThat(stateResult.last().isProgress).isTrue()
      assertThat(stateResult.last().progressStatus).isEqualTo("")
      assertThat(messagesResult.last().consume()).isEqualTo(R.string.textTraktSyncStarted)

      job.cancel()
      job2.cancel()
    }

  @Test
  internal fun `Should handle TraktSyncProgress event`() =
    runTest {
      val job = launch(UnconfinedTestDispatcher()) { SUT.uiState.toList(stateResult) }
      val job2 = launch(UnconfinedTestDispatcher()) { SUT.messageFlow.toList(messagesResult) }

      SUT.handleEvent(TraktSyncProgress("test"))

      assertThat(stateResult.last().isProgress).isTrue()
      assertThat(stateResult.last().progressStatus).isEqualTo("test")
      assertThat(messagesResult).isEmpty()

      job.cancel()
      job2.cancel()
    }

  @Test
  internal fun `Should handle TraktSyncSuccess event`() =
    runTest {
      val job = launch(UnconfinedTestDispatcher()) { SUT.uiState.toList(stateResult) }
      val job2 = launch(UnconfinedTestDispatcher()) { SUT.messageFlow.toList(messagesResult) }
      coEvery { miscPreferences.getLong(any(), 0) } returns 0

      SUT.handleEvent(TraktSyncSuccess)

      assertThat(stateResult.last().isProgress).isFalse()
      assertThat(stateResult.last().progressStatus).isEqualTo("")
      assertThat(messagesResult.last().consume()).isEqualTo(string.textTraktSyncComplete)

      job.cancel()
      job2.cancel()
    }

  @Test
  internal fun `Should handle TraktSyncError event`() =
    runTest {
      val job = launch(UnconfinedTestDispatcher()) { SUT.uiState.toList(stateResult) }
      val job2 = launch(UnconfinedTestDispatcher()) { SUT.messageFlow.toList(messagesResult) }

      SUT.handleEvent(TraktSyncError)

      assertThat(stateResult.last().isProgress).isFalse()
      assertThat(stateResult.last().progressStatus).isEqualTo("")
      assertThat(messagesResult.last().consume()).isEqualTo(string.textTraktSyncError)

      job.cancel()
      job2.cancel()
    }

  @Test
  internal fun `Should handle TraktSyncAuthError event`() =
    runTest {
      val job = launch(UnconfinedTestDispatcher()) { SUT.uiState.toList(stateResult) }
      val job2 = launch(UnconfinedTestDispatcher()) { SUT.messageFlow.toList(messagesResult) }

      SUT.handleEvent(TraktSyncAuthError)

      assertThat(stateResult.last().isProgress).isFalse()
      assertThat(stateResult.last().progressStatus).isEqualTo("")
      assertThat(messagesResult.last().consume()).isEqualTo(string.errorTraktAuthorization)

      job.cancel()
      job2.cancel()
    }
}
