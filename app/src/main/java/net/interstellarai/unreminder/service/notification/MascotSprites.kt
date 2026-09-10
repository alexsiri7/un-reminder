package net.interstellarai.unreminder.service.notification

import androidx.annotation.DrawableRes
import net.interstellarai.unreminder.R

data class MascotSprite(
    val tag: String,
    val description: String,
    @DrawableRes val drawableRes: Int,
)

/**
 * The sprite vocabulary, generated from `art/mascot-sprites.json`. Single source of truth: the
 * tags offered to the worker, the drawables they resolve to, and the size of the rotation
 * fallback all derive from this list, so swapping the sheet needs no other change.
 */
object MascotSprites {
    val entries: List<MascotSprite> = listOf(
        MascotSprite("astronaut_zero_g", "a cat in an orange spacesuit tumbling weightless in deep space", R.drawable.mascot_01),
        MascotSprite("pirate_ship_rigging", "a cat in a skull-and-crossbones tricorn and eyepatch hauling on a ship's rigging", R.drawable.mascot_02),
        MascotSprite("wizard_starry_robe", "a cat in a star-covered wizard robe and pointed hat trailing sparks from a wand", R.drawable.mascot_03),
        MascotSprite("chef_pan_flip", "a cat in a chef's toque and apron flipping bacon and an egg out of a frying pan", R.drawable.mascot_04),
        MascotSprite("plain_star_wand", "an uncostumed cat holding up a star-tipped wand", R.drawable.mascot_05),
        MascotSprite("plain_sword_raised", "an uncostumed cat holding a plain sword raised at its side", R.drawable.mascot_06),
        MascotSprite("detective_magnifier", "a cat in a deerstalker crouching over pawprints with a magnifying glass", R.drawable.mascot_07),
        MascotSprite("diver_underwater", "a cat in a black wetsuit, mask and yellow fins gliding past fish underwater", R.drawable.mascot_09),
        MascotSprite("cowboy_lasso_swing", "a cat in a cowboy hat and red bandana swinging a lasso over the desert", R.drawable.mascot_10),
        MascotSprite("plain_rope_loop", "an uncostumed cat dangling a looped rope from one paw", R.drawable.mascot_11),
        MascotSprite("cowboy_pistol_raise", "a cat in a cowboy hat and waistcoat raising a revolver", R.drawable.mascot_12),
        MascotSprite("knight_armour_shield", "a cat in full plate armour with a red plume charging with sword and shield", R.drawable.mascot_13),
        MascotSprite("beekeeper_veil", "a cat in a white beekeeping suit and veiled hat greeting bees in a meadow", R.drawable.mascot_14),
        MascotSprite("painter_easel", "a cat in dungarees brushing bright streaks onto a canvas on an easel", R.drawable.mascot_15),
        MascotSprite("conductor_flag_wave", "a cat in a navy railway uniform and peaked cap waving a red flag from a carriage step", R.drawable.mascot_16),
        MascotSprite("bathrobe_stretch", "a cat in a white bathrobe and slippers stretching awake beside its bed", R.drawable.mascot_17),
        MascotSprite("raincoat_puddle_splash", "a cat in a yellow raincoat and wellies stamping in a puddle", R.drawable.mascot_18),
        MascotSprite("safari_vine_swing", "a cat in a safari hat with a camera swinging on a jungle vine", R.drawable.mascot_19),
        MascotSprite("rockstar_guitar", "a cat in a studded leather jacket playing a red electric guitar", R.drawable.mascot_20),
        MascotSprite("ballerina_pointe", "a cat in a pink tutu balancing on pointe on a wooden floor", R.drawable.mascot_21),
        MascotSprite("gardener_watering", "a cat in green overalls and a straw hat watering seedlings", R.drawable.mascot_22),
        MascotSprite("winter_snowball_throw", "a cat in a bobble hat and scarf hurling a snowball across the snow", R.drawable.mascot_23),
        MascotSprite("businessman_briefcase", "a cat in a grey suit and red tie dashing off with a briefcase as papers fly", R.drawable.mascot_24),
    )
}
