package com.fotos.app.ui.viewer

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrixValues = FloatArray(9)
    private val imgMatrix = Matrix()
    private var currentScale = 1f
    private val minScale = 1f
    private val maxScale = 4f

    var onSingleTap: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val nextScale = max(minScale, min(maxScale, currentScale * factor))
                val applied = nextScale / currentScale
                currentScale = nextScale
                imgMatrix.postScale(applied, applied, detector.focusX, detector.focusY)
                imageMatrix = imgMatrix
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onSingleTap?.invoke()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > 1.2f) {
                    resetZoom()
                } else {
                    currentScale = 2.5f
                    imgMatrix.postScale(2.5f, 2.5f, e.x, e.y)
                    imageMatrix = imgMatrix
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (currentScale > 1.05f) {
                    imgMatrix.postTranslate(-distanceX, -distanceY)
                    imageMatrix = imgMatrix
                    return true
                }
                return false
            }
        })

    init {
        scaleType = ScaleType.MATRIX
    }

    fun resetZoom() {
        currentScale = 1f
        imgMatrix.reset()
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (vw > 0 && vh > 0 && dw > 0 && dh > 0) {
            val scale = min(vw / dw, vh / dh)
            val dx = (vw - dw * scale) / 2f
            val dy = (vh - dh * scale) / 2f
            imgMatrix.setScale(scale, scale)
            imgMatrix.postTranslate(dx, dy)
        }
        imageMatrix = imgMatrix
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(currentScale > 1.05f)
        return true
    }
}
