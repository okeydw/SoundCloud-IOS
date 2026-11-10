package com.scd.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.scd.android.res.*
import androidx.compose.ui.unit.dp

@Composable
fun TrackPageFooter(pager: TrackPager) {
    LaunchedEffect(pager, pager.page, pager.hasMore, pager.loading) {
        if (pager.hasMore && !pager.loading && pager.error == null) pager.loadMore()
    }
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        if (pager.error != null) {
            TextButton(onClick = { if (pager.hasMore) pager.loadMore() else pager.refresh() }) {
                Text(stringResource(Res.string.retry_action))
            }
        } else if (pager.loading || pager.hasMore) CircularProgressIndicator(Modifier.size(24.dp))
    }
}
