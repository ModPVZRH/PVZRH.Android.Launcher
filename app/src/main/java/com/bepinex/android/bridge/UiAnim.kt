package com.bepinex.android.bridge

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator

/** Overlay expand/collapse and tab-switch motion. */
internal object UiAnim {

    const val COLLAPSE_MS = 220L
    const val TAB_MS = 180L

    val Ease = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    fun height(view: View, expand: Boolean, current: ValueAnimator?): ValueAnimator? {
        current?.cancel()
        if (expand) {
            view.visibility = View.VISIBLE
            val target = measureWrapHeight(view)
            val start = view.height
            if (target <= 0) {
                restoreWrap(view)
                return null
            }
            if (start == target) {
                restoreWrap(view)
                return null
            }
            return runHeight(view, start, target, hideAtEnd = false)
        }
        val start = view.height
        if (start <= 0) {
            view.visibility = View.GONE
            restoreWrap(view)
            return null
        }
        return runHeight(view, start, 0, hideAtEnd = true)
    }

    fun rotation(view: View, degrees: Float, animate: Boolean) {
        view.animate().cancel()
        if (!animate) {
            view.rotation = degrees
            return
        }
        view.animate()
            .rotation(degrees)
            .setDuration(COLLAPSE_MS)
            .setInterpolator(Ease)
            .start()
    }

    fun tabSwitch(
        outgoing: View?,
        incoming: View,
        forward: Boolean,
        containerWidth: Int,
        generation: Int,
        currentGeneration: () -> Int,
    ) {
        outgoing?.animate()?.cancel()
        incoming.animate().cancel()
        if (outgoing == null || outgoing === incoming) {
            incoming.visibility = View.VISIBLE
            incoming.alpha = 1f
            incoming.translationX = 0f
            return
        }
        val density = incoming.resources.displayMetrics.density
        val maxDx = 48f * density
        val dx = (if (containerWidth > 0) containerWidth * 0.22f else 40f * density)
            .coerceAtMost(maxDx)
            .let { if (forward) it else -it }

        incoming.visibility = View.VISIBLE
        incoming.alpha = 0f
        incoming.translationX = dx

        outgoing.animate()
            .alpha(0f)
            .translationX(-dx)
            .setDuration(TAB_MS)
            .setInterpolator(Ease)
            .withEndAction {
                if (generation != currentGeneration()) return@withEndAction
                outgoing.visibility = View.GONE
                outgoing.alpha = 1f
                outgoing.translationX = 0f
            }
            .start()

        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(TAB_MS)
            .setInterpolator(Ease)
            .start()
    }

    private fun runHeight(
        view: View,
        from: Int,
        to: Int,
        hideAtEnd: Boolean,
    ): ValueAnimator {
        val lp = view.layoutParams
        lp.height = from
        view.layoutParams = lp
        return ValueAnimator.ofInt(from, to).apply {
            duration = COLLAPSE_MS
            interpolator = Ease
            addUpdateListener { animator ->
                lp.height = animator.animatedValue as Int
                view.layoutParams = lp
            }
            addListener(object : AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    if (hideAtEnd) view.visibility = View.GONE
                    restoreWrap(view)
                }
            })
            start()
        }
    }

    private fun restoreWrap(view: View) {
        val lp = view.layoutParams ?: return
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        view.layoutParams = lp
    }

    private fun measureWrapHeight(view: View): Int {
        val parent = view.parent as? View ?: return 0
        val width = parent.width - parent.paddingLeft - parent.paddingRight
        val widthSpec = if (width > 0) {
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        } else {
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }
        view.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return view.measuredHeight
    }
}
