package net.interstellarai.unreminder.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import dagger.hilt.android.EntryPointAccessors
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.IScope
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class MarkDoneActionTest {

    private val context: Context = mockk(relaxed = true)
    private val glanceId: GlanceId = mockk()
    private val completionRecorder: WidgetCompletionRecorder = mockk(relaxUnitFun = true)
    private val widgetRefresher: WidgetRefresher = mockk(relaxUnitFun = true)
    private val sentryScope: IScope = mockk(relaxUnitFun = true)
    private val scopeCallback = slot<ScopeCallback>()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), capture(scopeCallback)) } returns SentryId.EMPTY_ID
        mockkStatic(EntryPointAccessors::class)
        val entryPoint: WidgetEntryPoint = mockk {
            every { completionRecorder() } returns this@MarkDoneActionTest.completionRecorder
            every { widgetRefresher() } returns this@MarkDoneActionTest.widgetRefresher
        }
        every { EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java) } returns entryPoint
    }

    @After
    fun tearDown() {
        unmockkStatic(EntryPointAccessors::class, Sentry::class, Log::class)
    }

    private val shown = DoableHabit(id = 5L, name = "stretch", emoji = "🧘", text = "x", variationId = 50L, spriteTag = null)

    private suspend fun tap(parameters: ActionParameters = MarkDoneAction.parameters(shown)) =
        MarkDoneAction().onAction(context, glanceId, parameters)

    @Test
    fun `records the completion of the shown variant and then refreshes`() = runTest {
        tap()

        coVerify(exactly = 1) { completionRecorder.complete(5L, 50L) }
        coVerify(exactly = 1) { widgetRefresher.refreshNow() }
        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `a fallback habit completes without a variant`() = runTest {
        tap(MarkDoneAction.parameters(shown.copy(variationId = null)))

        coVerify(exactly = 1) { completionRecorder.complete(5L, null) }
    }

    @Test
    fun `parameters from before the variant was stored still complete the habit`() = runTest {
        tap(actionParametersOf(MarkDoneAction.HABIT_ID to 5L))

        coVerify(exactly = 1) { completionRecorder.complete(5L, null) }
    }

    @Test
    fun `a failed write is reported and the widget still refreshes`() = runTest {
        coEvery { completionRecorder.complete(5L, 50L) } throws RuntimeException("boom")

        tap()

        coVerify(exactly = 1) { widgetRefresher.refreshNow() }
        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        scopeCallback.captured.run(sentryScope)
        verify { sentryScope.setTag("component", "widget-done") }
        verify { sentryScope.setTag("habit_id", "5") }
    }

    @Test
    fun `a failed refresh is reported instead of escaping the tap`() = runTest {
        coEvery { widgetRefresher.refreshNow() } throws RuntimeException("boom")

        tap()

        coVerify(exactly = 1) { completionRecorder.complete(5L, 50L) }
        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        scopeCallback.captured.run(sentryScope)
        verify { sentryScope.setTag("component", "widget-done-refresh") }
        verify { sentryScope.setTag("habit_id", "5") }
    }
}
