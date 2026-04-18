package com.alexsiri7.unreminder.service.notification

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmojiRotator @Inject constructor() {
    companion object {
        private val EMOJIS = listOf(
            "🌱", "💡", "🎯", "🔥", "⚡", "🌟", "🎪", "🏃", "🧘", "🎨",
            "🌈", "🦋", "🍃", "💫", "🎭", "🌊", "🏔️", "🎸", "🦅", "🌺"
        )
    }

    // .mod() (not %) ensures a non-negative index when triggerId is negative
    fun pick(triggerId: Long): String =
        EMOJIS[triggerId.mod(EMOJIS.size)]
}
