package com.fanhl.flamelinechart

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import java.util.*
import android.graphics.Shader
import android.graphics.LinearGradient
import android.os.Build
import android.support.v4.content.ContextCompat
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.R.attr.y
import android.R.attr.x
import android.support.v4.widget.ViewDragHelper.INVALID_POINTER


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
    private val paint by lazy { Paint() }
    private val path by lazy { Path() }
    private val scroller by lazy { OverScroller(context) }

    /**
     * True if the user is currently dragging this ScrollView around. This is
     * not the same as 'is being flinged', which can be checked by
     * mScroller.isFinished() (flinging begins when the user lifts his finger).
     */
    private var mIsBeingDragged = false
    /** 速度管理 */
    private val velocityTracker by lazy { VelocityTracker.obtain() }

    private var mTouchSlop: Int = 0
    private var mMinimumVelocity: Int = 0
    private var mMaximumVelocity: Int = 0
    private var mOverscrollDistance: Int = 0

    private var mScrollX: Int
        get() {
            return calculationScrollX(centerX, centerXOffset)
        }
        set(value) {
            val (centerX, centerXOffset) = calculationCenterX(value)
            this.centerX = centerX
            this.centerXOffset = centerXOffset
        }
    private var mScrollY: Int
        get() {
            return 0
        }
        set(value) {
        }

    // --------------------------------- 输入 ---------------------------
    /** 水平两个坐标点的间距 */
    var xInterval = 0
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }

    /** 曲线的水平渐变颜色起始值 */
    var gradientStart = 0
    /** 曲线的水平渐变颜色结束值 */
    var gradientEnd = 0

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
        set(value) {
            if (field == value) {
                return
            }
            field = value

            activeXRange.start = (centerX - centerX % 7).toFloat()
            activeXRange.end = (centerX - centerX % 7 + 7).toFloat()
        }

    /** 居中的X的偏移值 in (-0.5,0.5] */
    private var centerXOffset = 0f

    /**
     *  活动的x轴区间
     *  非区间的与区间内的显示不一样。（用来区分各月份的数据）
     */
    var activeXRange = Range(0f, 7f)


    /**
     * Position of the last motion event.
     */
    private var mLastMotionX: Int = 0

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 10f
        paint.isAntiAlias = true
        paint.color = Color.RED

        val configuration = ViewConfiguration.get(context)
        mTouchSlop = configuration.scaledTouchSlop
        mMinimumVelocity = configuration.scaledMinimumFlingVelocity
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity
        mOverscrollDistance = configuration.scaledOverscrollDistance
        // see more config : /android/widget/HorizontalScrollView.java:222


        val resources = context.resources
        val a = context.obtainStyledAttributes(attrs, R.styleable.TravelChart, defStyleAttr, R.style.Widget_Travel_Chart)

        xInterval = a.getDimensionPixelOffset(R.styleable.TravelChart_xInterval, resources.getDimensionPixelOffset(R.dimen.x_interval_default))

        gradientStart = a.getColor(R.styleable.TravelChart_gradientStart, ContextCompat.getColor(context, R.color.gradient_start))
        gradientEnd = a.getColor(R.styleable.TravelChart_gradientEnd, ContextCompat.getColor(context, R.color.gradient_start))

        a.recycle()

        if (isInEditMode) {
            dataParser = object : DataParser {
                override fun parseItem(item: IItem): Vector2 {
                    val itemItem = Vector2(item.getXAxis(), item.getYAxis())

                    return Vector2(itemItem.x, itemItem.y)
                }
            }
            data = Data<DefaultItem>().apply {
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

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        velocityTracker.addMovement(ev)

        val action = ev?.action ?: return false

        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mIsBeingDragged = !scroller.isFinished
                if (mIsBeingDragged) {
                    this.parent?.requestDisallowInterceptTouchEvent(true)
                }

                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }

                mLastMotionX = ev.x.toInt()
            }
            MotionEvent.ACTION_MOVE -> {
                val x = ev.x.toInt()
                var deltaX = mLastMotionX - x
                if (!mIsBeingDragged && Math.abs(deltaX) > mTouchSlop) {
                    val parent = parent
                    parent?.requestDisallowInterceptTouchEvent(true)
                    mIsBeingDragged = true
                    if (deltaX > 0) {
                        deltaX -= mTouchSlop
                    } else {
                        deltaX += mTouchSlop
                    }
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    mLastMotionX = x

                    val oldX = mScrollX
                    val oldY = 0
                    val range = getScrollRange()
                    val canOverscroll = overScrollMode == View.OVER_SCROLL_ALWAYS || overScrollMode == View.OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0

                    // Calling overScrollBy will call onOverScrolled, which
                    // calls onScrollChanged if applicable.
                    if (overScrollBy(deltaX, 0, mScrollX, 0, range, 0, mOverscrollDistance, 0, true)) {
                        // Break our velocity if we hit a scroll barrier.
                        velocityTracker.clear()
                    }

                    if (canOverscroll) {
                        val pulledToX = oldX + deltaX
                        if (pulledToX < 0) {
                            // 边缘效果
//                            mEdgeGlowLeft.onPull(deltaX.toFloat() / width, 1f - ev.getY(activePointerIndex) / height)
//                            if (!mEdgeGlowRight.isFinished()) {
//                                mEdgeGlowRight.onRelease()
//                            }
                        } else if (pulledToX > range) {
                            // 边缘效果
//                            mEdgeGlowRight.onPull(deltaX.toFloat() / width, ev.getY(activePointerIndex) / height)
//                            if (!mEdgeGlowLeft.isFinished()) {
//                                mEdgeGlowLeft.onRelease()
//                            }
                        }
//                        if (mEdgeGlowLeft != null && (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished())) {
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//                                postInvalidateOnAnimation()
//                            } else {
//                                postInvalidate()
//                            }
//                        }
                    }


                }
            }
            MotionEvent.ACTION_UP -> {
                if (mIsBeingDragged) {
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
                    val initialVelocity = velocityTracker.getXVelocity().toInt()

                    if (getChildCount() > 0) {
                        if (Math.abs(initialVelocity) > mMinimumVelocity) {
                            fling(-initialVelocity)
                        } else {
                            if (scroller.springBack(mScrollX, mScrollY, 0, getScrollRange(), 0, 0)) {
                                CompatibleHelper.postInvalidateOnAnimation(this)
                            }
                        }
                    }

//                    mActivePointerId = INVALID_POINTER
                    mIsBeingDragged = false
                    recycleVelocityTracker()

//                    if (mEdgeGlowLeft != null) {
//                        mEdgeGlowLeft.onRelease()
//                        mEdgeGlowRight.onRelease()
//                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (mIsBeingDragged && getChildCount() > 0) {
                    if (scroller.springBack(mScrollX, mScrollY, 0, getScrollRange(), 0, 0)) {
                        CompatibleHelper.postInvalidateOnAnimation(this)
                    }
//                    mActivePointerId = INVALID_POINTER
                    mIsBeingDragged = false
                    recycleVelocityTracker()

//                    if (mEdgeGlowLeft != null) {
//                        mEdgeGlowLeft.onRelease()
//                        mEdgeGlowRight.onRelease()
//                    }
                }
            }
        }
        return true
    }

    /**
     * Fling the scroll view
     *
     * @param velocityX The initial velocity in the X direction. Positive
     * numbers mean that the finger/cursor is moving down the screen,
     * which means we want to scroll towards the left.
     */
    private fun fling(velocityX: Int) {
        if (getChildCount() > 0) {
            val width = width - paddingRight - paddingLeft
            val right = getScrollRange()

            scroller.fling(mScrollX, mScrollY, velocityX, 0, 0, Math.max(0, right - width), 0, 0, width / 2, 0)

            val movingRight = velocityX > 0

            val currentFocused = findFocus()
            var newFocused: View? = null// findFocusableViewInMyBounds(movingRight, scroller.finalX, currentFocused)

            if (newFocused == null) {
                newFocused = this
            }

            if (newFocused !== currentFocused) {
                newFocused.requestFocus(if (movingRight) View.FOCUS_RIGHT else View.FOCUS_LEFT)
            }

            CompatibleHelper.postInvalidateOnAnimation(this)
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        if (mScrollX !== x || 0 !== y) {
            val oldX = mScrollX
            val oldY = mScrollY
            mScrollX = x
            mScrollY = y
//            invalidateParentCaches()
            onScrollChanged(mScrollX, mScrollY, oldX, oldY)
            if (!awakenScrollBars()) {
                CompatibleHelper.postInvalidateOnAnimation(this)
            }
        }
    }

    override fun computeScroll() {
        //先判断mScroller滚动是否完成
        if (scroller.computeScrollOffset()) {

            //这里调用View的scrollTo()完成实际的滚动
//            scrollTo(scroller.currX, scroller.currY)
            val (centerX, centerXOffset) = calculationCenterX(scroller.currX)
            this.centerX = centerX
            this.centerXOffset = centerXOffset

            //必须调用该方法，否则不一定能看到滚动效果
            CompatibleHelper.postInvalidateOnAnimation(this)
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
            drawCurve(canvas, validWidth, validHeight)

            //draw current center hint
            drawCenterHint(canvas, validWidth, validHeight)
        }

        canvas.restoreToCount(saveCount)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // Calling this with the present values causes it to re-claim them
        scrollTo(mScrollX, 0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        // Treat animating scrolls differently; see #computeScroll() for why.
        if (!scroller.isFinished) {
            val oldX = mScrollX
            val oldY = 0
            mScrollX = scrollX
//            mScrollY = scrollY
//            invalidateParentIfNeeded()
            onScrollChanged(mScrollX, 0, oldX, oldY)
            if (clampedX) {
                scroller.springBack(mScrollX, 0, 0, getScrollRange(), 0, 0)
            }
        } else {
//            super.scrollTo(scrollX, scrollY)
            scrollTo(scrollX, scrollY)
        }

        awakenScrollBars()
    }

    /**
     * 绘制曲线
     */
    private fun drawCurve(canvas: Canvas, validWidth: Int, validHeight: Int) {
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

        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        //fixme 把shader这个移到之前的方法中去 的实例
        var shader: Shader = LinearGradient(0f, 0f, drawCurveWidth, 0f, gradientStart, gradientEnd, Shader.TileMode.CLAMP)

        val (activeXPixelRangeStart, activeXPixelRangeEnd) = calculationActiveXPixelRange(drawCurveWidth, drawCurveHeight, activeXRange)

        Log.d(TAG, "drawCurve: activeXPixelRangeStart:$activeXPixelRangeStart,activeXPixelRangeEnd:$activeXPixelRangeEnd")

        if (activeXPixelRangeStart > 0) {
            val transparentStart = LinearGradient(activeXPixelRangeStart - xInterval, 0f, activeXPixelRangeStart, 0f, 0x22ffffff, Color.WHITE, Shader.TileMode.CLAMP)
//            val transparentStart = LinearGradient(0f, 0f, 200f, 0f, 0x22ffffff, Color.WHITE, Shader.TileMode.CLAMP)
            shader = ComposeShader(shader, transparentStart, PorterDuff.Mode.MULTIPLY)
        }
        if (activeXPixelRangeEnd < drawCurveWidth) {
            val transparentEnd = LinearGradient(activeXPixelRangeEnd, 0f, activeXPixelRangeEnd + xInterval, 0f, Color.WHITE, 0x22ffffff, Shader.TileMode.CLAMP)
//            val transparentEnd = LinearGradient(400f, 0f, 500f, 0f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            shader = ComposeShader(shader, transparentEnd, PorterDuff.Mode.MULTIPLY)
        }
        paint.shader = shader

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
    }

    /**
     * 绘制水平居中的提示线等
     */
    private fun drawCenterHint(canvas: Canvas, validWidth: Int, validHeight: Int) {
        canvas.drawLine((validWidth / 2).toFloat(), 0F, (validWidth / 2).toFloat(), validHeight.toFloat(), paint)
    }

    private fun getChildCount(): Int {
        return data?.list?.size ?: 0
    }

    private fun getScrollRange(): Int {
        var scrollRange = 0
        data?.list?.size?.let {
            scrollRange = it * xInterval
        }
        return scrollRange
    }

    private fun recycleVelocityTracker() {
        //这里临时改用 by lazy (为啥要recycle啊？)
//        velocityTracker.recycle()
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

        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }

        val startScrollX = calculationScrollX(this.centerX, centerXOffset)
        val endScrollX = calculationScrollX(centerX, 0f)
        scroller.startScroll(startScrollX, 0, endScrollX - startScrollX, 0, AUTO_SCROLL_DURATION_DEFAULT)
        invalidate()
    }

    /**
     * 将 activeXRange 转换成 activeXPixelRange,将图表的x坐标换算成屏幕上的x pixel 坐标
     */
    private fun calculationActiveXPixelRange(width: Float, height: Float, activeXRange: Range): Range {
        return Range(
                calculationScrollX((activeXRange.start - centerX).toInt(), -centerXOffset) + width / 2,
                calculationScrollX((activeXRange.end - centerX).toInt(), -centerXOffset).toFloat() + width / 2
        )
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
        var centerX = scrollX / xInterval
        var centerXOffset = (scrollX % xInterval).toFloat() / xInterval
        //注意 centerXOffset 的 值在 区间 (-0.5,0.5]中
        if (centerXOffset > 0.5f) {
            centerX += 1
            centerXOffset -= 1
        }

        return Pair(centerX, centerXOffset)
    }

    companion object {
        val TAG = TravelChart::class.java.simpleName!!

        private const val AUTO_SCROLL_DURATION_DEFAULT = 250

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