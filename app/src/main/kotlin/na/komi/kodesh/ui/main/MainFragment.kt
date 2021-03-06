package na.komi.kodesh.ui.main


import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import na.komi.kodesh.Prefs
import na.komi.kodesh.R
import na.komi.kodesh.model.Bible
import na.komi.kodesh.ui.find.FindInPageFragment
import na.komi.kodesh.ui.internal.BaseFragment2
import na.komi.kodesh.ui.navigate.NavigateDialogFragment
import na.komi.kodesh.ui.styling.StylingDialogFragment
import na.komi.kodesh.ui.widget.ViewPager3
import na.komi.kodesh.util.betterSmoothScrollToPosition
import na.komi.kodesh.util.closestKatana
import na.komi.kodesh.util.livedata.raw
import na.komi.kodesh.util.livedata.toSingleEvent
import na.komi.kodesh.util.onClick
import na.komi.kodesh.util.setLowProfileStatusBar
import na.komi.kodesh.util.text.futureSet
import na.komi.skate.core.extension.hide
import org.rewedigital.katana.Component
import org.rewedigital.katana.KatanaTrait
import org.rewedigital.katana.androidx.viewmodel.activityViewModel

class MainFragment : BaseFragment2(), KatanaTrait {
    override val layout: Int = R.layout.fragment_main

    private val appName by lazy { getString(R.string.app_name) }

    override val component: Component by closestKatana()

    private val viewModel: MainViewModel by activityViewModel()

