package com.simplemobiletools.smsmessenger.views

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import com.google.android.material.appbar.AppBarLayout
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.LOWER_ALPHA
import com.simplemobiletools.commons.helpers.MEDIUM_ALPHA
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import kotlinx.android.synthetic.main.menu_search.view.*

class MySearchMenu(context: Context, attrs: AttributeSet) : AppBarLayout(context, attrs) {
    var isSearchOpen = false
    var onSearchOpenListener: (() -> Unit)? = null
    var onSearchClosedListener: (() -> Unit)? = null
    var onSearchTextChangedListener: ((text: String) -> Unit)? = null

    init {
        inflate(context, R.layout.menu_search, this)
    }

    fun setupMenu() {
        top_toolbar_search_icon.setOnClickListener {
            if (isSearchOpen) {
                closeSearch()
            } else {
                top_toolbar_search.requestFocus()
                (context as? Activity)?.showKeyboard(top_toolbar_search)
            }
        }

        top_toolbar_search.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                openSearch()
            }
        }

        top_toolbar_search.onTextChangeListener {
            onSearchTextChangedListener?.invoke(it)
        }
    }

    private fun openSearch() {
        isSearchOpen = true
        onSearchOpenListener?.invoke()
        top_toolbar_search_icon.setImageResource(R.drawable.ic_arrow_left_vector)
    }

    fun closeSearch() {
        isSearchOpen = false
        onSearchClosedListener?.invoke()
        top_toolbar_search.setText("")
        top_toolbar_search_icon.setImageResource(R.drawable.ic_search_vector)
        (context as? Activity)?.hideKeyboard()
    }

    fun updateColors() {
        val backgroundColor = context.getProperBackgroundColor()
        val contrastColor = backgroundColor.getContrastColor()

        setBackgroundColor(backgroundColor)
        top_app_bar_layout.setBackgroundColor(backgroundColor)
        top_toolbar_search_icon.applyColorFilter(contrastColor)
        top_toolbar_holder.background?.applyColorFilter(context.getProperPrimaryColor().adjustAlpha(LOWER_ALPHA))
        top_toolbar_search.setTextColor(contrastColor)
        top_toolbar_search.setHintTextColor(contrastColor.adjustAlpha(MEDIUM_ALPHA))
        (context as? SimpleActivity)?.updateTopBarColors(top_toolbar, backgroundColor)
    }
}
