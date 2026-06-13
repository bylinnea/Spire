package no.bylinnea.spire.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.abs

/**
 * Draws the coloured background and label revealed when a RecyclerView row is
 * swiped left or right. Shared by the plant list (MainActivity) and the health
 * log (PlantDetailActivity) so the swipe visuals stay consistent in one place.
 *
 * Alpha fades in with swipe distance. Pass [cornerRadius] = 0f for square
 * corners (log rows) or a positive value for rounded cards (plant list).
 */
object SwipeDecorator {

    fun draw(
        canvas: Canvas,
        item: View,
        dX: Float,
        leftColor: String,
        leftLabel: String,
        rightColor: String,
        rightLabel: String,
        cornerRadius: Float = 0f,
        textSize: Float = 32f,
        labelInset: Float = 32f
    ) {
        val alpha = (abs(dX) / (item.width * 0.35f)).coerceIn(0f, 1f)
        val bg = Paint().apply { this.alpha = (alpha * 255).toInt() }
        val label = Paint().apply {
            color = Color.WHITE
            this.alpha = (alpha * 255).toInt()
            this.textSize = textSize
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            isAntiAlias = true
        }
        val baselineY = item.top + item.height / 2f + label.textSize / 3f

        when {
            dX < 0 -> {
                bg.color = leftColor.toColorInt()
                canvas.drawRoundRect(
                    item.right + dX, item.top.toFloat(),
                    item.right.toFloat(), item.bottom.toFloat(),
                    cornerRadius, cornerRadius, bg
                )
                canvas.drawText(
                    leftLabel,
                    item.right - label.measureText(leftLabel) - labelInset,
                    baselineY, label
                )
            }
            dX > 0 -> {
                bg.color = rightColor.toColorInt()
                canvas.drawRoundRect(
                    item.left.toFloat(), item.top.toFloat(),
                    item.left + dX, item.bottom.toFloat(),
                    cornerRadius, cornerRadius, bg
                )
                canvas.drawText(rightLabel, item.left + labelInset, baselineY, label)
            }
        }
    }
}
