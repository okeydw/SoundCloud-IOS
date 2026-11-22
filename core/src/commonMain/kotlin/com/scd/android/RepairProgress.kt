package com.scd.android

enum class RepairOutcome { CLEAN, REPAIRED, REMAINING }

data class RepairProgress(val checked: Int = 0, val repaired: Int = 0, val remaining: Int = 0) {
    fun add(outcome: RepairOutcome) = copy(
        checked = checked + 1,
        repaired = repaired + if (outcome == RepairOutcome.REPAIRED) 1 else 0,
        remaining = remaining + if (outcome == RepairOutcome.REMAINING) 1 else 0,
    )
}
