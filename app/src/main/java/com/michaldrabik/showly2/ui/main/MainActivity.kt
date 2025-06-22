package com.michaldrabik.showly2.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.DecelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michaldrabik.common.Config
import com.michaldrabik.common.Mode
import com.michaldrabik.common.Mode.MOVIES
import com.michaldrabik.common.Mode.SHOWS
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.showly2.R
import com.michaldrabik.showly2.databinding.ActivityMainBinding
import com.michaldrabik.showly2.ui.BaseActivity
import com.michaldrabik.showly2.ui.main.delegates.MainTipsDelegate
import com.michaldrabik.showly2.ui.main.delegates.TipsDelegate
import com.michaldrabik.showly2.ui.views.WhatsNewView
import com.michaldrabik.showly2.utilities.deeplink.DeepLinkResolver
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.common.OnShowsMoviesSyncedListener
import com.michaldrabik.ui_base.common.OnTabReselectedListener
import com.michaldrabik.ui_base.events.Event
import com.michaldrabik.ui_base.events.EventsManager
import com.michaldrabik.ui_base.events.ShowsMoviesSyncComplete
import com.michaldrabik.ui_base.events.TraktQuickSyncSuccess
import com.michaldrabik.ui_base.events.TraktSyncAuthError
import com.michaldrabik.ui_base.network.NetworkStatusProvider
import com.michaldrabik.ui_base.sync.ShowsMoviesSyncWorker
import com.michaldrabik.ui_base.utilities.ModeHost
import com.michaldrabik.ui_base.utilities.MoviesStatusHost
import com.michaldrabik.ui_base.utilities.NavigationHost
import com.michaldrabik.ui_base.utilities.SnackbarHost
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.fadeIn
import com.michaldrabik.ui_base.utilities.extensions.fadeOut
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.openWebUrl
import com.michaldrabik.ui_base.utilities.extensions.showErrorSnackbar
import com.michaldrabik.ui_base.utilities.extensions.showInfoSnackbar
import com.michaldrabik.ui_base.utilities.extensions.visible
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_settings.helpers.AppLanguage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class MainActivity :
  BaseActivity(),
  SnackbarHost,
  NavigationHost,
  ModeHost,
  MoviesStatusHost,
  TipsDelegate by MainTipsDelegate() {

  companion object {
    private const val NAVIGATION_TRANSITION_DURATION_MS = 250L
    private const val ARG_NAVIGATION_VISIBLE = "ARG_NAVIGATION_VISIBLE"
  }

  private val viewModel by viewModels<MainViewModel>()
  private lateinit var binding: ActivityMainBinding

  private val navigationHeight by lazy { dimenToPx(R.dimen.bottomNavigationHeight) }
  private val navigationPadding by lazy { dimenToPx(R.dimen.spaceMedium) }
  private val decelerateInterpolator by lazy { DecelerateInterpolator(2F) }

  @Inject lateinit var workManager: WorkManager
  @Inject lateinit var eventsManager: EventsManager
  @Inject lateinit var deepLinkResolver: DeepLinkResolver
  @Inject lateinit var settingsRepository: SettingsRepository
  @Inject lateinit var networkStatusProvider: NetworkStatusProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(ContextCompat.getColor(this, R.color.colorBlackTranslucentMedium)),
    )

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    registerTipsDelegate(viewModel, binding)

    setupViewModel()
    setupNavigation()
    setupView()
    setupNetworkObserver()

    restoreState(savedInstanceState)
    onNewIntent(intent)
  }

  override fun onStart() {
    super.onStart()
    ShowsMoviesSyncWorker.schedule(workManager)
  }

  override fun onResume() {
    super.onResume()
    setupBackPressed()
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    handleAppShortcut(intent)
    handleNotification(intent?.extras) { hideNavigation(false) }
    handleTraktAuthorization(intent?.data)
    handleDeepLink(intent)
  }

  override fun onDestroy() {
    lifecycle.removeObserver(networkStatusProvider)
    super.onDestroy()
  }

  private fun setupViewModel() {
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.uiState.collect { render(it) } }
        launch { eventsManager.events.collect { handleEvent(it) } }
      }
    }
    viewModel.initialize()
    viewModel.refreshTraktSyncSchedule()
    viewModel.refreshBackupExportSchedule()
  }

  private fun setupView() {
    with(binding) {
      bottomNavigationWrapper.doOnApplyWindowInsets { _, insets, _, margins ->
        val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        bottomNavigationWrapper.updateLayoutParams<MarginLayoutParams> {
          bottomMargin = margins.bottom + inset
        }
      }
      bottomMenuView.isModeMenuEnabled = hasMoviesEnabled()
      bottomMenuView.onModeSelected = { setMode(it) }
      viewMask.onClick { /* NOOP */ }
    }
  }

  @OptIn(FlowPreview::class)
  private fun setupNetworkObserver() {
    lifecycle.addObserver(networkStatusProvider)
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          networkStatusProvider.status
            .debounce(3.seconds)
            .collect {
              binding.statusView.visibleIf(!it)
              binding.statusView.text = getString(R.string.errorNoInternetConnection)
            }
        }
      }
    }
  }

  private fun setupNavigation() {
    findNavControl()?.run {
      val graph = navInflater.inflate(R.navigation.navigation_graph).apply {
        val destination = when (viewModel.getMode()) {
          SHOWS -> R.id.progressMainFragment
          MOVIES -> R.id.progressMoviesMainFragment
          else -> throw IllegalStateException()
        }
        setStartDestination(destination)
      }
      setGraph(graph, Bundle.EMPTY)
    }
    with(binding.bottomMenuView.binding.bottomNavigationView) {
      setOnItemSelectedListener { item ->
        if (selectedItemId == item.itemId) {
          doForFragments { (it as? OnTabReselectedListener)?.onTabReselected() }
          return@setOnItemSelectedListener true
        }

        val target = when (item.itemId) {
          R.id.menuProgress -> getMenuProgressAction()
          R.id.menuDiscover -> getMenuDiscoverAction()
          R.id.menuCollection -> getMenuCollectionAction()
          else -> throw IllegalStateException("Invalid menu item.")
        }

        findNavControl()?.navigate(target)
        showNavigation(true)

        return@setOnItemSelectedListener true
      }
    }
  }

  private fun setupBackPressed() {
    with(binding) {
      onBackPressedDispatcher.addCallback(this@MainActivity) {
        if (tutorialView.isVisible) {
          tutorialView.fadeOut()
          return@addCallback
        }
        findNavControl()?.run {
          when (currentDestination?.id) {
            R.id.discoverFragment,
            R.id.discoverMoviesFragment,
            R.id.followedShowsFragment,
            R.id.followedMoviesFragment,
            R.id.listsFragment,
            -> {
              bottomMenuView.binding.bottomNavigationView.selectedItemId = R.id.menuProgress
            }
            else -> {
              remove()
              super.onBackPressed()
            }
          }
        }
      }
    }
  }

  override fun hideNavigation(animate: Boolean) {
    with(binding) {
      hideAllTips()
      bottomMenuView.isEnabled = false
      snackbarHost.translationY = 0F
      bottomNavigationWrapper
        .animate()
        .alpha(0F)
        .translationYBy(navigationHeight.toFloat() / 2)
        .setDuration(if (animate) NAVIGATION_TRANSITION_DURATION_MS else 0)
        .setInterpolator(decelerateInterpolator)
        .withEndAction { bottomMenuView.gone() }
        .start()
    }
  }

  override fun showNavigation(animate: Boolean) {
    with(binding) {
      showAllTips()
      bottomMenuView.visible()
      bottomMenuView.isEnabled = true
      snackbarHost.translationY = -(navigationHeight + (navigationPadding.toFloat()))
      bottomNavigationWrapper
        .animate()
        .alpha(1F)
        .translationY(0F)
        .setDuration(if (animate) NAVIGATION_TRANSITION_DURATION_MS else 0)
        .setInterpolator(decelerateInterpolator)
        .start()
    }
  }

  override fun navigateToDiscover() {
    with(binding) {
      bottomMenuView.isEnabled = true
      bottomMenuView.binding.bottomNavigationView.selectedItemId = R.id.menuDiscover
    }
  }

  override fun setMode(
    mode: Mode,
    force: Boolean,
  ) {
    if (force || viewModel.getMode() != mode) {
      viewModel.setMode(mode)
      val target = when (binding.bottomMenuView.binding.bottomNavigationView.selectedItemId) {
        R.id.menuDiscover -> getMenuDiscoverAction()
        R.id.menuCollection -> getMenuCollectionAction()
        R.id.menuProgress -> getMenuProgressAction()
        else -> 0
      }
      if (target != 0) {
        findNavControl()?.navigate(target)
      }
    }
  }

  override fun getMode() = viewModel.getMode()

  override fun hasMoviesEnabled() = viewModel.hasMoviesEnabled()

  private fun render(uiState: MainUiState) {
    with(binding) {
      uiState.run {
        isLoading.let {
          mainProgress.visibleIf(it)
        }
        showMask.let {
          viewMask.visibleIf(it)
        }
        isInitialRun?.let {
          if (it.consume() == true) {
            viewModel.checkInitialLanguage()
          }
        }
        showWhatsNew?.let {
          if (it.consume() == true) showWhatsNewDialog()
        }
        initialLanguage?.let { event ->
          event.consume()?.let {
            showWelcomeDialog(it)
          }
        }
        openLink?.let { event ->
          event.consume()?.let { bundle ->
            findNavHostFragment()?.findNavController()?.let { nav ->
              bundle.show?.let {
                deepLinkResolver.resolveDestination(nav, bottomMenuView.binding.bottomNavigationView, it)
              }
              bundle.movie?.let {
                deepLinkResolver.resolveDestination(nav, bottomMenuView.binding.bottomNavigationView, it)
              }
            }
          }
        }
      }
    }
  }

  private fun showWelcomeDialog(language: AppLanguage) {
    navigateToDiscover()
    with(binding.welcomeView) {
      setLanguage(language)
      fadeIn()
      onOkClickListener = {
        fadeOut()
        showMask(false)
        if (language != AppLanguage.ENGLISH) {
          showWelcomeLanguageDialog(language)
        }
      }
    }
    showMask(true)
  }

  private fun showWelcomeLanguageDialog(language: AppLanguage) {
    with(binding.welcomeLanguageView) {
      setLanguage(language)
      fadeIn()
      onYesClick = {
        viewModel.setLanguage(language)
        fadeOut()
        showMask(false)
      }
      onNoClick = {
        viewModel.setLanguage(AppLanguage.ENGLISH)
        fadeOut()
        showMask(false)
      }
    }
    showMask(true)
  }

  private fun showMask(show: Boolean) {
    binding.viewMask.visibleIf(show)
    if (!show) viewModel.clearMask()
  }

  @SuppressLint("MissingSuperCall")
  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean(ARG_NAVIGATION_VISIBLE, binding.bottomNavigationWrapper.translationY == 0F)
    super.onSaveInstanceState(outState)
  }

  private fun restoreState(savedInstanceState: Bundle?) {
    val isNavigationVisible = savedInstanceState?.getBoolean(ARG_NAVIGATION_VISIBLE, true) ?: true
    if (!isNavigationVisible) hideNavigation(true)
  }

  private fun doForFragments(action: (Fragment) -> Unit) {
    findNavControl()?.currentDestination?.id?.let {
      val navHost = supportFragmentManager.findFragmentById(R.id.navigationHost)
      navHost?.childFragmentManager?.primaryNavigationFragment?.let { action(it) }
    }
  }

  private fun handleEvent(event: Event) {
    when (event) {
      is ShowsMoviesSyncComplete -> {
        if (event.count > 0) {
          doForFragments { (it as? OnShowsMoviesSyncedListener)?.onShowsMoviesSyncFinished() }
        }
        viewModel.refreshAnnouncements()
      }
      is TraktQuickSyncSuccess -> {
        val message = resources.getQuantityString(R.plurals.textTraktQuickSyncComplete, event.count, event.count)
        provideSnackbarLayout().showInfoSnackbar(message)
      }
      is TraktSyncAuthError -> {
        provideSnackbarLayout().showErrorSnackbar(getString(R.string.errorTraktAuthorization))
      }
      else -> Timber.d("Event ignored. Noop.")
    }
  }

  private fun handleAppShortcut(intent: Intent?) {
    when {
      intent == null -> return

      intent.extras?.containsKey("extraShortcutProgress") == true ->
        binding.bottomMenuView.binding.bottomNavigationView.selectedItemId = R.id.menuProgress

      intent.extras?.containsKey("extraShortcutDiscover") == true ->
        binding.bottomMenuView.binding.bottomNavigationView.selectedItemId = R.id.menuDiscover

      intent.extras?.containsKey("extraShortcutCollection") == true ->
        binding.bottomMenuView.binding.bottomNavigationView.selectedItemId = R.id.menuCollection

      intent.extras?.containsKey("extraShortcutSearch") == true -> {
        binding.bottomMenuView.binding.bottomNavigationView.selectedItemId = R.id.menuDiscover
        val action = when (viewModel.getMode()) {
          SHOWS -> R.id.actionDiscoverFragmentToSearchFragment
          MOVIES -> R.id.actionDiscoverMoviesFragmentToSearchFragment
          else -> throw IllegalStateException()
        }
        findNavControl()?.navigate(action)
      }
    }
  }

  override fun handleSearchWidgetClick(bundle: Bundle?) {
    findNavHostFragment()?.findNavController()?.run {
      try {
        when (currentDestination?.id) {
          R.id.searchFragment -> return@run
          R.id.showDetailsFragment, R.id.movieDetailsFragment -> navigateUp()
        }
        if (currentDestination?.id != R.id.discoverFragment) {
          binding.bottomMenuView.binding.bottomNavigationView.selectedItemId = R.id.menuDiscover
        }
        when (currentDestination?.id) {
          R.id.discoverFragment -> navigate(R.id.actionDiscoverFragmentToSearchFragment)
          R.id.discoverMoviesFragment -> navigate(R.id.actionDiscoverMoviesFragmentToSearchFragment)
        }
        bundle?.clear()
      } catch (error: Throwable) {
        Logger.record(error, "BaseActivity::handleSearchWidgetClick()")
      }
    }
  }

  private fun showWhatsNewDialog() {
    MaterialAlertDialogBuilder(
      this,
      R.style.AlertDialog,
    ).setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog))
      .setView(WhatsNewView(this))
      .setCancelable(false)
      .setPositiveButton(R.string.textClose) { _, _ -> }
      .setNeutralButton("Twitter") { _, _ -> openWebUrl(Config.TWITTER_URL) }
      .show()
  }

  private fun getMenuDiscoverAction() =
    when (viewModel.getMode()) {
      SHOWS -> R.id.actionNavigateDiscoverFragment
      MOVIES -> R.id.actionNavigateDiscoverMoviesFragment
      else -> throw IllegalStateException()
    }

  private fun getMenuCollectionAction() =
    when (viewModel.getMode()) {
      SHOWS -> R.id.actionNavigateFollowedShowsFragment
      MOVIES -> R.id.actionNavigateFollowedMoviesFragment
      else -> throw IllegalStateException()
    }

  private fun getMenuProgressAction() =
    when (viewModel.getMode()) {
      SHOWS -> R.id.actionNavigateProgressFragment
      MOVIES -> R.id.actionNavigateProgressMoviesFragment
      else -> throw IllegalStateException()
    }

  private fun handleDeepLink(intent: Intent?) {
    deepLinkResolver.findSource(intent)?.let {
      viewModel.openDeepLink(it)
    }
  }

  override fun findNavControl() = findNavHostFragment()?.findNavController()

  override fun provideSnackbarLayout(): ViewGroup = binding.snackbarHost
}
