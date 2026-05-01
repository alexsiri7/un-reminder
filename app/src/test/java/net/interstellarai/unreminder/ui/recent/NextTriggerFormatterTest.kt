package net.interstellarai.unreminder.ui.recent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class NextTriggerFormatterTest {

    @Test
    fun `NotScheduled renders literal not scheduled`() {
        val now = Instant.parse("2026-05-01T14:00:00Z")
        assertEquals(
            "not scheduled",
            formatNextTrigger(NextTriggerState.NotScheduled, now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `Scheduled today future renders next HH mm`() {
        val now = Instant.parse("2026-05-01T14:00:00Z")
        val at = Instant.parse("2026-05-01T14:35:00Z")
        assertEquals(
            "next: 14:35",
            formatNextTrigger(NextTriggerState.Scheduled(at), now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `Scheduled past on same local day still uses today branch`() {
        val now = Instant.parse("2026-05-01T14:00:00Z")
        val at = Instant.parse("2026-05-01T09:00:00Z")
        assertEquals(
            "next: 09:00",
            formatNextTrigger(NextTriggerState.Scheduled(at), now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `Scheduled tomorrow renders next tomorrow HH mm`() {
        val now = Instant.parse("2026-05-01T22:00:00Z")
        val at = Instant.parse("2026-05-02T09:30:00Z")
        assertEquals(
            "next: tomorrow 09:30",
            formatNextTrigger(NextTriggerState.Scheduled(at), now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `Scheduled day after tomorrow renders MMM d HH mm`() {
        val now = Instant.parse("2026-05-01T14:00:00Z")
        val at = Instant.parse("2026-05-03T09:00:00Z")
        assertEquals(
            "next: May 3 09:00",
            formatNextTrigger(NextTriggerState.Scheduled(at), now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `Scheduled honors passed ZoneId for cross-zone date boundary`() {
        // 2026-05-01T22:00:00Z is 2026-05-02T07:00 in Tokyo (UTC+9), which is
        // "tomorrow" relative to a "now" that is 23:00 Tokyo time on May 1.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = Instant.parse("2026-05-01T14:00:00Z") // 2026-05-01T23:00 Tokyo
        val at = Instant.parse("2026-05-01T22:00:00Z") // 2026-05-02T07:00 Tokyo
        assertEquals(
            "next: tomorrow 07:00",
            formatNextTrigger(NextTriggerState.Scheduled(at), now, tokyo),
        )
    }

    @Test
    fun `Scheduled across spring-forward DST still classifies tomorrow correctly`() {
        // 2026-03-08 02:00 → 03:00 EST→EDT spring-forward in America/New_York.
        // "tomorrow" must be classified as the next calendar day, not now+24h.
        val ny = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-03-07T22:00:00Z") // 2026-03-07 17:00 NY (EST)
        val at = Instant.parse("2026-03-08T13:00:00Z") // 2026-03-08 09:00 NY (EDT)
        assertEquals(
            "next: tomorrow 09:00",
            formatNextTrigger(NextTriggerState.Scheduled(at), now, ny),
        )
    }

    @Test
    fun `Scheduled tomorrow across year boundary classifies as tomorrow`() {
        // Dec 31 → Jan 1 must still classify as "tomorrow", not fall through to MMM d.
        val now = Instant.parse("2026-12-31T22:00:00Z")
        val at = Instant.parse("2027-01-01T09:30:00Z")
        assertEquals(
            "next: tomorrow 09:30",
            formatNextTrigger(NextTriggerState.Scheduled(at), now, ZoneOffset.UTC),
        )
    }
}
