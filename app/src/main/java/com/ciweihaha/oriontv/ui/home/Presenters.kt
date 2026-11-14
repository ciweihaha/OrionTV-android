package com.ciweihaha.oriontv.ui.home

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.model.HomeAction
import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.ui.common.PosterArt

/** Poster card for channels and VOD items (2:3, 12dp radius via card style). */
class PosterCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            cardType = ImageCardView.CARD_TYPE_MAIN_ONLY
            val density = resources.displayMetrics.density
            setMainImageDimensions((160 * density).toInt(), (240 * density).toInt())
            isFocusable = true
            isFocusableInTouchMode = true
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val card = viewHolder.view as ImageCardView
        when (item) {
            is Channel -> {
                card.titleText = item.name
                card.contentText = item.group
                card.mainImage = PosterArt.make(card.context, item.name, 320, 480)
            }
            is HomeAction -> {
                card.titleText = item.title
                card.contentText = item.subtitle
                card.mainImage = PosterArt.make(card.context, item.title, 320, 480)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as ImageCardView).mainImage = null
    }
}

/** Plain text card (category chips, empty-state rows). */
class TextCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        val frame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                (220 * density).toInt(),
                (72 * density).toInt(),
            )
            setBackgroundResource(R.drawable.bg_card_focusable)
            isFocusable = true
            isFocusableInTouchMode = true
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val text = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.orion_text))
            textSize = 16f
            maxLines = 2
        }
        frame.addView(text)
        return ViewHolder(frame)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val frame = viewHolder.view as FrameLayout
        val text = frame.getChildAt(0) as TextView
        text.text = when (item) {
            is VodCategory -> item.name
            is String -> item
            else -> item.toString()
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        ((viewHolder.view as FrameLayout).getChildAt(0) as TextView).text = null
    }
}
