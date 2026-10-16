package app.statusprep.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test fun formatsDuration() {
        assertEquals("0:05", Formatters.duration(5_000))
        assertEquals("1:05", Formatters.duration(65_000))
        assertEquals("1:01:01", Formatters.duration(3_661_000))
        assertEquals("—", Formatters.duration(null))
    }

    @Test fun formatsBytesAndBitrates() {
        assertEquals("1,5 Mo", Formatters.bytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("512 o", Formatters.bytes(512))
        assertEquals("2,5 Mb/s", Formatters.bitrate(2_500_000))
        assertEquals("96 kb/s", Formatters.bitrate(96_000))
        assertEquals("—", Formatters.bitrate(null))
    }

    @Test fun namesCodecs() {
        assertEquals("H.264", Formatters.videoCodec("video/avc"))
        assertEquals("H.265 (HEVC)", Formatters.videoCodec("video/hevc"))
        assertEquals("AAC", Formatters.audioCodec("audio/mp4a-latm"))
        assertEquals("—", Formatters.audioCodec(null))
    }

    @Test fun formatsDimensions() {
        assertEquals("1280 × 720", Formatters.dimensions(1280, 720))
        assertEquals("—", Formatters.dimensions(0, 720))
    }
}
