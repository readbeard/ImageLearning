package com.example.imagelearning.graphics

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.imagelearning.MainActivity
import com.google.mlkit.vision.objects.DetectedObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Draw the detected object info in preview.  */
class ObjectGraphic constructor(
        overlay: GraphicOverlay,
        private val detectedObject: DetectedObject
) : GraphicOverlay.Graphic(overlay) {

    private val numColors = COLORS.size

    private val boxPaints = Array(numColors) { Paint() }
    private val textPaints = Array(numColors) { Paint() }
    private val labelPaints = Array(numColors) { Paint() }

    init {
        for (i in 0 until numColors) {
            textPaints[i] = Paint()
            textPaints[i].color = COLORS[i][0]
            textPaints[i].textSize = TEXT_SIZE
            boxPaints[i] = Paint()
            boxPaints[i].color = COLORS[i][1]
            boxPaints[i].style = Paint.Style.STROKE
            boxPaints[i].strokeWidth = STROKE_WIDTH
            labelPaints[i] = Paint()
            labelPaints[i].color = COLORS[i][1]
            labelPaints[i].style = Paint.Style.FILL
        }
    }

    override fun draw(canvas: Canvas) {
        // Decide color based on object tracking ID
        val colorID =
                if (detectedObject.trackingId == null) 0
                else abs(detectedObject.trackingId!! % NUM_COLORS)
        val mostConfidentResult = detectedObject.labels[0].text
        val textWidth =
                textPaints[colorID].measureText(mostConfidentResult)
        val lineHeight = TEXT_SIZE + STROKE_WIDTH
        var yLabelOffset = -lineHeight

        // Draws the bounding box.
        rect = RectF(detectedObject.boundingBox)
        val x0 = translateX(rect.left)
        val x1 = translateX(rect.right)
        rect.left = min(x0, x1)
        rect.right = max(x0, x1)
        rect.top = translateY(rect.top)
        rect.bottom = translateY(rect.bottom)
        canvas.drawRoundRect(rect, 50f, 50f, boxPaints[colorID])

        // Draws other object info.
        canvas.drawRect(
                rect.left - STROKE_WIDTH,
                rect.top + yLabelOffset,
                rect.left + textWidth + 2 * STROKE_WIDTH,
                rect.top,
                labelPaints[colorID]
        )
        yLabelOffset += TEXT_SIZE
        canvas.drawText(
                mostConfidentResult,
                rect.left,
                rect.top + yLabelOffset,
                textPaints[colorID]
        )

    }

    override fun onTouch(x: Float, y: Float) {
        if (rect.contains(x, y)) {
            MainActivity.showBottomSheet(detectedObject);
        }
    }

    companion object {
        private const val TEXT_SIZE = 54.0f
        private const val STROKE_WIDTH = 4.0f
        private const val NUM_COLORS = 10
        private val COLORS =
                arrayOf(
                        intArrayOf(Color.BLACK, Color.WHITE),
                        intArrayOf(Color.WHITE, Color.MAGENTA),
                        intArrayOf(Color.BLACK, Color.LTGRAY),
                        intArrayOf(Color.WHITE, Color.RED),
                        intArrayOf(Color.WHITE, Color.BLUE),
                        intArrayOf(Color.WHITE, Color.DKGRAY),
                        intArrayOf(Color.BLACK, Color.CYAN),
                        intArrayOf(Color.BLACK, Color.YELLOW),
                        intArrayOf(Color.WHITE, Color.BLACK),
                        intArrayOf(Color.BLACK, Color.GREEN)
                )

        private val TAG = ObjectGraphic::class.simpleName
        private lateinit var rect: RectF
    }
}