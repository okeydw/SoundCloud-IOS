package com.scd.android

import kotlin.concurrent.Volatile
import platform.Network.nw_path_get_status
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.darwin.dispatch_get_main_queue

object IosNet {
    @Volatile
    var metered: Boolean = false
        private set

    private var monitor: Any? = null

    fun start() {
        if (monitor != null) return
        val m = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(m) { path ->
            if (path != null && nw_path_get_status(path) == nw_path_status_satisfied) ScAnon.onNetworkChanged()
            metered = path != null && (nw_path_is_expensive(path) || nw_path_is_constrained(path))
        }
        nw_path_monitor_set_queue(m, dispatch_get_main_queue())
        nw_path_monitor_start(m)
        monitor = m
    }
}
