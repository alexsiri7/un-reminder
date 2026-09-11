package net.interstellarai.unreminder.service.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app deliberately removed guilt from its nudges. An artwork swap must not sneak it back in
 * through the pictures, so no sprite may be named or described in terms of sadness, tiredness,
 * disappointment, scolding or pleading.
 */
class MascotSpritesTest {

    private val guiltWords = setOf(
        "sad", "sadly", "sadness", "unhappy", "gloomy", "glum", "crying", "cry", "cries", "tears", "sob", "sobbing",
        "tired", "exhausted", "weary", "sleepy", "drowsy", "yawning", "yawn", "slumped", "drained",
        "disappointed", "disappointment", "letdown", "sigh", "sighing", "frown", "frowning", "sulking", "sulk", "moping",
        "scolding", "scold", "scolds", "nagging", "nag", "wagging", "stern", "angry", "cross", "glaring", "glare",
        "pleading", "plead", "pleads", "begging", "beg", "begs", "desperate", "guilt", "guilty", "ashamed", "shame",
        "sorry", "apologetic",
    )

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }

    private fun offenders(catalogue: List<MascotSprite>): List<MascotSprite> = catalogue.filter { sprite ->
        (words(sprite.tag) + words(sprite.description)).any { it in guiltWords }
    }

    @Test
    fun `no sprite tag or description implies sadness, tiredness, disappointment, scolding or pleading`() {
        assertEquals(emptyList<MascotSprite>(), offenders(MascotSprites.entries))
    }

    @Test
    fun `the guard reads tags and descriptions word by word`() {
        val sadTag = MascotSprite("cat_sad_rain", "a cat holding an umbrella", 1)
        val scoldingDescription = MascotSprite("cat_umbrella", "a cat wagging a finger, scolding", 2)
        val fine = MascotSprite("safari_vine_swing", "a cat swinging on a jungle vine", 3)

        assertEquals(listOf(sadTag, scoldingDescription), offenders(listOf(sadTag, scoldingDescription, fine)))
        assertTrue(offenders(listOf(fine)).isEmpty())
    }
}
