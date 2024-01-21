package de.davis.passwordmanager.ui.dashboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import androidx.slidingpanelayout.widget.SlidingPaneLayout.PanelSlideListener
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.dtos.Item
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.dtos.TagWithCount
import de.davis.passwordmanager.databinding.FragmentDashboardBinding
import de.davis.passwordmanager.filter.Filter
import de.davis.passwordmanager.ktx.doFlowInLifecycle
import de.davis.passwordmanager.ktx.getParcelableCompat
import de.davis.passwordmanager.manager.ActivityResultManager
import de.davis.passwordmanager.ui.callbacks.SearchViewBackPressedHandler
import de.davis.passwordmanager.ui.callbacks.SlidingBackPaneManager
import de.davis.passwordmanager.ui.dashboard.managers.AbsItemManager
import de.davis.passwordmanager.ui.dashboard.managers.ElementItemManager
import de.davis.passwordmanager.ui.dashboard.managers.TagItemManager
import de.davis.passwordmanager.ui.dashboard.menuprovider.DefaultElementMenuProvider
import de.davis.passwordmanager.ui.viewmodels.ScrollingViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.parcelize.Parcelize

class DashboardFragment : Fragment() {

    private lateinit var binding: FragmentDashboardBinding

    private val adapter = DashboardAdapter { updateUI() }

    private val scrollingViewModel: ScrollingViewModel by activityViewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()

    private lateinit var navController: NavController

    private lateinit var dashboardHandler: DashboardHandler

    private val onElementClicked = { e: SecureElement -> dashboardHandler.onElementClicked(e) }

    @Parcelize
    data class DashboardHandler(
        val hideElementTypes: List<ElementType> = emptyList(),
        val onElementClicked: (SecureElement) -> Unit
    ) : Parcelable

