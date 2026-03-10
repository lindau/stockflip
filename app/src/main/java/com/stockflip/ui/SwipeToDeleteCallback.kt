package com.stockflip.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
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

    private val deleteBackground = ColorDrawable(
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorError, Color.RED)
    )
    private val navigateBackground = ColorDrawable(
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
    )
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
        // Icon size fixed at 28dp for consistent affordance regardless of drawable intrinsic size
        val iconSizePx = (28 * itemView.context.resources.displayMetrics.density).toInt()
        val iconMarginV = (itemView.height - iconSizePx) / 2

        if (dX < 0) {
            // Left swipe — delete (red background covering full item height, trash icon on right)
            deleteBackground.setBounds(
                itemView.right + dX.toInt(),
                itemView.top,
                itemView.right,
                itemView.bottom
            )
            deleteBackground.draw(c)

            deleteIcon?.let { icon ->
                val iconMarginH = iconSizePx + (8 * itemView.context.resources.displayMetrics.density).toInt()
                val iconTop = itemView.top + iconMarginV
                val iconLeft = itemView.right - iconMarginH
                val iconRight = iconLeft + iconSizePx
                val iconBottom = iconTop + iconSizePx
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                icon.setTint(Color.WHITE)
                icon.draw(c)
            }
        } else if (dX > 0) {
            // Right swipe — navigate (primary background, chart/stock icon on left)
            navigateBackground.setBounds(
                itemView.left,
                itemView.top,
                itemView.left + dX.toInt(),
                itemView.bottom
            )
            navigateBackground.draw(c)

            navigateIcon?.let { icon ->
                val iconMarginH = (8 * itemView.context.resources.displayMetrics.density).toInt()
                val iconTop = itemView.top + iconMarginV
                val iconLeft = itemView.left + iconMarginH
                val iconRight = iconLeft + iconSizePx
                val iconBottom = iconTop + iconSizePx
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                icon.setTint(Color.WHITE)
                icon.draw(c)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
