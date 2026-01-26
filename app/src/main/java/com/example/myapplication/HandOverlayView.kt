package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class HandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<HandDetectionAnalyzer.NormalizedPoint>? = null

    private var imageWidth: Int = 1280
    private var imageHeight: Int = 720
    private var mirrorX: Boolean = true
    private var flipY: Boolean = false

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 6f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val connections: Array<IntArray> = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4),
        intArrayOf(0, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 8),
        intArrayOf(0, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 12),
        intArrayOf(0, 13), intArrayOf(13, 14), intArrayOf(14, 15), intArrayOf(15, 16),
        intArrayOf(0, 17), intArrayOf(17, 18), intArrayOf(18, 19), intArrayOf(19, 20),
        intArrayOf(5, 9), intArrayOf(9, 13), intArrayOf(13, 17)
    )

    fun setLandmarks(points: List<HandDetectionAnalyzer.NormalizedPoint>?) {
        landmarks = points
        postInvalidateOnAnimation()
    }

    fun setTransform(imageWidth: Int, imageHeight: Int, mirrorX: Boolean, flipY: Boolean = false) {
        if (imageWidth > 0) this.imageWidth = imageWidth
        if (imageHeight > 0) this.imageHeight = imageHeight
        this.mirrorX = mirrorX
        this.flipY = flipY
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pts = landmarks ?: return
        if (pts.size < 21) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val imgW = imageWidth.toFloat()
        val imgH = imageHeight.toFloat()

        // FIT_CENTER mapping: scale uniformly so the full image fits, with letterboxing.
        val scale = minOf(viewW / imgW, viewH / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale
        val offsetX = (viewW - scaledW) / 2f
        val offsetY = (viewH - scaledH) / 2f

        fun mapX(nx: Float): Float {
            val x = if (mirrorX) (1f - nx) else nx
            return offsetX + x * scaledW
        }

        fun mapY(ny: Float): Float {
            val y = if (flipY) (1f - ny) else ny
            return offsetY + y * scaledH
        }

        // Draw connections first
        for (pair in connections) {
            val a = pair[0]
            val b = pair[1]
            if (a >= pts.size || b >= pts.size) continue

            val ax = mapX(pts[a].x)
            val ay = mapY(pts[a].y)
            val bx = mapX(pts[b].x)
            val by = mapY(pts[b].y)

            canvas.drawLine(ax, ay, bx, by, linePaint)
        }

        // Draw landmarks as points
        for (p in pts) {
            canvas.drawCircle(mapX(p.x), mapY(p.y), 8f, pointPaint)
        }
    }
}