    private val defaultHandler = DashboardHandler { launchElement(it) }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dashboardHandler = arguments?.getParcelableCompat(
            EXTRA_DASHBOARD_HANDLER,
            DashboardHandler::class.java
        ) ?: defaultHandler
    }

    @SuppressLint("PrivateResource")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dashboardViewModel.initiateState(dashboardHandler.hideElementTypes)

        binding.root.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

        val menuProvider = DefaultElementMenuProvider(parentFragmentManager, adapter)
        requireActivity().addMenuProvider(
            menuProvider,
            viewLifecycleOwner,
            Lifecycle.State.STARTED
        )

        adapter.applyRecyclerView(
            binding.listPane.recyclerView,
            enableSelection = dashboardHandler == defaultHandler
        ) {
            binding.listPane.searchBar.hint = if (it.isNotEmpty())
                getString(R.string.selected_items, it.size)
            else
                getString(android.R.string.search_go)

            menuProvider.updateMenu(requireActivity()) {
                selectedElements = it
            }
        }

        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.elementContainer) as NavHostFragment
        navController = navHostFragment.navController

        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.listPane.searchBar)


        val backToTagsCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                dashboardViewModel.updateState(ListState.Tag)
            }
        }

        val slidingBackPaneManager =
            SlidingBackPaneManager(binding.slidingPaneLayout, scrollingViewModel)

        binding.slidingPaneLayout.addPanelSlideListener(object : PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) {}

            override fun onPanelOpened(panel: View) {
                backToTagsCallback.isEnabled = false
            }

            override fun onPanelClosed(panel: View) {
                backToTagsCallback.isEnabled = true
            }

        })

        requireActivity().onBackPressedDispatcher.apply {
            addCallback(
                viewLifecycleOwner,
                slidingBackPaneManager
            )

            addCallback(
                viewLifecycleOwner,
                SearchViewBackPressedHandler(binding.listPane.searchView)
            )

            addCallback(viewLifecycleOwner, backToTagsCallback)
        }

        // Animation for fab and bottom nav bar
        binding.listPane.recyclerView.addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                scrollingViewModel.setConsumedY(dy)
            }
        })
        // ------------------------------------

        ActivityResultManager.getOrCreateManager(javaClass, this).apply {
            registerCreate()
            registerEdit {}
        }

        viewLifecycleOwner.doFlowInLifecycle(dashboardViewModel.state) {
            collectLatest { pair ->
                @Suppress("UNCHECKED_CAST")
                pair?.let { (listState, data) ->
                    // handle menu
                    menuProvider.updateMenu(requireActivity()) {
                        filterVisible = listState !is ListState.Tag
                    }
                    // -----------

                    val absItemManager = when (listState) {
                        is ListState.Tag -> TagItemManager(
                            data.data as List<TagWithCount>,
                            onClick = {
                                dashboardViewModel.updateState(ListState.Element(it.tag.name))
                            }
                        )

                        // Element and AllElements
                        else -> {
                            val elements = data.data as List<SecureElement>
                            if (data is ListData.Elements) {
                                Filter.updateTags(data.tags)
                            }

                            ElementItemManager(
                                elements,
                                onElementClicked,
                                childFragmentManager
                            )
                        }
                    }

                    update(absItemManager)

                    // Update icon
                    backToTagsCallback.isEnabled =
                        listState is ListState.Element && !binding.slidingPaneLayout.isOpen

                    if (backToTagsCallback.isEnabled) {
                        binding.listPane.searchBar.navigationIcon =
                            AppCompatResources.getDrawable(
                                requireContext(),
                                R.drawable.ic_baseline_close_24
                            )

                        binding.listPane.searchBar.setNavigationOnClickListener {
                            dashboardViewModel.updateState(ListState.Tag)
                        }
                    } else {
                        binding.listPane.searchBar.navigationIcon =
                            AppCompatResources.getDrawable(
                                requireContext(),
                                com.google.android.material.R.drawable.ic_search_black_24
                            )

                        binding.listPane.searchBar.setNavigationOnClickListener(null)
                    }
                }
            }
        }

        binding.listPane.searchView.editText.doAfterTextChanged {
            dashboardViewModel.search(it.toString())
        }
        val searchResultAdapter = DashboardAdapter {}
        searchResultAdapter.applyRecyclerView(binding.listPane.recyclerViewResults, false)

        doFlowInLifecycle(dashboardViewModel.searchResults) {
            collectLatest {
                searchResultAdapter.update(
                    ElementItemManager(
                        it.second,
                        onElementClicked,
                        childFragmentManager
                    )
                )
                searchResultAdapter.filter = it.first

                binding.listPane.noResultsSearch.visibility =
                    if (it.first.isNotEmpty() && it.second.isEmpty()) View.VISIBLE
                    else View.GONE
            }
        }


        arguments?.getParcelableCompat("element", SecureElement::class.java)?.let {
            onElementClicked(it)
        }
    }

    private fun launchElement(element: SecureElement) {
        scrollingViewModel.setFabVisibility(false)
        binding.listPane.searchView.hide()
        val bundle = bundleOf("element" to element)
        navController.apply {
            popBackStack()
            navigate(element.elementType.viewFragmentId, bundle)
        }

        binding.root.open()
    }

    override fun onPause() {
        super.onPause()
        scrollingViewModel.setFabVisibility(false)
    }

    override fun onResume() {
        super.onResume()
        scrollingViewModel.setFabVisibility(true)
    }

    private fun update(update: AbsItemManager<out Item>) {
        adapter.update(update)

        updateUI()
    }

    private fun updateUI() {
        binding.listPane.progress.visibility = View.GONE

        binding.listPane.noResults.visibility =
            if (adapter.itemCount > 0) View.GONE else View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        adapter.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        adapter.onRestoreInstanceState(savedInstanceState)
    }

    companion object {
        const val EXTRA_DASHBOARD_HANDLER = "de.davis.passwordmanager.extra.DASHBOARD_HANDLER"
    }
}