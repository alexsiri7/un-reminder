package net.interstellarai.unreminder.service.notification

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.internal.GeneratedComponentManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import net.interstellarai.unreminder.widget.WidgetRefresher
import org.junit.Before
import org.junit.Test

class NotificationActionReceiverTest {

    private val context: Context = mockk(relaxed = true)
    private val pendingResult: BroadcastReceiver.PendingResult = mockk(relaxUnitFun = true)
    private val notificationManager: NotificationManager = mockk(relaxUnitFun = true)
    private val triggerRepository: TriggerRepository = mockk(relaxUnitFun = true)
    private val dismissalTracker: DismissalTracker = mockk(relaxUnitFun = true)
    private val widgetRefresher: WidgetRefresher = mockk(relaxUnitFun = true)

    private val receiver = spyk(NotificationActionReceiver()).apply {
        triggerRepository = this@NotificationActionReceiverTest.triggerRepository
        dismissalTracker = this@NotificationActionReceiverTest.dismissalTracker
        widgetRefresher = this@NotificationActionReceiverTest.widgetRefresher
    }

    @Before
    fun setup() {
        // The Hilt base class injects from the application on every onReceive; the fields
        // are set by hand above, so the injector only has to exist.
        val injector: NotificationActionReceiver_GeneratedInjector = mockk(relaxUnitFun = true)
        val application = mockk<Application>(moreInterfaces = arrayOf(GeneratedComponentManager::class))
        every { (application as GeneratedComponentManager<*>).generatedComponent() } returns injector
        every { context.applicationContext } returns application
        every { context.getSystemService(NotificationManager::class.java) } returns notificationManager
        every { receiver.goAsync() } returns pendingResult
    }

    private fun actionIntent(action: String): Intent = mockk {
        every { getLongExtra(NotificationHelper.EXTRA_TRIGGER_ID, -1) } returns 42L
        every { getStringExtra(NotificationHelper.EXTRA_ACTION) } returns action
    }

    @Test
    fun `did it records the completion and refreshes the widget`() {
        receiver.onReceive(context, actionIntent(NotificationHelper.ACTION_COMPLETED))

        verify(timeout = 2_000) { pendingResult.finish() }
        coVerify(exactly = 1) { triggerRepository.updateOutcome(42L, TriggerStatus.COMPLETED) }
        coVerify(exactly = 1) { dismissalTracker.onCompleted(42L) }
        verify(exactly = 1) { notificationManager.cancel(42) }
        verify(exactly = 1) { widgetRefresher.refresh() }
    }

    @Test
    fun `dismiss records the dismissal and refreshes the widget`() {
        receiver.onReceive(context, actionIntent(NotificationHelper.ACTION_DISMISSED))

        verify(timeout = 2_000) { pendingResult.finish() }
        coVerify(exactly = 1) { triggerRepository.updateOutcome(42L, TriggerStatus.DISMISSED) }
        coVerify(exactly = 1) { dismissalTracker.onDismissed(42L) }
        verify(exactly = 1) { widgetRefresher.refresh() }
    }
}
