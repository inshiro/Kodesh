package na.komi.kodesh.ui.main

import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.recyclerview_child.*
import kotlinx.coroutines.*
import na.komi.kodesh.Application
import na.komi.kodesh.R
import na.komi.kodesh.model.Bible
import na.komi.kodesh.ui.internal.ItemDecorator
import na.komi.kodesh.ui.internal.LinearLayoutManager2
import na.komi.kodesh.ui.widget.NestedRecyclerView
import kotlin.coroutines.CoroutineContext
import androidx.recyclerview.widget.SimpleItemAnimator



class MainPageAdapter(private val vm: MainViewModel, private val coroutineContext: CoroutineContext) :
    PagedListAdapter<Bible, MainPageAdapter.ViewHolder>(object : DiffUtil.ItemCallback<Bible>() {
        /**
         * This diff callback informs the PagedListAdapter how to compute list differences when new
         * PagedLists arrive.
         * <p>
         * When you add a Bible with the 'Add' button, the PagedListAdapter uses diffCallback to
         * detect there's only a single item difference from before, so list[i] only needs to animate and
         * rebind a single view.
         *
         * @see android.support.v7.util.DiffUtil
         */
        override fun areItemsTheSame(oldItem: Bible, newItem: Bible): Boolean =
            oldItem.id == newItem.id

        /**
         * Note that in kotlin, == checking on data classes compares all contents, but in Java,
         * typically you'll implement Object#equals, and use list[i] to compare object contents.
         */
        override fun areContentsTheSame(oldItem: Bible, newItem: Bible): Boolean =
            oldItem == newItem
    }) {
    private val clipboard by lazy {
        Application.instance.applicationContext.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager?
    }
    private val audioManager by lazy {
        Application.instance.applicationContext.getSystemService(
            Context.AUDIO_SERVICE
        ) as AudioManager?
    }
    private val widthPadding = 10
    private val dropCapSizeRatio = 4f
    protected val uiScope by lazy { CoroutineScope(coroutineContext) }
    protected val bg: CoroutineDispatcher = Dispatchers.IO

    val ZSPACE = "\u200B"

    init {
        setHasStableIds(true)
    }


    override fun getItemId(position: Int): Long {
        return getItem(position)?.id?.toLong() ?: position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.recyclerview_child, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    fun updateTextSize(size: Float) {
        //vm.fromAdapterNotify.value = true
        textSize = size
        notifyDataSetChanged()
    }

    private var textSize = -1f

    val viewPool by lazy { RecyclerView.RecycledViewPool() }

    inner class ViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        val childRecyclerView: NestedRecyclerView = child_recycler_view
        // private val lm by lazy { GridLayoutManager(childRecyclerView.context, 2) }

        init {
            childRecyclerView.apply {
                //ViewCompat.setNestedScrollingEnabled(this, true)
                //removeItemDecoration(itemDecorator)
                //addItemDecoration(itemDecorator)
                (this.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
                itemAnimator = null
                setRecycledViewPool(viewPool)
                layoutManager = LinearLayoutManager2(childRecyclerView.context, RecyclerView.VERTICAL, false).apply {
                    //recycleChildrenOnDetach = true // Resets scrollbar position
                    isItemPrefetchEnabled = true
                    //initialPrefetchItemCount = 3
                }
                adapter = MainChildAdapter(vm)
            }
        }

        fun bind(bible: Bible) {
            uiScope.launch {
                val list = withContext(vm.executorDispatcher) {
                    vm.getVersesRaw(bible.bookId!!, bible.chapterId!!).toMutableList()
                }
                (childRecyclerView.adapter as MainChildAdapter).setList(list)
                (childRecyclerView.layoutManager as LinearLayoutManager2).let {
                    it.currentList = list

                    // Scroll to top
                    if (vm.fromAdapterNotify) {
                        it.postOnAnimation {
                            childRecyclerView.post {
                                vm.fromAdapterNotify = false
                            }
                        }

                    } else {
                        if (childRecyclerView.computeVerticalScrollOffset() != 0) {
                        it.scrollToPositionWithOffset(0, 0)
                        it.resetScroll()
                    }
                    }
                }

            }


        }

    }


}