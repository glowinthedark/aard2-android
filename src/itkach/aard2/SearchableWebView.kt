/*
 * This file is heavily inspired by the Android Open Source Project
 * licensed under the Apache License, Version 2.0
 */
package itkach.aard2

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.max

 open class SearchableWebView constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(
    context, attrs
), NestedScrollingChild {
    private var mLastFind: String? = null

    private var lastMotionX = 0
    private var lastMotionY = 0

    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)

    private var nestedOffsetY = 0

    private val childHelper = NestedScrollingChildHelper(this)

    fun setLastFind(find: String?) {
        mLastFind = find
    }

    private fun init() {
        isNestedScrollingEnabled = true
    }

    /**
     * Start an ActionMode for finding text in this WebView.  Only works if this
     * WebView is attached to the view system.
     *
     * @param text    If non-null, will be the initial text to search for.
     * Otherwise, the last String searched for in this WebView will
     * be used to start.
     * @param showIme If true, show the IME, assuming the user will begin typing.
     * If false and text is non-null, perform a find all.
     * @return boolean True if the find dialog is shown, false otherwise.
     */
    override fun showFindDialog(text: String?, showIme: Boolean): Boolean {
        var text = text
        val callback = FindActionModeCallback(context, this)
        if (parent == null || startActionMode(callback) == null) {
            // Could not start the action mode, so end Find on page
            return false
        }

        if (showIme) {
            callback.showSoftInput()
        } else if (text != null) {
            callback.setText(text)
            callback.findAll()
            return true
        }
        if (text == null) {
            text = mLastFind
        }
        if (text != null) {
            callback.setText(text)
            callback.findAll()
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var result = false

        val trackedEvent = MotionEvent.obtain(event)

        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }

        val x = event.x
        val y = event.y

        event.offsetLocation(0f, nestedOffsetY.toFloat())

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastMotionX = x.toInt()
                lastMotionY = y.toInt()
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
                result = super.onTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                var deltaY = (lastMotionY - y).toInt()

                if (abs(deltaY.toDouble()) > abs((lastMotionX - x).toDouble()) &&
                    (canScrollVertically(1) || canScrollVertically(-1))
                ) {
                    requestDisallowInterceptTouchEvent(true)
                }

                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset)) {
                    deltaY -= scrollConsumed[1]
                    trackedEvent.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                }

                lastMotionY = (y - scrollOffset[1]).toInt()

                val oldY = scrollY
                val newScrollY = max(0.0, (oldY + deltaY).toDouble()).toInt()
                val dyConsumed = newScrollY - oldY
                val dyUnconsumed = deltaY - dyConsumed

                if (dispatchNestedScroll(0, dyConsumed, 0, dyUnconsumed, scrollOffset)) {
                    lastMotionY -= scrollOffset[1]
                    trackedEvent.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                }

                result = super.onTouchEvent(trackedEvent)
                trackedEvent.recycle()
            }

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopNestedScroll()
                requestDisallowInterceptTouchEvent(false)
                result = super.onTouchEvent(event)
            }
        }
        return result
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun stopNestedScroll() {
        childHelper.stopNestedScroll()
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return childHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return childHelper.startNestedScroll(axes)
    }

    override fun hasNestedScrollingParent(): Boolean {
        return childHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean {
        return childHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        return childHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return childHelper.dispatchNestedPreFling(velocityX, velocityY)
    }
}
