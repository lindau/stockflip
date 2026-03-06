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
 * Reusable ItemTouchHelper callback for swipe gestures.
 * Left swipe: draws a red background with a trash icon (delete).
 * Right swipe (optional): draws a blue background with a stock icon (navigate to detail).
 */
class SwipeToDeleteCallback(
    context: Context,
    private val onSwiped: (position: Int) -> Unit,
    private val canSwipe: (position: Int) -> Boolean = { true },
    private val onSwipedRight: ((position: Int) -> Unit)? = null
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val deleteBackground = ColorDrawable(Color.parseColor("#F44336"))
    private val navigateBackground = ColorDrawable(Color.parseColor("#2196F3"))
    private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete)
    private val navigateIcon = ContextCompat.getDrawable(context, R.drawable.ic_stock)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        if (direction == ItemTouchHelper.LEFT) {
            onSwiped(viewHolder.adapterPosition)
        } else if (direction == ItemTouchHelper.RIGHT) {
            onSwipedRight?.invoke(viewHolder.adapterPosition)
        }
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        if (!canSwipe(viewHolder.adapterPosition)) return 0
        var dirs = ItemTouchHelper.LEFT
        if (onSwipedRight != null) dirs = dirs or ItemTouchHelper.RIGHT
        return dirs
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
            // Left swipe — delete (red background, trash icon on right)
            deleteBackground.setBounds(
                itemView.right + dX.toInt(),
                itemView.top,
                itemView.right,
                itemView.bottom
            )
            deleteBackground.draw(c)

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
        } else if (dX > 0) {
            // Right swipe — navigate (blue background, stock icon on left)
            navigateBackground.setBounds(
                itemView.left,
                itemView.top,
                itemView.left + dX.toInt(),
                itemView.bottom
            )
            navigateBackground.draw(c)

            navigateIcon?.let { icon ->
                val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconLeft = itemView.left + iconMargin
                val iconRight = iconLeft + icon.intrinsicWidth
                val iconBottom = iconTop + icon.intrinsicHeight
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                icon.setTint(Color.WHITE)
                icon.draw(c)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
