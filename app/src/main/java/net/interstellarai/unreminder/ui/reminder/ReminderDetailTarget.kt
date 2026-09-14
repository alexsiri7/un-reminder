package net.interstellarai.unreminder.ui.reminder

/** What the variant view was opened for: the two entry paths into one screen. */
sealed interface ReminderDetailTarget {
    /** A fired (or otherwise recorded) trigger, reached from a notification or the Recent list. */
    data class Trigger(val triggerId: Long) : ReminderDetailTarget

    /**
     * A pull-sourced row from the Now page or the widget. No trigger exists: opening records
     * nothing, and completing inserts a COMPLETED trigger through [PullCompletionRecorder]
     * exactly as "did it" on the row does. A null [variationId] is the level-description
     * fallback row.
     */
    data class Variant(val habitId: Long, val variationId: Long?) : ReminderDetailTarget
}
