package com.mousike.app.playback

import android.content.Context
import org.json.JSONObject
import java.io.File

interface VisitorStore {fun get(): String?;fun put(value: String);fun clear()}
class MemoryVisitorStore: VisitorStore {
    @Volatile private var value: String?=null
    override fun get()=value
    override fun put(value: String){this.value=value}
    override fun clear(){value=null}
}
/** Anonymous protocol session, shared with catalog requests. No account or API key. */
object VisitorSession: VisitorStore {
    @Volatile private var file: File?=null
    @Volatile private var value: String?=null
    @Volatile private var savedAt=0L
    fun initialize(context: Context){file=File(context.cacheDir,"visitor-session.json")}
    @Synchronized override fun get(): String? {
        if(value==null)runCatching {file?.takeIf {it.isFile}?.readText()?.let {JSONObject(it)}?.let {value=it.optString("visitor");savedAt=it.optLong("savedAt")}}
        return value?.takeIf {it.isNotBlank() && System.currentTimeMillis()-savedAt<12*60*60*1000L}
    }
    @Synchronized override fun put(value: String){
        if(value.isBlank())return
        this.value=value;savedAt=System.currentTimeMillis()
        runCatching {file?.writeText(JSONObject().put("visitor",value).put("savedAt",savedAt).toString())}
    }
    @Synchronized override fun clear(){value=null;savedAt=0;file?.delete()}
}
