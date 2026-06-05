package org.schabi.newpipe.local.subscription

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.evernote.android.state.State
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogTitleBinding
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.subscription.SubscriptionViewModel.SubscriptionState
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.ImportSubscriptionsHintPlaceholderItem
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.SimpleDialog
import org.schabi.newpipe.util.external_communication.ShareUtils

class SubscriptionFragment : BaseStateFragment<SubscriptionState>() {
    private var _binding: FragmentSubscriptionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SubscriptionViewModel
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var importExportHelper: SubscriptionsImportExportHelper
    private val disposables: CompositeDisposable = CompositeDisposable()

    private val groupAdapter = GroupAdapter<GroupieViewHolder>()
    private val subscriptionsSection = Section()

    @State
    @JvmField
    var itemsListState: Parcelable? = null

    init {
        setHasOptionsMenu(true)
    }

    // /////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    // /////////////////////////////////////////////////////////////////////////

    override fun onAttach(context: Context) {
        super.onAttach(context)
        subscriptionManager = SubscriptionManager(requireContext())
        importExportHelper = SubscriptionsImportExportHelper(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_subscription, container, false)
    }

    override fun onPause() {
        super.onPause()
        itemsListState = binding.itemsList.layoutManager?.onSaveInstanceState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    // ////////////////////////////////////////////////////////////////////////
    // Menu
    // ////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        activity.supportActionBar?.setDisplayShowTitleEnabled(true)
        activity.supportActionBar?.setTitle(R.string.tab_subscriptions)

        buildImportExportMenu(menu)
    }

    private fun buildImportExportMenu(menu: Menu) {
        menu.add(R.string.import_title)
            .setIcon(R.drawable.ic_backup)
            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER)
            .setOnMenuItemClickListener {
                importExportHelper.onImportPreviousSelected()
                true
            }

        menu.add(R.string.export_to)
            .setIcon(R.drawable.ic_save)
            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER)
            .setOnMenuItemClickListener {
                importExportHelper.onExportSelected()
                true
            }
    }

    // ////////////////////////////////////////////////////////////////////////
    // Fragment Views
    // ////////////////////////////////////////////////////////////////////////

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        _binding = FragmentSubscriptionBinding.bind(rootView)
        super.initViews(rootView, savedInstanceState)

        groupAdapter.spanCount = 1
        binding.itemsList.layoutManager = LinearLayoutManager(requireContext())
        binding.itemsList.adapter = groupAdapter
        binding.itemsList.itemAnimator = null

        viewModel = ViewModelProvider(this)[SubscriptionViewModel::class.java]
        viewModel.stateLiveData.observe(viewLifecycleOwner) { it?.let(this::handleResult) }

        setupInitialLayout()
    }

    private fun setupInitialLayout() {
        subscriptionsSection.setPlaceholder(ImportSubscriptionsHintPlaceholderItem())
        subscriptionsSection.setHideWhenEmpty(true)

        groupAdapter.clear()
        groupAdapter.add(subscriptionsSection)
    }

    private fun showLongTapDialog(selectedItem: ChannelInfoItem) {
        val commands = arrayOf(
            getString(R.string.share),
            getString(R.string.open_in_browser),
            getString(R.string.unsubscribe)
        )

        val actions = DialogInterface.OnClickListener { _, i ->
            when (i) {
                0 -> ShareUtils.shareText(
                    requireContext(),
                    selectedItem.name,
                    selectedItem.url,
                    selectedItem.thumbnails
                )

                1 -> ShareUtils.openUrlInBrowser(requireContext(), selectedItem.url)

                2 -> confirmDeleteChannel(selectedItem)
            }
        }

        val dialogTitleBinding = DialogTitleBinding.inflate(LayoutInflater.from(requireContext()))
        dialogTitleBinding.root.isSelected = true
        dialogTitleBinding.itemTitleView.text = selectedItem.name
        dialogTitleBinding.itemAdditionalDetails.visibility = View.GONE

        AlertDialog.Builder(requireContext())
            .setCustomTitle(dialogTitleBinding.root)
            .setItems(commands, actions)
            .show()
    }

    private fun confirmDeleteChannel(selectedItem: ChannelInfoItem) {
        SimpleDialog.show(
            requireContext(),
            0,
            R.string.remove_subscription_confirmation,
            R.string.no,
            null,
            0,
            null,
            R.string.yes,
            { deleteChannel(selectedItem) }
        )
    }

    private fun deleteChannel(selectedItem: ChannelInfoItem) {
        disposables.add(
            subscriptionManager.deleteSubscription(selectedItem.serviceId, selectedItem.url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.channel_unsubscribed),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    {
                        Toast.makeText(requireContext(), R.string.general_error, Toast.LENGTH_SHORT).show()
                    }
                )
        )
    }

    override fun doInitialLoadLogic() = Unit
    override fun startLoading(forceLoad: Boolean) = Unit

    private val listenerChannelItem = object : OnClickGesture<ChannelInfoItem> {
        override fun selected(selectedItem: ChannelInfoItem) = NavigationHelper.openChannelFragment(
            fm,
            selectedItem.serviceId,
            selectedItem.url,
            selectedItem.name
        )

        override fun held(selectedItem: ChannelInfoItem) = showLongTapDialog(selectedItem)
    }

    private fun openLatestVideo(stream: StreamInfoItem) = NavigationHelper.openVideoDetailFragment(
        requireContext(),
        fm,
        stream.serviceId,
        stream.url,
        stream.name,
        null,
        false
    )

    override fun handleResult(result: SubscriptionState) {
        super.handleResult(result)

        when (result) {
            is SubscriptionState.LoadedState -> {
                result.subscriptions.forEach {
                    if (it is ChannelItem) {
                        it.gesturesListener = listenerChannelItem
                        it.favoriteClickListener = viewModel::setFavorite
                        it.unsubscribeClickListener = ::confirmDeleteChannel
                        it.expandClickListener = viewModel::toggleExpanded
                        it.latestVideoClickListener = ::openLatestVideo
                        it.itemVersion = ChannelItem.ItemVersion.NORMAL
                    }
                }

                subscriptionsSection.update(result.subscriptions)
                subscriptionsSection.setHideWhenEmpty(false)

                if (itemsListState != null) {
                    binding.itemsList.layoutManager?.onRestoreInstanceState(itemsListState)
                    itemsListState = null
                }
            }

            is SubscriptionState.ErrorState -> {
                result.error?.let {
                    showError(ErrorInfo(result.error, UserAction.SOMETHING_ELSE, "Subscriptions"))
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Contract
    // /////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        binding.itemsList.animate(false, 100)
    }

    override fun hideLoading() {
        super.hideLoading()
        binding.itemsList.animate(true, 200)
    }

    companion object {
        val JSON_MIME_TYPE = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension("json") ?: "application/octet-stream"
    }
}
