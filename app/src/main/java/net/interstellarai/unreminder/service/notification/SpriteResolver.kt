package net.interstellarai.unreminder.service.notification

import androidx.annotation.DrawableRes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a variant's sprite tag to a drawable. Variants generated before an artwork swap carry
 * tags the catalogue no longer knows, so an unresolvable tag rotates by trigger id instead of
 * failing: the result is always a drawable the app can display.
 */
@Singleton
class SpriteResolver @Inject constructor() {

    @DrawableRes
    fun resolve(
        spriteTag: String?,
        triggerId: Long,
        catalogue: List<MascotSprite> = MascotSprites.entries,
    ): Int {
        val tagged = spriteTag?.let { tag -> catalogue.firstOrNull { it.tag == tag } }
        // .mod() (not %) ensures a non-negative index when triggerId is negative
        return (tagged ?: catalogue[triggerId.mod(catalogue.size)]).drawableRes
    }
}
