package na.komi.kodesh.ui.internal

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import na.komi.kodesh.Application
import na.komi.kodesh.Prefs
import na.komi.kodesh.R
import na.komi.kodesh.ui.about.AboutFragment
import na.komi.kodesh.ui.find.FindInPageFragment
import na.komi.kodesh.ui.main.MainFragment
import na.komi.kodesh.ui.preface.PrefaceFragment
import na.komi.kodesh.ui.search.SearchFragment
import na.komi.kodesh.ui.setting.SettingsFragment
import na.komi.kodesh.ui.widget.LayoutedTextView
import na.komi.kodesh.util.*
import na.komi.skate.core.Skate
import na.komi.skate.core.extension.mode
import na.komi.skate.core.extension.show
import na.komi.skate.core.extension.startSkating
import kotlin.coroutines.CoroutineContext

/**
 *
 * Base activity for any activity that would have extended [AppCompatActivity]
 *
 * Ensures that some singleton methods are called.
 * This is simply a convenience class;
 * you can always copy and paste this to your own class.
 *
 * This also implements [CoroutineScope] that adheres to the activity lifecycle.
 * Note that by default, [SupervisorJob] is used, to avoid exceptions in one child from affecting that of another.
 * The default job can be overridden within [defaultJob]
 */
abstract class BaseActivity : AppCompatActivity(), CoroutineScope, TitleListener {
    abstract val layout: Int

    open val job = SupervisorJob()
    private lateinit var skate: Skate

    override val coroutineContext: CoroutineContext
        get() = ContextHelper.dispatcher + job


    val bottomSheetBehavior: BottomSheetBehavior2<ConstraintLayout>
        get() = BottomSheetBehavior2.from(bottomSheetContainer)

    val bottomSheetContainer: ConstraintLayout
        get() = findViewById(R.id.main_bottom_container)

    @Suppress("unused")
    private val isLargeLayout
        get() = resources.getBoolean(R.bool.large_layout)

    private val behavior by lazy { bottomSheetBehavior }

    val container by lazy { bottomSheetContainer }

    fun getNavigationView(): NavigationView = findViewById(R.id.navigation_view)

    fun getToolbar(): Toolbar? = findViewById(R.id.toolbar_main)

    fun getToolbarTitleView(): AppCompatTextView? {
        getToolbar()?.let {
            for (i in 0 until it.childCount) {
                val child = it.getChildAt(i)
                if (child is AppCompatTextView || child is LayoutedTextView || child is TextView) {
                    return child as? AppCompatTextView
                    //break
                }
            }
        }
        return null
    }


    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (behavior.state == BottomSheetBehavior2.STATE_EXPANDED) {
            val viewRect = Rect()
            container.getGlobalVisibleRect(viewRect)
            if (ev != null && !viewRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                behavior.close()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (Prefs.themeId) {
            1 -> setTheme(R.style.Theme_Dark)
            2 -> setTheme(R.style.Theme_Black)
            else -> setTheme(R.style.Theme_Light)
        }
        setContentView(layout)

        //val navController = findNavController(R.id.nav_host_fragment)
        val navigationView: NavigationView = getNavigationView()
        val toolbar = getToolbar()
        val mBottomSheetContainer: ConstraintLayout = findViewById(R.id.main_bottom_container)
        toolbar?.inflateMenu(R.menu.toolbar_menu_main)

        val mBottomSheetBehavior = BottomSheetBehavior2.from(mBottomSheetContainer)
        mBottomSheetBehavior.peekHeight = toolbar?.measuredHeight?.let { if (it <= 0) 147 else it } ?: 147
        mBottomSheetBehavior.state = BottomSheetBehavior2.STATE_COLLAPSED


        /**
         * We're using the same Fragment instance on this config(Portrait/Landscape)
         * This preserves its state. Only on config change we create a new instance.
         */
        val mainFragment by lazy {
            supportFragmentManager.findFragmentByTag(MainFragment::class.java.name) as? MainFragment
                ?: MainFragment()
        }
        val findInPageFragment by lazy {
            supportFragmentManager.findFragmentByTag(FindInPageFragment::class.java.name) as? FindInPageFragment
                ?: FindInPageFragment()
        }
        val prefaceFragment by lazy {
            supportFragmentManager.findFragmentByTag(PrefaceFragment::class.java.name) as? PrefaceFragment
                ?: PrefaceFragment()
        }
        val searchFragment by lazy {
            (supportFragmentManager.findFragmentByTag(SearchFragment::class.java.name) as? SearchFragment)
                ?: SearchFragment()
        }
        val settingsFragment by lazy {
            supportFragmentManager.findFragmentByTag(SettingsFragment::class.java.name) as? SettingsFragment
                ?: SettingsFragment()
        }
        val aboutFragment by lazy {
            supportFragmentManager.findFragmentByTag(AboutFragment::class.java.name) as? AboutFragment
                ?: AboutFragment()
        }

        skate = startSkating(savedInstanceState)

        skate.fragmentManager = supportFragmentManager
        skate.container = R.id.nav_main_container
        mainFragment.mode = Skate.SINGLETON
        findInPageFragment.mode = Skate.SPARING
        settingsFragment.mode = Skate.SPARING

        if (savedInstanceState == null) {
            mainFragment.show()
            Application.init = true

        } else {
            if (!Application.init) {
                Application.init = true
                //supportFragmentManager.findFragmentById(skate.container)?.view?.post {
                //    skate to mainFragment
                // }
            }
            if (skate.current is SettingsFragment) {
                launch {
                    getToolbar()?.post {
                        val title = getString(R.string.settings_title)
                        getToolbar()?.title = title
                        getToolbarTitleView()?.text = title
                        getToolbarTitleView()?.addTextChangedListener(object : TextWatcher {
                            override fun afterTextChanged(s: Editable?) {
                            }

                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                            }

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                getToolbarTitleView()?.removeTextChangedListener(this)
                                getToolbar()?.title = Prefs.title
                                getToolbarTitleView()?.text = Prefs.title
                            }

                        })
                    }
                }
            }

        }


