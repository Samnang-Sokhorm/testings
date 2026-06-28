package com.example.myapplication1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val metersToPixels = 100f // 1 meter = 100 pixels
    private val mapLengthPixels = 14.80f * metersToPixels
    private val mapWidthPixels = 6f * metersToPixels

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var scaleX = 1f
    private var scaleY = 1f

    private val robotHalfWidth = 30f
    private val robotHalfHeight = 20f

    private var posX = mapLengthPixels / 2f
    private var posY = mapWidthPixels / 2f
    private var theta = 0f // degrees

    private var Xx: Float = 0.0f
    private var Yy: Float = 0.0f
    private var Zz: Float = 0.0f

    private var LB: Int = 0
    private var RB: Int = 0
    private var LT: Int = 0
    private var RT: Int = 0

    private var M1: Int = 0
    private var M2: Int = 0

    private var X1: Int = 0
    private var Y1: Int = 1
    private var A1: Int = 0
    private var B1: Int = 0

    private var Forw: Int = 0
    private var Back: Int = 0
    private var Left: Int = 0
    private var Right: Int = 0

    private var offsetX = 0f
    private var offsetY = 0f

    private val robotPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val mapPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        scaleX = width / mapLengthPixels
        scaleY = height / mapWidthPixels

        offsetX = (width - mapLengthPixels) / 2f
        offsetY = (height - mapWidthPixels) / 2f

        // Draw map
        canvas.drawRect(
            offsetX,
            offsetY,
            offsetX + mapLengthPixels,
            offsetY + mapWidthPixels,
            mapPaint
        )

        // Draw robot
        canvas.save()
        canvas.translate(offsetX + posX, offsetY + posY)
        canvas.rotate(theta)
        canvas.drawRect(
            -robotHalfWidth,
            -robotHalfHeight,
            robotHalfWidth,
            robotHalfHeight,
            robotPaint
        )
        canvas.restore()

        canvas.drawText(
            "X: %.2f  Y: %.2f  Z: %.2f".format(Xx, Yy, Zz),
            30f,
            130f,
            textPaint
        )

        canvas.drawText(
            "LB: $LB  RB: $RB  LT: $LT  RT: $RT",
            30f,
            170f,
            textPaint
        )

        canvas.drawText(
            "X: $X1  Y Level: $Y1  A: $A1  B: $B1",
            30f,
            210f,
            textPaint
        )

        canvas.drawText(
            "Forw: $Forw  Back: $Back  Left: $Left  Right: $Right",
            30f,
            250f,
            textPaint
        )

        canvas.drawText(
            "M1: $M1  M2: $M2",
            30f,
            290f,
            textPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastTouchX) / scaleX
                val dy = (event.y - lastTouchY) / scaleY

                posX += dx
                posY += dy

                lastTouchX = event.x
                lastTouchY = event.y

                invalidate()
            }
        }

        return true
    }

    fun updateControllerState(
        x: Float,
        y: Float,
        z: Float,
        lb: Int,
        rb: Int,
        lt: Int,
        rt: Int,
        m1: Int,
        m2: Int,
        x1: Int,
        y1: Int,
        a1: Int,
        b1: Int,
        forw: Int,
        back: Int,
        left: Int,
        right: Int
    ) {
        Xx = x
        Yy = y
        Zz = z

        LB = lb
        RB = rb
        LT = lt
        RT = rt

        M1 = m1
        M2 = m2

        X1 = x1
        Y1 = y1
        A1 = a1
        B1 = b1

        Forw = forw
        Back = back
        Left = left
        Right = right

        invalidate()
    }
}