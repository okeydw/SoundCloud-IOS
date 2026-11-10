package com.scd.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scd.android.res.Res
import com.scd.android.res.ic_arrow_up
import com.scd.android.res.scroll_top
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val SCROLL_TOP_FROM = 3

suspend fun LazyListState.scrollToTop() {
    if (firstVisibleItemIndex > 24) scrollToItem(10)
    animateScrollToItem(0)
}

object ScrollTopTargets {
    private val stack = ArrayList<() -> Boolean>()

    fun add(target: () -> Boolean) {
        stack.add(target)
    }

    fun remove(target: () -> Boolean) {
        stack.remove(target)
    }

    fun scrollTop(): Boolean = stack.asReversed().any { it() }
}

@Composable
fun rememberScrollTop(state: LazyListState, from: Int = SCROLL_TOP_FROM): Pair<Boolean, () -> Unit> {
    val scope = rememberCoroutineScope()
    val show by remember(state, from) { derivedStateOf { state.firstVisibleItemIndex >= from } }
    val up: () -> Unit = remember(state) { { scope.launch { state.scrollToTop() } } }
    PlatformBackHandler(enabled = show, onBack = up)
    DisposableEffect(state) {
        val target: () -> Boolean = {
            val canScroll = state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0
            if (canScroll) up()
            canScroll
        }
        ScrollTopTargets.add(target)
        onDispose { ScrollTopTargets.remove(target) }
    }
    return show to up
}

@Composable
fun GlassList(
    modifier: Modifier = Modifier.fillMaxSize(),
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = dockContentPadding(),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = contentPadding, content = content)
        ScrollTopOrb(state)
    }
}

@Composable
fun BoxScope.ScrollTopOrb(state: LazyListState) {
    val (show, up) = rememberScrollTop(state)
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = LocalDockPadding.current + nav + 14.dp),
        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.7f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.7f),
    ) {
        IconOrb(Res.drawable.ic_arrow_up, stringResource(Res.string.scroll_top), size = 48.dp, iconSize = 20.dp, onClick = up)
    }
}
