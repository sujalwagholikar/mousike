package com.mousike.app.performance

import java.io.InterruptedIOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** Cancellation reaches HTTP calls and native child processes, rather than just the UI job. */
class ResolutionSignal {
    private val stopped=AtomicBoolean(false)
    private val hooks=CopyOnWriteArrayList<()->Unit>()
    val isCancelled: Boolean get()=stopped.get()
    fun check() {if(isCancelled || Thread.currentThread().isInterrupted)throw InterruptedIOException("Playback request cancelled")}
    fun onCancel(hook: ()->Unit) {hooks.add(hook);if(isCancelled)runCatching(hook)}
    fun cancel() {if(stopped.compareAndSet(false,true)){hooks.forEach {runCatching(it)};hooks.clear()}}
}

object BoundedResolution {
    private val workers=Executors.newCachedThreadPool {r -> Thread(r,"bounded-audio").apply {isDaemon=true;priority=Thread.MIN_PRIORITY}}
    fun <T> run(timeoutMs: Long,signal: ResolutionSignal,operation: ()->T): T {
        signal.check()
        val task=workers.submit(Callable {signal.check();operation()})
        signal.onCancel {task.cancel(true)}
        try {return task.get(timeoutMs,TimeUnit.MILLISECONDS)}
        catch(e: TimeoutException) {signal.cancel();throw InterruptedIOException("The audio connection took too long.")}
        catch(e: InterruptedException) {signal.cancel();Thread.currentThread().interrupt();throw InterruptedIOException("Playback request cancelled")}
        catch(e: CancellationException) {throw InterruptedIOException("Playback request cancelled")}
        catch(e: ExecutionException) {throw (e.cause ?: e)}
    }
}