    var init = false
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden)
            init = true
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupRecyclerView(view!!)
        launch {
            val p = Prefs.VP_Position
            while ((view?.findViewById<ViewPager3>(R.id.pager_main)?.adapter as? MainPageAdapter)?.currentList?.get(p) == null) delay(
                1
            )
            //if (viewModel.mBundleRecyclerViewState == null) //getToolbar()?.title = ""
            if (this@MainFragment.isVisible)
                getToolbar()?.post {
                    setTitle(Prefs.VP_Position)
                    init = true
                }
            getToolbar()?.post {
                getToolbar()?.menu?.findItem(R.id.styling)?.setOnMenuItemClickListener {
                    openStylingDialog()
                    true
                }

                getToolbarTitleView()?.onClick {
                    openNavDialog()
                }
            }

        }
    }

    private fun setupRecyclerView(view: View) {
        val rv = view.findViewById(R.id.pager_main) as ViewPager3
        rv.isNestedScrollingEnabled = true
        (rv.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        rv.itemAnimator = null
        val adapter = MainPageAdapter(viewModel, coroutineContext)
        viewModel.pagesList.toSingleEvent().observe(viewLifecycleOwner, Observer<PagedList<Bible>?> { pagedList ->
            pagedList?.let {
                adapter.submitList(pagedList)
                val p = Prefs.VP_Position
                viewModel.mBundleRecyclerViewState?.let {
                    rv.layoutManager?.onRestoreInstanceState(it.getParcelable(viewModel.KEY_RECYCLER_STATE))
                }
                rv.scrollToPosition(p)
            }
        })

        rv.registerOnPageChangeCallback(object : ViewPager3.OnPageChangeCallback {

            /*override fun onPageScrollStateChanged(state: Int) {
                val vh = rv.findViewHolderForAdapterPosition(Prefs.VP_Position) as? MainPageAdapter.ViewHolder
                vh?.childRecyclerView?.isVerticalScrollBarEnabled = state == RecyclerView.SCROLL_STATE_IDLE
            }*/
            override fun onPageScrolled(pagesState: List<ViewPager3.VisiblePageState>) {
                for (pageState in pagesState) {
                    val vh = rv.findContainingViewHolder(pageState.view) as MainPageAdapter.ViewHolder
                    //d {"pageState.distanceToSettled ${pageState.distanceToSettled}"}

                    // Set status bar on horizontal scroll
                    if (!viewModel.currentLowProfileFlag.raw) {
                        (activity as? MainActivity)?.setLowProfileStatusBar()
                        viewModel.currentLowProfileFlag.value = true
                    }


                    if (pageState.distanceToSettled == 1.0f) {
                        if (!requireActivity().isChangingConfigurations)
                            requireActivity().supportFragmentManager.fragments.lastOrNull {
                                it.javaClass == FindInPageFragment::class.java
                            }?.let {
                                it.hide()
                            }
                    }
                    vh.childRecyclerView.isVerticalScrollBarEnabled = pageState.distanceToSettled == 1.0f

                    //     vh.setRealtimeAttr(pageState.index, pageState.viewCenterX.toString(), pageState.distanceToSettled, pageState.distanceToSettledPixels)
                }
            }

            override fun onPageSelected(position: Int) {
                if (init)
                    setTitle(position)
                Prefs.VP_Position = position
                currentIndex = position

            }
        })
        rv.adapter = adapter

        // Lots of observers slowdown app launch
        subscribeUI(adapter)
    }

    private fun subscribeUI(adapter: MainPageAdapter) {
        navigationDialog.setOnNavigateListener {
            onPositiveClick {
                navigateToPage()
                closeBottomSheet()
            }
        }


        activity?.window?.decorView?.setOnSystemUiVisibilityChangeListener {
            if ((it and View.SYSTEM_UI_FLAG_LOW_PROFILE) == 0) {
                //d { "Status bar is visible" }
                @Suppress("RedundantIf")
                viewModel.currentLowProfileFlag.value = false
            } else {
                //d { "Status bar is NOT visible" }
                viewModel.currentLowProfileFlag.value = true
            }
        }

        viewModel.adapterUpdate.observe(viewLifecycleOwner, Observer {
            viewModel.fromAdapterNotify = true
            adapter.notifyDataSetChanged()
        })
    }

    private fun setTitle(position: Int) {

        val item =
            (view?.findViewById<ViewPager3>(R.id.pager_main)?.adapter as? MainPageAdapter)?.currentList?.get(position)
        item?.let {
            getToolbar()?.title = "${it.bookName} ${it.chapterId}"
            getToolbarTitleView()?.futureSet("${it.bookName} ${it.chapterId}")
            Prefs.title = "${it.bookName} ${it.chapterId}"
        } ?: {
            getToolbar()?.title = appName
            getToolbarTitleView()?.text = appName
        }()

    }

    override fun onPause() {
        super.onPause()
        val rv = view?.findViewById<ViewPager3>(R.id.pager_main)
        if (viewModel.mBundleRecyclerViewState == null)
            viewModel.mBundleRecyclerViewState = Bundle()
        viewModel.mBundleRecyclerViewState?.apply {
            clear()
            putParcelable(viewModel.KEY_RECYCLER_STATE, rv?.layoutManager?.onSaveInstanceState())
        }
        viewModel.MainRecyclerViewState = rv?.layoutManager?.onSaveInstanceState()
    }

    /**
     * Gets the first line that is visible on the screen.
     *
     * @return
     */
    private fun AppCompatTextView.getFirstVisibleLine(scrollY: Int): Int? = layout?.getLineForVertical(scrollY)

    /**
     * Gets the first line index that is visible on the screen.
     *
     * @return
     */
    private fun AppCompatTextView.getFirstLineIndex(scrollY: Int): Int? =
        getFirstVisibleLine(scrollY)?.let { layout?.getLineStart(it) }

    private fun navigateToPage() {

        if (Prefs.NavigateToPosition == -1) return

        val bp = Prefs.VP_Position
        val p = Prefs.NavigateToPosition
        Prefs.VP_Position = p
        val rv = view?.findViewById<ViewPager3>(R.id.pager_main)
        if (bp != p) {
            launch {
                rv?.visibility = View.INVISIBLE
                rv?.scrollToPosition(p)
                var c = 0
                while ((rv?.findViewHolderForAdapterPosition(p) as? MainPageAdapter.ViewHolder)?.childRecyclerView == null) {
                    delay(10)
                    c++
                    if (c > 5000)
                        break
                }
                delay(230)
                rv?.visibility = View.VISIBLE
                (rv?.findViewHolderForAdapterPosition(p) as? MainPageAdapter.ViewHolder)?.childRecyclerView?.betterSmoothScrollToPosition(
                    viewModel.versePicked - 1
                )
                setTitle(p)
            }
        } else if (bp == p)
            (rv?.findViewHolderForAdapterPosition(p) as? MainPageAdapter.ViewHolder)?.childRecyclerView?.betterSmoothScrollToPosition(
                viewModel.versePicked - 1
            )
        Prefs.NavigateToPosition = -1
    }

    private var currentIndex: Int = 0

    private val navigationDialog by lazy { NavigateDialogFragment() }

    private val stylingDialog by lazy { StylingDialogFragment() }

    fun openNavDialog() {
        navigationDialog.show(childFragmentManager, navigationDialog.javaClass.simpleName)
    }

    fun openStylingDialog() {
        stylingDialog.show(childFragmentManager, stylingDialog.javaClass.simpleName)
    }

}