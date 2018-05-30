package com.lxt.cfmoto.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.util.*


/**
 * 行驶数据图表（首页>Go）
 *
 * @author fanhl
 */
class TravelChart @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var paint = Paint()
    private var path = Path()

    // --------------------------------- 输入 ---------------------------

    var data: Data<*>? = null
        set(value) {
            field = value
            invalidate()
        }

    var dataParser: DataParser = DefaultDataParser()
        set(value) {
            field = value
            invalidate()
        }

    // --------------------------------- 运算 ---------------------------------

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.isAntiAlias = true
        paint.color = Color.RED

        if (isInEditMode) {
            dataParser = object : TravelChart.DataParser {
                override fun parseItem(item: Any): Vector2 {
                    val itemItem = item as? DefaultItem ?: return Vector2(0f, 0f)

                    return Vector2(itemItem.x, itemItem.y)
                }
            }
            data = TravelChart.Data<DefaultItem>().apply {
                list.apply {
                    fun add(x: Float, y: Float) {
                        add(DefaultItem(x, y))
                    }
                    add(0f, 1f)
                    add(100f, 200f)
                    add(200f, 5f)
                    add(300f, 400f)
                    add(400f, 100f)
                    add(500f, 20f)
                    add(600f, 20f)
                    add(700f, 200f)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val validWidth = width - paddingLeft - paddingRight
        val validHeight = height - paddingTop - paddingBottom

        val saveCount = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        if (data != null) {

            // draw curve
            val drawCurveSaveCount = canvas.save()

            // 获取绘制曲线的区域

            val drawCurvePaddingLeft = 0f
            val drawCurvePaddingRight = 0f
            val drawCurvePaddingTop = 50f
            val drawCurvePaddingBottom = 50f

            val drawCurveWidth = validWidth - drawCurvePaddingLeft - drawCurvePaddingRight
            val drawCurveHeight = validHeight - drawCurvePaddingTop - drawCurvePaddingBottom

            canvas.translate(drawCurvePaddingLeft, drawCurvePaddingTop)

            val list = data!!.list
            val yMin = list.min
            val yMax = list.max
            val iterator = list.iterator()
            if (iterator.hasNext()) {
                val vector2 = dataParser.parseItem(iterator.next() ?: return)

                path.reset()
                val (x, y) = projectionToCanvas(drawCurveWidth, drawCurveHeight, vector2, yMin, yMax)
                path.moveTo(x, y)

                //验证有多个点
                var isLine = false

                while (iterator.hasNext()) {
                    val vector2 = dataParser.parseItem(iterator.next() ?: return)
                    val (x, y) = projectionToCanvas(drawCurveWidth, drawCurveHeight, vector2, yMin, yMax)
                    path.lineTo(x, y)
                    isLine = true
                }

                if (isLine) {
                    canvas.drawPath(path, paint)
                }
            }

            canvas.restoreToCount(drawCurveSaveCount)
        }

        canvas.restoreToCount(saveCount)
    }

    /**
     * 将data中的item数据点的坐标系转换成手机屏幕坐标系
     */
    private fun projectionToCanvas(width: Float, height: Float, vector2: Vector2, yMin: Float, yMax: Float): Vector2 {
        var yBound = yMax - yMin
        if (Math.abs(yBound) <= 0f) {
            yBound = 1f // 给定一个最小值
        }

        var yPercent = (vector2.y - yMin) / yBound

        val x = vector2.x
        val y = (1 - yPercent) * height

//        throw Exception("yMin:$yMin,yMax:$yMax")

        return Vector2(x, y)
    }

    /**
     * TravelChart要绘制的数据
     */
    class Data<T : IItem> {
        val list = BoundList<T>()
    }

    /**
     * TravelChart的图表上关键点的数据结构
     */
    interface IItem {
        fun getYAxies(): Float
    }

    /**
     * only used in EditMode
     */
    private data class DefaultItem(
            var x: Float,
            var y: Float
    ) : IItem {
        override fun getYAxies(): Float {
            return y
        }
    }

    /**
     * 数据转换处理
     */
    interface DataParser {

        fun parseItem(item: Any): Vector2
    }

    /**
     * 默认数据转换处理
     */
    class DefaultDataParser : DataParser {

        override fun parseItem(item: Any): Vector2 {
            return Vector2(0f, 0f)
        }
    }

    /**
     * 在add时计算最大值最小值
     */
    class BoundList<T : IItem> : ArrayList<T>() {
        /** 这里存放整个列表的最小值 */
        var min: Float = 0f
        /** 这里存放整个列表的最大值 */
        var max: Float = 0f

        init {
            resetBound()
        }

        override fun add(element: T): Boolean {
            val add = super.add(element)
            min = minOf(min, element.getYAxies())
            max = maxOf(max, element.getYAxies())
            return add
        }

        override fun add(index: Int, element: T) {
            super.add(index, element)
            min = minOf(min, element.getYAxies())
            max = maxOf(max, element.getYAxies())
        }

        override fun addAll(elements: Collection<T>): Boolean {
            val addAll = super.addAll(elements)
            elements.forEach { element ->
                min = minOf(min, element.getYAxies())
                max = maxOf(max, element.getYAxies())

            }
            return addAll
        }

        override fun addAll(index: Int, elements: Collection<T>): Boolean {
            val addAll = super.addAll(index, elements)
            elements.forEach { element ->
                min = minOf(min, element.getYAxies())
                max = maxOf(max, element.getYAxies())

            }
            return addAll
        }

        // 注意 remove 时 还未做 mion/max 的处理，先不管

        override fun clear() {
            super.clear()
            resetBound()
        }

        private fun resetBound() {
            min = 0f
            max = 0f
        }
    }
}