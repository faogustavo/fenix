/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.sessioncontrol.viewholders

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observer
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.collection_home_list_row.*
import kotlinx.android.synthetic.main.collection_home_list_row.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import mozilla.components.browser.menu.BrowserMenu
import mozilla.components.browser.menu.BrowserMenuBuilder
import mozilla.components.browser.menu.item.SimpleBrowserMenuItem
import org.mozilla.fenix.DefaultThemeManager
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.urlToTrimmedHost
import org.mozilla.fenix.home.sessioncontrol.CollectionAction
import org.mozilla.fenix.home.sessioncontrol.SessionControlAction
import org.mozilla.fenix.home.sessioncontrol.TabCollection
import org.mozilla.fenix.home.sessioncontrol.onNext
import kotlin.coroutines.CoroutineContext

class CollectionViewHolder(
    val view: View,
    val actionEmitter: Observer<SessionControlAction>,
    val job: Job,
    override val containerView: View? = view
) :
    RecyclerView.ViewHolder(view), LayoutContainer, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private lateinit var collection: TabCollection
    private var expanded = false
    private var collectionMenu: CollectionItemMenu

    init {
        collectionMenu = CollectionItemMenu(view.context) {
            when (it) {
                is CollectionItemMenu.Item.DeleteCollection -> actionEmitter.onNext(CollectionAction.Delete(collection))
                is CollectionItemMenu.Item.AddTab -> actionEmitter.onNext(CollectionAction.AddTab(collection))
                is CollectionItemMenu.Item.RenameCollection -> actionEmitter.onNext(CollectionAction.Rename(collection))
                is CollectionItemMenu.Item.OpenTabs -> actionEmitter.onNext(CollectionAction.OpenTabs(collection))
            }
        }

        collection_overflow_button.run {
            setOnClickListener {
                collectionMenu.menuBuilder
                    .build(view.context)
                    .show(anchor = it, orientation = BrowserMenu.Orientation.DOWN)
            }
        }

        collection_share_button.run {
            setOnClickListener {
                actionEmitter.onNext(CollectionAction.ShareTabs(collection))
            }
        }

        view.setOnClickListener {
            handleExpansion(expanded)
        }
    }

    fun bindSession(collection: TabCollection, expanded: Boolean) {
        this.collection = collection
        this.expanded = expanded
        updateCollectionUI()

        // See #2625 for why we're invalidating
        view.invalidate()
    }

    private fun updateCollectionUI() {
        val title = if (collection.title.length > maxTitleLength) {
            collection.title.substring(0, maxTitleLength) + "..."
        } else {
            collection.title
        }

        view.collection_title.text = title

        var hostNameList = listOf<String>()

        collection.tabs.forEach {
            hostNameList += it.url.urlToTrimmedHost().capitalize()
        }

        var tabsDisplayed = 0
        val tabTitlesList = hostNameList.joinToString(", ") {
            if (it.length > maxTitleLength) {
                it.substring(
                    0,
                    maxTitleLength
                ) + "..."
            } else {
                tabsDisplayed += 1
                it
            }
        }

        view.collection_description.text = tabTitlesList

        if (expanded) {
            (view.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 0
            collection_title.setPadding(0, 0, 0, EXPANDED_PADDING)
            view.background = ContextCompat.getDrawable(view.context, R.drawable.rounded_top_corners)
            view.collection_description.visibility = View.GONE
            view.expand_button.setImageDrawable(ContextCompat.getDrawable(view.context, R.drawable.ic_chevron_up))
        } else {
            (view.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = COLLAPSED_MARGIN
            view.background = ContextCompat.getDrawable(view.context, R.drawable.rounded_all_corners)
            view.collection_description.visibility = View.VISIBLE
            view.expand_button.setImageDrawable(ContextCompat.getDrawable(view.context, R.drawable.ic_chevron_down))
        }

        view.collection_icon.setColorFilter(
            ContextCompat.getColor(
                view.context,
                getIconColor(collection.id)
            ),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    private fun handleExpansion(isExpanded: Boolean) {
        if (isExpanded) {
            actionEmitter.onNext(CollectionAction.Collapse(collection))
        } else {
            actionEmitter.onNext(CollectionAction.Expand(collection))
        }
    }

    @Suppress("ComplexMethod", "MagicNumber")
    private fun getIconColor(id: Long): Int {
        val sessionColorIndex = (id % 4).toInt()
        return when (sessionColorIndex) {
            0 -> R.color.collection_icon_color_violet
            1 -> R.color.collection_icon_color_blue
            2 -> R.color.collection_icon_color_pink
            3 -> R.color.collection_icon_color_green
            4 -> R.color.collection_icon_color_yellow
            else -> R.color.white_color
        }
    }

    companion object {
        const val EXPANDED_PADDING = 60
        const val COLLAPSED_MARGIN = 12
        const val LAYOUT_ID = R.layout.collection_home_list_row
        const val maxTitleLength = 20
    }
}

class CollectionItemMenu(
    private val context: Context,
    private val onItemTapped: (Item) -> Unit = {}
) {
    sealed class Item {
        object DeleteCollection : Item()
        object AddTab : Item()
        object RenameCollection : Item()
        object OpenTabs : Item()
    }

    val menuBuilder by lazy { BrowserMenuBuilder(menuItems) }

    private val menuItems by lazy {
        listOf(
            SimpleBrowserMenuItem(
                context.getString(R.string.collection_delete),
                textColorResource = DefaultThemeManager.resolveAttribute(R.attr.destructive, context)
            ) {
                onItemTapped.invoke(Item.DeleteCollection)
            },
            SimpleBrowserMenuItem(
                context.getString(R.string.add_tab)
            ) {
                onItemTapped.invoke(Item.AddTab)
            },
            SimpleBrowserMenuItem(
                context.getString(R.string.collection_rename)
            ) {
                onItemTapped.invoke(Item.RenameCollection)
            },
            SimpleBrowserMenuItem(
                context.getString(R.string.collection_open_tabs)
            ) {
                onItemTapped.invoke(Item.OpenTabs)
            }
        )
    }
}
