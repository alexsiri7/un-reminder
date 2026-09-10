package net.interstellarai.unreminder.service.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpriteResolverTest {

    private val resolver = SpriteResolver()

    private val catalogue = MascotSprites.entries

    @Test
    fun `known tag resolves to its own drawable`() {
        val third = catalogue[2]
        assertEquals(third.drawableRes, resolver.resolve(third.tag, triggerId = 0L, catalogue = catalogue))
    }

    @Test
    fun `known tag wins over the rotation slot for the same trigger id`() {
        val tagged = catalogue[5]
        val rotationSlot = catalogue[0L.mod(catalogue.size)]
        assertNotEquals(rotationSlot.drawableRes, tagged.drawableRes)
        assertEquals(tagged.drawableRes, resolver.resolve(tagged.tag, triggerId = 0L, catalogue = catalogue))
    }

    @Test
    fun `unknown tag falls back to rotation`() {
        assertEquals(
            catalogue[7L.mod(catalogue.size)].drawableRes,
            resolver.resolve("sprite_from_a_retired_sheet", triggerId = 7L, catalogue = catalogue)
        )
    }

    @Test
    fun `null tag falls back to rotation`() {
        assertEquals(
            catalogue[7L.mod(catalogue.size)].drawableRes,
            resolver.resolve(null, triggerId = 7L, catalogue = catalogue)
        )
    }

    @Test
    fun `empty tag falls back to rotation`() {
        assertEquals(
            catalogue[7L.mod(catalogue.size)].drawableRes,
            resolver.resolve("", triggerId = 7L, catalogue = catalogue)
        )
    }

    @Test
    fun `fallback is stable for a given trigger id`() {
        val first = resolver.resolve(null, triggerId = 42L, catalogue = catalogue)
        val second = resolver.resolve(null, triggerId = 42L, catalogue = catalogue)
        assertEquals(first, second)
    }

    @Test
    fun `fallback rotates across the whole catalogue`() {
        val resolved = (0L until catalogue.size.toLong()).map { resolver.resolve(null, it, catalogue) }.toSet()
        assertEquals(catalogue.size, resolved.size)
    }

    @Test
    fun `fallback wraps at the catalogue size`() {
        assertEquals(
            resolver.resolve(null, triggerId = 0L, catalogue = catalogue),
            resolver.resolve(null, triggerId = catalogue.size.toLong(), catalogue = catalogue)
        )
    }

    @Test
    fun `negative trigger id maps into the catalogue`() {
        assertEquals(
            catalogue[catalogue.size - 1].drawableRes,
            resolver.resolve(null, triggerId = -1L, catalogue = catalogue)
        )
    }

    @Test
    fun `fallback derives the rotation size from a catalogue of a different size`() {
        val threeSprites = catalogue.take(3)
        assertEquals(threeSprites[1].drawableRes, resolver.resolve(null, triggerId = 4L, catalogue = threeSprites))
        assertEquals(threeSprites[0].drawableRes, resolver.resolve("gone", triggerId = 9L, catalogue = threeSprites))

        val singleSprite = catalogue.take(1)
        assertEquals(singleSprite[0].drawableRes, resolver.resolve(null, triggerId = 7L, catalogue = singleSprite))
    }

    @Test
    fun `tag outside the supplied catalogue rotates even when the full catalogue knows it`() {
        val threeSprites = catalogue.take(3)
        val absent = catalogue[10]
        assertEquals(
            threeSprites[1L.mod(threeSprites.size)].drawableRes,
            resolver.resolve(absent.tag, triggerId = 1L, catalogue = threeSprites)
        )
    }

    @Test
    fun `default catalogue is the sprite vocabulary`() {
        val sprite = MascotSprites.entries[3]
        assertEquals(sprite.drawableRes, resolver.resolve(sprite.tag, triggerId = 0L))
    }

    @Test
    fun `vocabulary tags are unique and non-blank`() {
        assertEquals(MascotSprites.entries.size, MascotSprites.entries.map { it.tag }.toSet().size)
        assertEquals(emptyList<MascotSprite>(), MascotSprites.entries.filter { it.tag.isBlank() || it.description.isBlank() })
    }
}
