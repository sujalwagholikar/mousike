package com.mousike.app.performance

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.*

/** Only the selected song races two routes. A quick native result avoids starting Python at all. */
object ForegroundResolver {
    private val workers=Executors.newCachedThreadPool {r ->Thread(r,"foreground-source").apply {isDaemon=true;priority=Thread.NORM_PRIORITY}}
    fun <T: Any> resolve(parent: ResolutionSignal,delayMs: Long=2000,budgetMs: Long=35000,
        primary: (ResolutionSignal)->T?,fallback: (ResolutionSignal)->T): T {
        val native=ResolutionSignal();val compatibility=ResolutionSignal()
        val completion=ExecutorCompletionService<Pair<T?,Throwable?>>(workers)
        val primaryFailed=CountDownLatch(1)
        val first=completion.submit(Callable {try {val value=primary(native);if(value==null)primaryFailed.countDown();Pair(value,null)}catch(e: Exception){primaryFailed.countDown();Pair(null,e)}})
        val second=completion.submit(Callable {
            try {if(delayMs>0)primaryFailed.await(delayMs,TimeUnit.MILLISECONDS);compatibility.check();Pair(fallback(compatibility),null)}
            catch(e: Exception){Pair(null,e)}
        })
        parent.onCancel {native.cancel();compatibility.cancel();first.cancel(true);second.cancel(true)}
        val deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budgetMs)
        var last: Throwable?=null
        try {
            repeat(2) {
                parent.check()
                val remaining=deadline-System.nanoTime()
                if(remaining<=0)throw InterruptedIOException("Connection took too long")
                val result=completion.poll(remaining,TimeUnit.NANOSECONDS)?.get() ?: throw InterruptedIOException("Connection took too long")
                result.first?.let {value ->return value};last=result.second ?: last
            }
            throw IOException(last?.message ?: "No playable source is available",last)
        } catch(e: InterruptedException) {Thread.currentThread().interrupt();throw InterruptedIOException("Selection cancelled")}
        finally {native.cancel();compatibility.cancel();first.cancel(true);second.cancel(true)}
    }
}
