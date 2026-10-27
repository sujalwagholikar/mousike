package com.mousike.app.performance

import java.io.InterruptedIOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** One request per key. A tap promotes preparation; cancellation releases only that subscriber. */
class ConnectionWork<K: Any,V: Any> {
    private val entries=HashMap<K,Entry>()
    private val workers=Executors.newCachedThreadPool {r->Thread(r,"audio-connection").apply {isDaemon=true}}
    private inner class Entry(val key: K,var speculative: Boolean) {
        val signal=ResolutionSignal();var readers=0
        lateinit var task: FutureTask<V>
    }
    @Synchronized private fun entry(key: K,speculative: Boolean,operation: (ResolutionSignal)->V,onPrepared: ((V)->Unit)?=null): Entry {
        entries[key]?.takeIf {!it.signal.isCancelled}?.let {old ->
            val failed=old.task.isDone && runCatching {old.task.get()}.isFailure
            if(!failed){if(!speculative)old.speculative=false;return old}
            entries.remove(key)
        }
        val e=Entry(key,speculative)
        e.task=FutureTask(Callable {
            val result=operation(e.signal);e.signal.check()
            synchronized(this) {if(e.speculative && entries[key]===e)onPrepared?.invoke(result)}
            result
        })
        entries[key]=e;workers.execute(e.task);return e
    }
    @Synchronized fun prepare(key: K,operation: (ResolutionSignal)->V,onPrepared: (V)->Unit) {
        cancelSpeculationExcept(key)
        entry(key,true,operation,onPrepared)
    }
    @Synchronized fun cancelSpeculationExcept(key: K?) {
        val old=entries.values.filter {it.speculative && it.key!=key}.toList()
        old.forEach {entries.remove(it.key);it.signal.cancel();it.task.cancel(true)}
    }
    fun get(key: K,parent: ResolutionSignal,budgetMs: Long,operation: (ResolutionSignal)->V): V {
        parent.check()
        val e=synchronized(this) {cancelSpeculationExcept(key);entry(key,false,operation).also {it.readers++}}
        val released=AtomicBoolean()
        fun release() {
            if(!released.compareAndSet(false,true))return
            synchronized(this) {
                e.readers--
                if(e.readers==0 && !e.speculative) {
                    if(entries[key]===e)entries.remove(key)
                    if(!e.task.isDone){e.signal.cancel();e.task.cancel(true)}
                }
            }
        }
        parent.onCancel {release()}
        val end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budgetMs)
        try {
            while(true) {
                parent.check()
                val remaining=end-System.nanoTime()
                if(remaining<=0)throw InterruptedIOException("Connection timed out")
                try {return e.task.get(minOf(remaining,TimeUnit.MILLISECONDS.toNanos(100)),TimeUnit.NANOSECONDS).also {parent.check()}}
                catch(_: TimeoutException) { }
            }
        } catch(e: ExecutionException){throw (e.cause ?: e)}
        catch(_: CancellationException){throw InterruptedIOException("Connection cancelled")}
        catch(_: InterruptedException){Thread.currentThread().interrupt();throw InterruptedIOException("Connection cancelled")}
        finally {release()}
    }
    @Synchronized fun discard(key: K) {entries.remove(key)?.let {it.signal.cancel();it.task.cancel(true)}}
}
