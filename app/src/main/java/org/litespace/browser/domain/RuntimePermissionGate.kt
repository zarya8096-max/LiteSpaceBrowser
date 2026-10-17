package org.litespace.browser.domain

/** One Android permission request may remain in flight after its page is gone.
 * Cancellation denies its consumer, but reserves the slot until Android returns.
 * Otherwise that stale result could accidentally authorize the next website. */
class RuntimePermissionGate {
    private data class Pending(val permissions: Set<String>, val anyOf: Boolean, var callback: ((Boolean) -> Unit)?)
    private var pending: Pending? = null
    val busy: Boolean get() = pending != null

    fun begin(permissions: Set<String>, anyOf: Boolean = false, callback: (Boolean) -> Unit): Boolean {
        if (pending != null || permissions.isEmpty()) { callback(false); return false }
        pending = Pending(permissions, anyOf, callback)
        return true
    }

    fun resolve(grants: Map<String, Boolean>) {
        val request = pending ?: return
        pending = null
        val granted = if (request.anyOf) request.permissions.any { grants[it] == true }
            else request.permissions.all { grants[it] == true }
        request.callback?.invoke(granted)
    }

    fun cancel() {
        val callback = pending?.callback
        pending?.callback = null
        callback?.invoke(false)
    }
}
