package com.markq.core

object SyncErrors {
    fun isSilent(message: String?): Boolean {
        val m = message.orEmpty()
        if (m.contains("precondition", ignoreCase = true)) return true
        if (Regex("\\b412\\b").containsMatchIn(m)) return true
        return false
    }

    fun isSilent(error: Throwable): Boolean = isSilent(error.message)
}
