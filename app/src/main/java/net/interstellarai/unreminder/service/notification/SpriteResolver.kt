package net.interstellarai.unreminder.service.notification

import androidx.annotation.DrawableRes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a variant's sprite tag to a drawable. Variants generated before an artwork swap carry
 * tags the catalogue no longer knows, so an unresolvable (or absent) tag rotates by [rotationSeed]
 * instead of failing: the result is always a drawable the app can display. Notifications seed the
 * rotation with the trigger id; the menu and widget, which have no trigger, use the habit id so a
 * habit's fallback sprite stays put across refreshes.
 */
@Singleton
class SpriteResolver @Inject constructor() {

    @DrawableRes
    fun resolve(
        spriteTag: String?,
        rotationSeed: Long,
        catalogue: List<MascotSprite> = MascotSprites.entries,
    ): Int {
        val tagged = spriteTag?.let { tag -> catalogue.firstOrNull { it.tag == tag } }
        // .mod() (not %) ensures a non-negative index when rotationSeed is negative
        return (tagged ?: catalogue[rotationSeed.mod(catalogue.size)]).drawableRes
    }
}
