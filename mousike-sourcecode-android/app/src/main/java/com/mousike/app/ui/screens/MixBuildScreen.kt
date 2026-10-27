package com.mousike.app.ui.screens
import androidx.compose.runtime.*
import com.mousike.app.ui.viewmodel.MousikeViewModel
import com.mousike.app.ui.components.PreparationScreen
@Composable fun MixBuildScreen(vm: MousikeViewModel) {
    val stage by vm.mixStage.collectAsState();val title by vm.mixTitle.collectAsState();val error by vm.mixError.collectAsState()
    PreparationScreen(title,listOf("Reading your listening taste","Balancing artists and sounds","Building your track list","Your mix is ready"),stage,error,{vm.navigateBack()},{vm.retryDailyMix()})
}
