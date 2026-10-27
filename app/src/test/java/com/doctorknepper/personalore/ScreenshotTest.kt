package com.doctorknepper.personalore

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.doctorknepper.personalore.data.Entry
import com.doctorknepper.personalore.data.PromptLibrary
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.Home
import com.doctorknepper.personalore.ui.LocalNav
import com.doctorknepper.personalore.ui.Nav
import com.doctorknepper.personalore.ui.PersonaLoreTheme
import com.doctorknepper.personalore.ui.Route
import com.doctorknepper.personalore.ui.Tab
import com.doctorknepper.personalore.ui.ThemeMode
import com.doctorknepper.personalore.ui.screens.CategoryScreen
import com.doctorknepper.personalore.ui.screens.EditorScreen
import com.doctorknepper.personalore.ui.screens.EntryViewScreen
import com.doctorknepper.personalore.ui.screens.SearchScreen
import com.doctorknepper.personalore.ui.screens.SeriesScreen
import com.doctorknepper.personalore.ui.screens.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renders each screen with sample data and saves PNGs to app/build/screens for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun seed() {
        Store.resetForTest()
        Store.init(ApplicationProvider.getApplicationContext(), TestKeyWrapper())
        val today = LocalDate.now()
        val prompts = PromptLibrary.categories.flatMap { it.prompts }
        val bodies = listOf(
            "Walked by the old house on Maple today. The porch swing is gone but the lilac bush is still there, bigger than I remember.",
            "Long day at work. I keep thinking about what Dad said about patience, and how I never understood it until now.",
            "Sat outside with coffee before anyone was up. Quiet mornings are where I find myself again.",
            "Called Sarah for the first time in a year. We laughed like nothing had changed.",
        )
        val tags = listOf(listOf("family", "memories"), listOf("work"), listOf("faith", "mornings"), listOf("friends"), listOf("childhood", "family"))
        for (i in 0 until 40) {
            if (i % 5 == 3) continue
            val d = today.minusDays(i.toLong())
            val p = if (i % 3 == 0) prompts[(i * 7) % prompts.size] else null
            Store.saveEntry(
                Entry(
                    id = "e$i", date = d, createdAt = i.toLong(), updatedAt = i.toLong(),
                    title = if (p == null && i % 2 == 0) "A quiet ${d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}" else "",
                    body = bodies[i % bodies.size], mood = listOf(4, 3, 5, 2, 4, 3, 4)[i % 7], tags = tags[i % tags.size],
                    photos = emptyList(), audio = emptyList(), promptId = p?.id, promptText = p?.text,
                ),
            )
        }
        // an "On this day" entry from last year
        Store.saveEntry(Entry("old", today.minusYears(1), 0, 0, "First snow", "The first snow of the year and the whole street went quiet.",
            5, listOf("memories"), emptyList(), emptyList(), null, null))
        Store.toggleFavorite(prompts[2].id)
        Store.startSeries("childhood7")
        Store.ensureDailyPrompt()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        val out = File("build/screens").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderScreens() {
        seed()
        assertTrue(Store.entries.size > 30)
        assertEquals(1, Store.entries.count { it.id == "old" })

        val nav = Nav()
        var screen: @Composable () -> Unit by mutableStateOf({ Home(nav) })
        var mode by mutableStateOf(ThemeMode.DARK)
        var colorway by mutableStateOf("amber")
        rule.setContent {
            PersonaLoreTheme(colorway, mode) {
                CompositionLocalProvider(LocalNav provides nav) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { screen() }
                }
            }
        }
        shot("01_journal")
        nav.tab = Tab.CALENDAR; shot("02_calendar")
        nav.tab = Tab.PROMPTS; shot("03_prompts")
        nav.tab = Tab.INSIGHTS; shot("04_insights")
        screen = { EditorScreen(Route.Editor(promptId = PromptLibrary.categories[8].prompts[1].id)) }; shot("05_editor_prompt")
        screen = { EntryViewScreen("e0") }; shot("06_entry")
        screen = { CategoryScreen("Spirituality") }; shot("07_category")
        screen = { SeriesScreen("childhood7") }; shot("08_series")
        screen = { SearchScreen("family") }; shot("09_search")
        screen = { SettingsScreen() }; shot("10_settings")
        Store.deleteEntry("e1"); Store.deleteEntry("e2")
        screen = { com.doctorknepper.personalore.ui.screens.TrashScreen() }; shot("13_trash")
        mode = ThemeMode.LIGHT; colorway = "ocean"
        nav.tab = Tab.JOURNAL; screen = { Home(nav) }; shot("11_journal_light_ocean")
        mode = ThemeMode.DARK; colorway = "rose"; nav.tab = Tab.INSIGHTS; shot("12_insights_rose")
    }
}
