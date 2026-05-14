package me.ibrahimrafi.wififileshare.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpUtilsTest {

    // --- parseRangeHeader ---------------------------------------------------

    @Test fun `range with start and end`() {
        assertEquals(0L to 99L, parseRangeHeader("bytes=0-99", 1000))
    }

    @Test fun `range open-ended end uses size minus one`() {
        assertEquals(500L to 999L, parseRangeHeader("bytes=500-", 1000))
    }

    @Test fun `range suffix returns last N bytes`() {
        assertEquals(900L to 999L, parseRangeHeader("bytes=-100", 1000))
    }

    @Test fun `range suffix larger than size clamps`() {
        assertEquals(0L to 999L, parseRangeHeader("bytes=-5000", 1000))
    }

    @Test fun `range with end past size clamps to size minus one`() {
        assertEquals(0L to 999L, parseRangeHeader("bytes=0-5000", 1000))
    }

    @Test fun `range with start equal to size is rejected`() {
        assertNull(parseRangeHeader("bytes=1000-", 1000))
    }

    @Test fun `range with end before start is rejected`() {
        assertNull(parseRangeHeader("bytes=50-10", 1000))
    }

    @Test fun `multi-range not supported`() {
        assertNull(parseRangeHeader("bytes=0-99,200-299", 1000))
    }

    @Test fun `range without bytes prefix is rejected`() {
        assertNull(parseRangeHeader("0-99", 1000))
    }

    @Test fun `range on empty file is rejected`() {
        assertNull(parseRangeHeader("bytes=0-99", 0))
    }

    // --- parseContentRange --------------------------------------------------

    @Test fun `content range parses canonical form`() {
        val r = parseContentRange("bytes 0-99/1000")
        assertEquals(ContentRange(0L, 99L, 1000L), r)
    }

    @Test fun `content range without bytes prefix returns null`() {
        assertNull(parseContentRange("0-99/1000"))
    }

    @Test fun `content range with end past total is rejected`() {
        assertNull(parseContentRange("bytes 0-9999/1000"))
    }

    @Test fun `content range with end equal to total is rejected`() {
        assertNull(parseContentRange("bytes 0-1000/1000"))
    }

    @Test fun `content range with malformed pieces returns null`() {
        assertNull(parseContentRange("bytes 0-/1000"))
        assertNull(parseContentRange("bytes -99/1000"))
        assertNull(parseContentRange("bytes 0-99/"))
        assertNull(parseContentRange("bytes 0-99"))
        assertNull(parseContentRange(null))
        assertNull(parseContentRange(""))
    }

    // --- percentEncodeFileName ---------------------------------------------

    @Test fun `percent encode strips CR and LF to prevent header injection`() {
        val encoded = percentEncodeFileName("evil\r\nX-Injected: header.txt")
        // No literal CR/LF anywhere in the encoded output.
        assert(!encoded.contains('\r'))
        assert(!encoded.contains('\n'))
    }

    @Test fun `percent encode keeps simple filename round-trippable`() {
        val encoded = percentEncodeFileName("hello world.txt")
        assertEquals("hello%20world.txt", encoded)
    }

    @Test fun `percent encode handles unicode`() {
        // Just verify it doesn't throw and produces something non-empty.
        val encoded = percentEncodeFileName("résumé.pdf")
        assert(encoded.isNotEmpty())
    }

    // --- percentEncodePath -------------------------------------------------

    @Test fun `percent encode path preserves slashes`() {
        assertEquals("foo/bar%20baz/qux.txt", percentEncodePath("foo/bar baz/qux.txt"))
    }

    @Test fun `percent encode path on single segment`() {
        assertEquals("hello%20world", percentEncodePath("hello world"))
    }
}
