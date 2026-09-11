package net.interstellarai.unreminder.service.notification

import java.time.LocalDate

object EveningInvitationWording {
    const val TITLE = "Still time today"

    val BODIES = listOf(
        "Nothing's been ticked off yet. Something small would still count.",
        "The day isn't over. Have a look at what you could do right now.",
        "One easy thing before the day closes? There are a few to pick from.",
        "Quiet day so far. A five-minute thing would make it count.",
        "Fancy doing one small thing tonight? There's a short list waiting.",
        "Still a bit of evening left. Something quick could go on the board.",
    )

    // Rotates by calendar day so consecutive evenings never repeat a line.
    fun body(date: LocalDate): String = BODIES[date.toEpochDay().mod(BODIES.size)]
}
