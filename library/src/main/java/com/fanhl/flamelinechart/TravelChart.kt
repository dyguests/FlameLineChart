package com.lxt.cfmoto.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import com.fanhl.flamelinechart.R
import com.fanhl.flamelinechart.Range
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
    private val paint = Paint()
    private val path = Path()
    private val scroller = OverScroller(context)

    // --------------------------------- 输入 ---------------------------

    /** 水平两个坐标点的间距 */
    var xInterval = 0

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

    /** 居中的X的值 */
    private var centerX = 0

    /** 居中的X的偏移值 in (-0.5,0.5] */
    private var centerXOffset = 0f

    /**
     *  活动的x轴区间
     *  非区间的与区间内的显示不一样。（用来区分各月份的数据）
     */
    var activeXRange = Range(0f, 5f)

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.isAntiAlias = true
        paint.color = Color.RED

        val resources = context.resources
        val a = context.obtainStyledAttributes(attrs, R.styleable.TravelChart, defStyleAttr, R.style.Widget_Travel_Chart)

        xInterval = a.getDimensionPixelOffset(R.styleable.TravelChart_xInterval, resources.getDimensionPixelOffset(R.dimen.x_interval_default))

        a.recycle()

        if (isInEditMode) {
            dataParser = object : TravelChart.DataParser {
                override fun parseItem(item: IItem): Vector2 {
                    val itemItem = Vector2(item.getXAxis(), item.getYAxis())

                    return Vector2(itemItem.x, itemItem.y)
                }
            }
            data = TravelChart.Data<DefaultItem>().apply {
                list.apply {
                    fun add(x: Float, y: Float) {
                        add(DefaultItem(x, y))
                    }
                    add(0f, 123f)
                    add(1f, 200f)
                    add(2f, 5f)
                    add(3f, 400f)
                    add(4f, 100f)
                    add(5f, 20f)
                    add(6f, 20f)
                    add(7f, 200f)
                    add(8f, 200f)
                    add(9f, 300f)
                    add(10f, 400f)
                    add(11f, 200f)
                    add(12f, 300f)
                    add(13f, 400f)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        //先判断mScroller滚动是否完成
        if (scroller.computeScrollOffset()) {

            //这里调用View的scrollTo()完成实际的滚动
//            scrollTo(scroller.currX, scroller.currY)
            val (centerX, centerXOffset) = calculationCenterX(scroller.currX)
            this.centerX = centerX
            this.centerXOffset = centerXOffset

            Log.d(TAG, "computeScroll: centerX:$centerX,centerXOffset:$centerXOffset")

            //必须调用该方法，否则不一定能看到滚动效果
            postInvalidate()
        }
        super.computeScroll()
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

            // FIXME: 2018/5/30 fanhl 只绘制屏幕内的数据

            val list = data!!.list
            val yMin = list.min
            val yMax = list.max
            val iterator = list.iterator()
            if (iterator.hasNext()) {
                run {
                    val vector2 = dataParser.parseItem(iterator.next())

                    path.reset()
                    val (x, y) = projectionToCanvas(drawCurveWidth, drawCurveHeight, vector2, yMin, yMax)
                    path.moveTo(x, y)
                }

                //验证有多个点
                var isLine = false

                while (iterator.hasNext()) {
                    val vector2 = dataParser.parseItem(iterator.next())
                    val (x, y) = projectionToCanvas(drawCurveWidth, drawCurveHeight, vector2, yMin, yMax)
                    path.lineTo(x, y)
                    isLine = true
                }

                if (isLine) {
                    canvas.drawPath(path, paint)
                }
            }

            canvas.restoreToCount(drawCurveSaveCount)

            //draw current center hint
            canvas.drawLine((validWidth / 2).toFloat(), 0F, (validWidth / 2).toFloat(), validHeight.toFloat(), paint)
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

        val yPercent = (vector2.y - yMin) / yBound

        val x = width / 2 + (vector2.x - centerX - centerXOffset) * xInterval
        val y = (1 - yPercent) * height

//        throw Exception("yMin:$yMin,yMax:$yMax")

        return Vector2(x, y)
    }

    fun changeCenterX(centerX: Int) {
//        this.centerX = centerX
//        this.centerXOffset = 0f
        val startScrollX = calculationScrollX(this.centerX, 0f)
        val endScrollX = calculationScrollX(centerX, 0f)
        scroller.startScroll(startScrollX, 0, endScrollX - startScrollX, 0)
        invalidate()
    }

    /**
     * 根据centerX与centerXOffset计算出scrollX
     */
    private fun calculationScrollX(centerX: Int, centerXOffset: Float): Int {
        return ((centerX + centerXOffset) * xInterval).toInt()
    }

    /**
     * 根据scrollX计算出centerX与centerXOffset
     */
    private fun calculationCenterX(scrollX: Int): Pair<Int, Float> {
        val centerX = scrollX / xInterval
        val centerXOffset = (scrollX % xInterval).toFloat() / xInterval

        return Pair(centerX, centerXOffset)
    }

    companion object {
        val TAG = TravelChart::class.java.simpleName!!
    }

    /**
     * TravelChart要绘制的数据
     */
    class Data<T : IItem> {
        val list = BoundList<T>()
        // 添加数据时，判断数据是否在屏幕外，再决定是否 invalidate()
    }

    /**
     * TravelChart的图表上关键点的数据结构
     */
    interface IItem {
        fun getXAxis(): Float
        fun getYAxis(): Float
    }

    /**
     * only used in EditMode
     */
    private data class DefaultItem(
            var x: Float,
            var y: Float
    ) : IItem {
        override fun getXAxis(): Float {
            return x
        }

        override fun getYAxis(): Float {
            return y
        }
    }

    /**
     * 数据转换处理
     */
    interface DataParser {

        fun parseItem(item: IItem): Vector2
    }

    /**
     * 默认数据转换处理
     */
    class DefaultDataParser : DataParser {

        override fun parseItem(item: IItem): Vector2 {
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
            min = minOf(min, element.getYAxis())
            max = maxOf(max, element.getYAxis())
            return add
        }

        override fun add(index: Int, element: T) {
            super.add(index, element)
            min = minOf(min, element.getYAxis())
            max = maxOf(max, element.getYAxis())
        }

        override fun addAll(elements: Collection<T>): Boolean {
            val addAll = super.addAll(elements)
            elements.forEach { element ->
                min = minOf(min, element.getYAxis())
                max = maxOf(max, element.getYAxis())

            }
            return addAll
        }

        override fun addAll(index: Int, elements: Collection<T>): Boolean {
            val addAll = super.addAll(index, elements)
            elements.forEach { element ->
                min = minOf(min, element.getYAxis())
                max = maxOf(max, element.getYAxis())

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