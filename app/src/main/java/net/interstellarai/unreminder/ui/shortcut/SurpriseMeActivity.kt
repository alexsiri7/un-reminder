package net.interstellarai.unreminder.ui.shortcut

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class SurpriseMeActivity : ComponentActivity() {

    @Inject lateinit var triggerRepository: TriggerRepository
    @Inject lateinit var triggerPipeline: TriggerPipeline

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            val trigger = TriggerEntity(
                scheduledAt = Instant.now(),
                status = TriggerStatus.SCHEDULED,
                source = "MANUAL"
            )
            val id = triggerRepository.insert(trigger)
            triggerPipeline.execute(id)
            withContext(Dispatchers.Main) { finish() }
        }
    }
}
