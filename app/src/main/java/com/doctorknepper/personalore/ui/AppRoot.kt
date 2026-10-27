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
import com.doctorknepper.personalore.ui.screens.TrashScreen
import com.doctorknepper.personalore.data.Store
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.doctorknepper.personalore.ui.screens.RecoveryKeyDialog
import com.doctorknepper.personalore.ui.screens.SetPinDialog
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Route {
    data class Editor(val entryId: String? = null, val date: LocalDate? = null, val promptId: String? = null) : Route
    data class View(val entryId: String) : Route
    data class Search(val tag: String? = null) : Route
    data class Category(val name: String) : Route
    data class Series(val id: String) : Route
    data object Settings : Route
    data object Trash : Route
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

/** App-wide snackbar, so messages like "Moved to trash · Undo" survive leaving the screen that caused them. */
object Snacks {
    val host = SnackbarHostState()
    private val scope = MainScope()
    fun show(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val r = host.showSnackbar(message, actionLabel = action, withDismissAction = action == null,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

/**
 * Lets the app open the photo picker, permission dialogs and file pickers without the lock
 * kicking in as soon as they cover the screen.
 */
object AppLock {
    @Volatile var leftForPickerAt = 0L; private set
    fun leavingForPicker() { leftForPickerAt = System.currentTimeMillis() }
    fun consumePickerGrace(): Boolean {
        val ok = System.currentTimeMillis() - leftForPickerAt < 5 * 60_000
        leftForPickerAt = 0L
        return ok
    }
}

@Composable
fun AppRoot(activity: AppCompatActivity) {
    val nav = remember { Nav() }
    val unlocked = Store.unlocked
    var everUnlocked by remember { mutableStateOf(false) }
    if (unlocked) everUnlocked = true
    LaunchedEffect(Store.notice) { Store.notice?.let { Snacks.show(it); Store.notice = null } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            // The journal stays composed underneath a re-lock, so an unsaved draft isn't lost,
            // but it's hidden from screen readers and covered completely by the lock screen.
            if (everUnlocked) Box(Modifier.fillMaxSize().then(if (!unlocked) Modifier.clearAndSetSemantics {} else Modifier)) {
                CompositionLocalProvider(LocalNav provides nav) {
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
                            Route.Trash -> TrashScreen()
                        }
                    }
                }
            }
            if (!unlocked) LockScreen(activity)
            if (unlocked) {
                Store.recoveryKeyToShow?.let { rk -> RecoveryKeyDialog(rk) { Store.recoveryKeyToShow = null } }
                if (Store.mustSetNewPin) {
                    val scope = rememberCoroutineScope()
                    SetPinDialog(title = "Choose a new PIN", firstTime = false, onDismiss = null) { pin ->
                        scope.launch {
                            withContext(Dispatchers.Default) { Store.changePin(pin) }
                            Store.mustSetNewPin = false
                            Snacks.show("New PIN set. Your recovery key still works.")
                        }
                    }
                }
            }
            SnackbarHost(Snacks.host, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
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
