package com.mousike.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.theme.*
import com.mousike.app.ui.viewmodel.NavScreen

data class NavItem(val screen: NavScreen,val label: String,val selectedIcon: ImageVector,val unselectedIcon: ImageVector)
@Composable fun MousikeBottomBar(currentScreen: NavScreen,onNavigate: (NavScreen)->Unit,modifier: Modifier=Modifier) {
    val items=listOf(
        NavItem(NavScreen.HOME,"Home",Icons.Filled.Home,Icons.Outlined.Home),
        NavItem(NavScreen.SEARCH,"Search",Icons.Filled.Search,Icons.Outlined.Search),
        NavItem(NavScreen.EXPLORE,"Explore",Icons.Filled.Explore,Icons.Outlined.Explore),
        NavItem(NavScreen.LIBRARY,"Library",Icons.Filled.LibraryMusic,Icons.Outlined.LibraryMusic),
        NavItem(NavScreen.SETTINGS,"Settings",Icons.Filled.Settings,Icons.Outlined.Settings))
    NavigationBar(modifier=modifier.fillMaxWidth().testTag("bottom_navigation"),containerColor=CharcoalSurface,
        windowInsets=WindowInsets(0,0,0,0),tonalElevation=0.dp) {
        items.forEach {item ->
            val selected=currentScreen==item.screen || item.screen==NavScreen.SETTINGS && currentScreen==NavScreen.ABOUT
            NavigationBarItem(selected=selected,onClick={onNavigate(item.screen)},
                icon={Icon(if(selected)item.selectedIcon else item.unselectedIcon,item.label,Modifier.size(24.dp))},
                label=if(LocalAppearance.current.showNavLabels){{Text(item.label,fontFamily=Roboto,fontSize=11.sp,maxLines=1,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium)}}else null,
                colors=NavigationBarItemDefaults.colors(selectedIconColor=MousikeGreen,selectedTextColor=TextPrimary,
                    indicatorColor=MousikeGreen.copy(alpha=.14f),unselectedIconColor=TextMuted,unselectedTextColor=TextMuted),
                modifier=Modifier.testTag("nav_${item.label.lowercase()}"))
        }
    }
}
