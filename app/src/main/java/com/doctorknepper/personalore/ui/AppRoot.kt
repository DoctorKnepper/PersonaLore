package com.doctorknepper.personalore.ui

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.doctorknepper.personalore.ui.screens.CalendarScreen
import com.doctorknepper.personalore.ui.screens.CategoryScreen
import com.doctorknepper.personalore.ui.screens.EditorScreen
import com.doctorknepper.personalore.ui.screens.EntryViewScreen
import com.doctorknepper.personalore.ui.screens.InsightsScreen
import com.doctorknepper.personalore.ui.screens.JournalScreen
import com.doctorknepper.personalore.ui.screens.LockScreen
import com.doctorknepper.personalore.ui.screens.PromptsScreen
import com.doctorknepper.personalore.ui.screens.SearchScreen
import com.doctorknepper.personalore.ui.screens.SeriesScreen
import com.doctorknepper.personalore.ui.screens.SettingsScreen
import java.time.LocalDate

sealed interface Route {
    data class Editor(val entryId: String? = null, val date: LocalDate? = null, val promptId: String? = null) : Route
    data class View(val entryId: String) : Route
    data class Search(val tag: String? = null) : Route
    data class Category(val name: String) : Route
    data class Series(val id: String) : Route
    data object Settings : Route
}

enum class Tab(val label: String, val icon: ImageVector) {
    JOURNAL("Journal", Icons.Outlined.MenuBook),
    CALENDAR("Calendar", Icons.Outlined.CalendarMonth),
    PROMPTS("Prompts", Icons.Outlined.AutoAwesome),
    INSIGHTS("Insights", Icons.Outlined.Insights),
}

class Nav {
    val stack = mutableStateListOf<Route>()
    var tab by mutableStateOf(Tab.JOURNAL)
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    /** Replace the top screen (e.g. after saving an entry, show it instead of the editor). */
    fun replace(r: Route) { pop(); push(r) }
}

val LocalNav = compositionLocalOf<Nav> { error("no nav") }

@Composable
fun AppRoot(activity: AppCompatActivity, locked: Boolean, onUnlocked: () -> Unit) {
    val nav = remember { Nav() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (locked) {
            LockScreen(activity, onUnlocked)
            return@Surface
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalNav provides nav) {
            val top = nav.stack.lastOrNull()
            BackHandler(enabled = top != null && top !is Route.Editor) { nav.pop() }
            BackHandler(enabled = top == null && nav.tab != Tab.JOURNAL) { nav.tab = Tab.JOURNAL }
            AnimatedContent(targetState = top, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { route ->
                when (route) {
                    null -> Home(nav)
                    is Route.Editor -> EditorScreen(route)
                    is Route.View -> EntryViewScreen(route.entryId)
                    is Route.Search -> SearchScreen(route.tag)
                    is Route.Category -> CategoryScreen(route.name)
                    is Route.Series -> SeriesScreen(route.id)
                    Route.Settings -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun Home(nav: Nav) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = nav.tab == t,
                        onClick = { nav.tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (nav.tab) {
                Tab.JOURNAL -> JournalScreen()
                Tab.CALENDAR -> CalendarScreen()
                Tab.PROMPTS -> PromptsScreen()
                Tab.INSIGHTS -> InsightsScreen()
            }
        }
    }
}
