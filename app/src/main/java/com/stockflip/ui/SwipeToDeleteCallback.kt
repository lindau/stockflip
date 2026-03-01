package com.stockflip.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.stockflip.R

/**
 * Reusable ItemTouchHelper callback for swipe-to-delete gestures.
 * Draws a red background with a trash icon when swiping left.
 */
class SwipeToDeleteCallback(
    context: Context,
    private val onSwiped: (position: Int) -> Unit,
    private val canSwipe: (position: Int) -> Boolean = { true }
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val background = ColorDrawable(Color.parseColor("#F44336"))
    private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onSwiped(viewHolder.adapterPosition)
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return if (canSwipe(viewHolder.adapterPosition)) super.getSwipeDirs(recyclerView, viewHolder) else 0
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        if (dX < 0) {
            background.setBounds(
                itemView.right + dX.toInt(),
                itemView.top,
                itemView.right,
                itemView.bottom
            )
            background.draw(c)

            deleteIcon?.let { icon ->
                val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                val iconRight = itemView.right - iconMargin
                val iconBottom = iconTop + icon.intrinsicHeight
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                icon.setTint(Color.WHITE)
                icon.draw(c)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
