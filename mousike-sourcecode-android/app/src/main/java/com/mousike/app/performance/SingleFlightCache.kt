package com.mousike.app.performance

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/** Shares expensive work across playback, album inspection and speculative warmups. */
class SingleFlightCache<K,V>(private val capacity: Int,private val valid: (V)->Boolean = {true}) {
    private val entries=LinkedHashMap<K,CompletableFuture<V>>(16,.75f,true)
    fun peek(key: K): V? = synchronized(entries) {
        val f=entries[key] ?: return@synchronized null
        if(!f.isDone || f.isCompletedExceptionally) return@synchronized null
        val value=f.getNow(null)
        if(valid(value)) value else { entries.remove(key); null }
    }
    fun get(key: K,loader: ()->V): V {
        var owner=false
        val f=synchronized(entries) {
            val old=entries[key]
            if(old!=null && (!old.isDone || (!old.isCompletedExceptionally && valid(old.getNow(null))))) old
            else CompletableFuture<V>().also { entries[key]=it; owner=true }
        }
        if(owner) {
            try { f.complete(loader()); synchronized(entries) { trim() } }
            catch(t: Throwable) { f.completeExceptionally(t); synchronized(entries) { if(entries[key]===f)entries.remove(key) } }
        }
        return try { f.get() } catch(e: ExecutionException) { throw (e.cause ?: e) }
    }
    private fun trim() {
        val i=entries.entries.iterator()
        while(entries.size>capacity && i.hasNext()) if(i.next().value.isDone)i.remove()
    }
    fun remove(key: K) { synchronized(entries) { entries.remove(key) } }
    fun clear() { synchronized(entries) { entries.clear() } }
}
