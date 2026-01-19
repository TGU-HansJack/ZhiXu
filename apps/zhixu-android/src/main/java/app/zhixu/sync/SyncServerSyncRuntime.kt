package app.zhixu.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

object SyncServerSyncRuntime {
    private val _runningCount = MutableStateFlow(0)
    val runningCount: StateFlow<Int> = _runningCount.asStateFlow()

    fun begin(): AutoCloseable {
        _runningCount.update { it + 1 }
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (!closed.compareAndSet(false, true)) return@AutoCloseable
            _runningCount.update { (it - 1).coerceAtLeast(0) }
        }
    }
}

