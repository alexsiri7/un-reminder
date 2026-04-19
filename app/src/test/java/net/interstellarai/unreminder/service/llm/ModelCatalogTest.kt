package net.interstellarai.unreminder.service.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `byId resolves every entry in all`() {
        // Contract: every catalog entry is retrievable by its declared id,
        // and the returned descriptor is identity-equal to the entry in
        // `all` (no cloning).
        ModelCatalog.all.forEach { entry ->
            val resolved = ModelCatalog.byId(entry.id)
            assertNotNull("byId returned null for '${entry.id}'", resolved)
            assertSame("byId must return the canonical instance", entry, resolved)
        }
    }

    @Test
    fun `byId returns null for an unknown id`() {
        assertNull(ModelCatalog.byId("definitely-not-in-the-catalog"))
    }

    @Test
    fun `default is a member of all`() {
        // Prevents the footgun where the default field is forgotten when the
        // catalog list gets reshuffled, leaving `byId(default.id)` returning
        // null on fresh installs.
        assertTrue(
            "ModelCatalog.default must appear in ModelCatalog.all",
            ModelCatalog.all.any { it.id == ModelCatalog.default.id },
        )
    }

    @Test
    fun `catalog contains gemma-4 and gemma-3 fallback`() {
        // The whole point of this PR: give users an escape hatch when Gemma 4
        // fails to load. If either entry gets dropped this test catches it
        // before release.
        assertNotNull(ModelCatalog.byId("gemma-4-e2b-it-litertlm"))
        assertNotNull(ModelCatalog.byId("gemma-3-1b-it-task"))
    }

    @Test
    fun `every descriptor has plausible sizeBytes`() {
        // Sanity floor/ceiling — on-device LLMs we ship are all between 100 MB
        // and 10 GB. Catches a typo like "2580L" (2.5 KB) that would make the
        // UI show "0.00 GB" and a user think the download was instant.
        ModelCatalog.all.forEach { desc ->
            assertTrue(
                "${desc.id}: sizeBytes=${desc.sizeBytes} is implausibly small",
                desc.sizeBytes >= 100_000_000L,
            )
            assertTrue(
                "${desc.id}: sizeBytes=${desc.sizeBytes} is implausibly large",
                desc.sizeBytes <= 10_000_000_000L,
            )
        }
    }

    @Test
    fun `every descriptor has a non-empty magicPrefix`() {
        // The worker uses magicPrefix for its post-download integrity check.
        // A zero-length prefix would accept any download including HTML error
        // bodies, which is the exact failure mode this PR's other tests guard
        // against.
        ModelCatalog.all.forEach { desc ->
            assertTrue(
                "${desc.id}: magicPrefix must not be empty",
                desc.magicPrefix.isNotEmpty(),
            )
            assertTrue(
                "${desc.id}: magicPrefix should be short enough to be a file header",
                desc.magicPrefix.size in 2..16,
            )
        }
    }

    @Test
    fun `each descriptor has a distinct fileName`() {
        // Two descriptors sharing a filename would clobber each other on disk
        // and silently corrupt the integrity check.
        val filenames = ModelCatalog.all.map { it.fileName }
        assertEquals(
            "all catalog entries must have unique fileName values",
            filenames.size,
            filenames.toSet().size,
        )
    }

    @Test
    fun `each descriptor has a distinct id`() {
        val ids = ModelCatalog.all.map { it.id }
        assertEquals(
            "all catalog entries must have unique id values",
            ids.size,
            ids.toSet().size,
        )
    }

    @Test
    fun `ModelDescriptor equals uses content equality for magicPrefix`() {
        // Default data-class equals on a ByteArray field is reference equality,
        // which makes set-membership and test assertions behave surprisingly.
        // The overridden equals keeps tests stable when callers reconstruct a
        // descriptor with a fresh byteArrayOf() call.
        val a = ModelCatalog.gemma4E2BLitertlm
        val b = a.copy(
            magicPrefix = byteArrayOf(*a.magicPrefix),
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = a.copy(magicPrefix = byteArrayOf(0x00))
        assertFalse("descriptors with different magicPrefix must not be equal", a == c)
    }

    @Test
    fun `gemma-3 fallback entry is flagged as having a placeholder URL`() {
        // Regression guard: if someone swaps the URL to a real CDN we should
        // notice because downstream code (PromptGeneratorImpl.initialize())
        // branches on ModelConfig.isPlaceholderUrl. This assertion is the
        // canonical "are we still waiting for the self-host?" signal.
        assertTrue(
            "Gemma 3 descriptor is expected to keep its placeholder URL until " +
                "we self-host the gated HF file",
            ModelConfig.isPlaceholderUrl(ModelCatalog.gemma3_1B_Task.url),
        )
    }

    @Test
    fun `gemma-4 default entry has a real URL`() {
        assertFalse(
            "Gemma 4 default must ship with a real download URL, not the placeholder",
            ModelConfig.isPlaceholderUrl(ModelCatalog.gemma4E2BLitertlm.url),
        )
    }
}
