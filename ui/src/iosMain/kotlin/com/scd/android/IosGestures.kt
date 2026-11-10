package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue


object IosBack {
    class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = ArrayList<Entry>()

    fun add(e: Entry) {
        entries.add(e)
    }

    fun remove(e: Entry) {
        entries.remove(e)
    }

    fun handle(): Boolean {
        val e = entries.lastOrNull { it.enabled } ?: return false
        e.onBack()
        return true
    }
}

@Composable
fun IosBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val entry = remember { IosBack.Entry(enabled, onBack) }
    SideEffect {
        entry.enabled = enabled
        entry.onBack = onBack
    }
    DisposableEffect(entry) {
        IosBack.add(entry)
        onDispose { IosBack.remove(entry) }
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class EdgeBackTarget : NSObject() {
    @ObjCAction
    fun onEdgePan(sender: UIScreenEdgePanGestureRecognizer) {
        if (sender.state != UIGestureRecognizerStateEnded) return
        val dx = sender.translationInView(sender.view).useContents { x }
        val vx = sender.velocityInView(sender.view).useContents { x }
        if (dx > 60.0 || vx > 500.0) IosBack.handle()
    }
}

private class StatusBarTap : NSObject(), UIScrollViewDelegateProtocol {
    override fun scrollViewShouldScrollToTop(scrollView: UIScrollView): Boolean {
        ScrollTopTargets.scrollTop()
        return false
    }
}

object IosGestures {
    private val edgeTarget = EdgeBackTarget()
    private val statusTap = StatusBarTap()
    private var installed = false

    @OptIn(ExperimentalForeignApi::class)
    fun install(vc: UIViewController) {
        dispatch_async(dispatch_get_main_queue()) {
            if (installed) return@dispatch_async
            installed = true
            val view = vc.view
            val edge = UIScreenEdgePanGestureRecognizer(target = edgeTarget, action = NSSelectorFromString("onEdgePan:"))
            edge.edges = UIRectEdgeLeft
            view.addGestureRecognizer(edge)

            val tap = UIScrollView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0))
            tap.scrollsToTop = true
            tap.showsVerticalScrollIndicator = false
            tap.showsHorizontalScrollIndicator = false
            tap.setContentSize(CGSizeMake(1.0, 3.0))
            tap.setContentOffset(CGPointMake(0.0, 1.0), animated = false)
            tap.alpha = 0.02
            tap.delegate = statusTap
            view.addSubview(tap)
        }
    }
}
