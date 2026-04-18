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

    fun pick(triggerId: Long): String =
        EMOJIS[Math.floorMod(triggerId, EMOJIS.size.toLong()).toInt()]
}
