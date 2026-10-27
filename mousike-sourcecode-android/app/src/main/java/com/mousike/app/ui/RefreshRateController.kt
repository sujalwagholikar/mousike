package com.mousike.app.ui

import android.app.Activity
import android.os.Build

/** Select a real mode at the current resolution. Auto leaves the platform in charge. */
object RefreshRateController {
    fun choose(rates: List<Float>,requested: Int): Float? {
        if(requested<=0)return null
        val valid=rates.filter {it>0 && it<=120.5f}
        return valid.filter {it<=requested+.5f}.maxOrNull() ?: valid.minOrNull()
    }
    @Suppress("DEPRECATION") fun apply(activity: Activity,choice: String) {
        val display=if(Build.VERSION.SDK_INT>=30)activity.display else activity.windowManager.defaultDisplay
        display ?: return
        val current=display.mode
        val modes=display.supportedModes.filter {it.physicalWidth==current.physicalWidth && it.physicalHeight==current.physicalHeight}
        val rate=choose(modes.map {it.refreshRate},choice.removeSuffix(" Hz").toIntOrNull() ?: 0)
        val mode=rate?.let {r->modes.firstOrNull {kotlin.math.abs(it.refreshRate-r)<.6f}}
        val attrs=activity.window.attributes
        val id=mode?.modeId ?: 0;val hz=rate ?: 0f
        if(attrs.preferredDisplayModeId!=id || attrs.preferredRefreshRate!=hz) {
            attrs.preferredDisplayModeId=id;attrs.preferredRefreshRate=hz;activity.window.attributes=attrs
        }
    }
}
