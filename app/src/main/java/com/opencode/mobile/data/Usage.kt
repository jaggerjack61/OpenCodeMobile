package com.opencode.mobile.data

data class UsageWindow(
    val status: String?,
    val percent: Float,
    val resetsAt: Long?,
)

data class GoUsage(
    val rolling: UsageWindow?,
    val weekly: UsageWindow?,
    val monthly: UsageWindow?,
)
