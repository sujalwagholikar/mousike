package com.mousike.app.ui.components
import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mousike.app.ui.theme.*
@OptIn(ExperimentalLayoutApi::class)
@Composable fun RefreshRateControls(choice: String,onChoice: (String)->Unit) {
    val context=LocalContext.current
    val manager=remember(context){context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager}
    var revision by remember {mutableIntStateOf(0)}
    DisposableEffect(manager) {
        val listener=object: DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int){revision++};override fun onDisplayRemoved(id: Int){revision++};override fun onDisplayChanged(id: Int){revision++}
        }
        manager.registerDisplayListener(listener,android.os.Handler(android.os.Looper.getMainLooper()))
        onDispose {manager.unregisterDisplayListener(listener)}
    }
    val display=remember(revision){manager.getDisplay(android.view.Display.DEFAULT_DISPLAY)}
    val mode=display?.mode
    val rates=display?.supportedModes.orEmpty().filter {it.physicalWidth==mode?.physicalWidth && it.physicalHeight==mode.physicalHeight}.map {it.refreshRate}
    Text("Display refresh rate",style=MaterialTheme.typography.titleSmall,color=TextPrimary)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf("Auto","60 Hz","90 Hz","120 Hz").forEach {label ->
            val target=label.removeSuffix(" Hz").toIntOrNull()
            val available=target==null || rates.any {kotlin.math.abs(it-target)<1f}
            FilterChip(choice==label,{onChoice(label)},label={Text(label)},enabled=available,modifier=Modifier.testTag("refresh_${target ?: 0}"))
        }
    }
    Text("Current display: ${display?.refreshRate?.toInt() ?: 60} Hz. Android may adjust the rate for battery saving and display support.",style=MaterialTheme.typography.bodySmall,color=TextMuted)
}