        //if (skate.current?.javaClass == FindInPageFragment::class.java) {

        //}

        toolbar?.post {
            getToolbar()?.let { tb ->
                tb.setNavigationOnClickListener { mBottomSheetBehavior.toggle() }
                tb.menu.findItem(R.id.search_menu)?.setOnMenuItemClickListener {
                    setLowProfileStatusBar()
                    if (mBottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                        searchFragment.navToFrag()
                    } else {
                        skate to searchFragment
                    }
                    mBottomSheetBehavior.close()
                    navigationView.menu.children.forEach {
                        it.isChecked = false
                    }
                    true

                }
                tb.menu.findItem(R.id.find_in_page).setOnMenuItemClickListener {
                    findInPageFragment.show()
                    mBottomSheetBehavior.close()
                    true
                }
            }

        }
        /**
         * https://stackoverflow.com/a/37873884
         * https://stackoverflow.com/a/36793341
         */
        navigationView.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                it.setOnApplyWindowInsetsListener(object : View.OnApplyWindowInsetsListener {
                    override fun onApplyWindowInsets(v: View, insets: WindowInsets): WindowInsets {
                        return insets
                    }
                })
            }

            it.setNavigationItemSelectedListener { item ->
                mBottomSheetBehavior.close()
                setLowProfileStatusBar()
                //skate.container = R.id.nav_main_container
                //skate.mode = Skate.FACTORY

                // Prevent pressing self
                if (!item.isChecked) {
                    //if (item.itemId != R.id.action_read) skate.hide(mainFragment, Skate.SINGLETON)
                    when (item.itemId) {
                        R.id.action_read -> {
                            mainFragment.navToFrag()
                            backToMain(mainFragment)
                        }
                        R.id.action_preface -> prefaceFragment.navToFrag()
                        R.id.action_settings -> settingsFragment.navToFrag()
                        R.id.action_about -> aboutFragment.navToFrag()

                    }
                }
                !item.isChecked
            }

            skate.setOnNavigateListener(object : Skate.OnNavigateListener {
                override fun onBackPressed(current: Fragment?) {
                    if (current != null && current.javaClass == MainFragment::class.java) {
                        mainFragment.show()
                        backToMain(current as MainFragment)
                    }
                }
            })
        }

    }

    fun backToMain(mainFragment: MainFragment) {
        bottomSheetContainer.visibility = View.VISIBLE
        //log w "backToMain ${findInPageFragment.hashCode()}"
        getToolbar()?.also { tb ->
            tb.title = if (Prefs.title.isNotEmpty()) Prefs.title else getString(R.string.app_name)
            getToolbarTitleView()?.apply {
                text = if (Prefs.title.isNotEmpty()) Prefs.title else getString(R.string.app_name)
                onClick { mainFragment.openNavDialog() }
            }
            for (a in tb.menu.children)
                a.isVisible = true
            tb.menu.findItem(R.id.styling).setOnMenuItemClickListener {
                mainFragment.openStylingDialog()
                true
            }
            /*tb.menu.findItem(R.id.find_in_page).setOnMenuItemClickListener {
                skate.container = R.id.container_main
                findInPageFragment.show()
                bottomSheetBehavior.close()
                //bottomSheetContainer.visibility = View.GONE
                skate.container = R.id.nav_main_container
                true
            }*/
        }
        getNavigationView().let { nv ->
            nv.setCheckedItem(R.id.action_read)
            nv.menu.findItem(R.id.action_read).isChecked = true
            nv.apply {
                isClickable = true
                isFocusableInTouchMode = true
            }
        }

    }

    fun Fragment.navToFrag() {
        launch {
            delay(250)
            skate to this@navToFrag
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
    }

    override fun onStart() {
        super.onStart()

        //ViewCompat.setBackground(v, ColorDrawable(ContextCompat.getColor(this, R.color.default_background_color)))
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            val min = 0.5f
            val multiplier = 1f + min

            var v = skate.current?.view
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {


                }
                if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                    skate.current?.let {
                        v = it.view
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val alpha = Math.abs(slideOffset - 1f)
                v?.alpha = (alpha + min) / multiplier // Prevent from hitting 0, then normalize
            }

        })
    }

    override fun onBackPressed() {
        //  if (findNavController(R.id.nav_host_fragment).currentDestination?.id != R.id.mainFragment)
        //     bottomSheetContainer.invalidate()
        val mBottomSheetBehavior = bottomSheetBehavior
        if (mBottomSheetBehavior.state == BottomSheetBehavior2.STATE_EXPANDED)
            mBottomSheetBehavior.state = BottomSheetBehavior2.STATE_COLLAPSED
        else if (!skate.back)
            super.onBackPressed()
    }

    @Suppress("unused")
    fun setupStatusBar() {
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) window.navigationBarColor =
        //    ContextCompat.getColor(this, R.color.colorAccent)
        /*TranslucentBarManager(this).transparent(this)
        setLowProfileStatusBar()
        setLightStatusBar()
        setLightNavBar()
    */
    }

}

