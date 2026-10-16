package app.statusprep.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OutputPlannerTest {

    private val balanced = EncodingConfig.specFor(ProfileSelection(ProfileId.BALANCED))
    private val detailed = EncodingConfig.specFor(ProfileSelection(ProfileId.DETAILED))

    private fun src(
        w: Int, h: Int,
        fps: Float? = 30f,
        bitrate: Long? = null,
        mime: String? = "video/avc",
        audio: Boolean = true,
        durationMs: Long? = 60_000,
    ) = SourceMedia(w, h, durationMs, fps, bitrate, mime, audio)

    private fun assertAspectPreserved(sw: Int, sh: Int, p: OutputPlan) {
        // L'arrondi pair peut décaler chaque côté d'au plus 1 px.
        val expectedH = p.width.toDouble() * sh / sw
        assertTrue("aspect ${sw}x$sh -> ${p.width}x${p.height}", abs(expectedH - p.height) <= 2.0)
    }

    // ---- Dimensions ----------------------------------------------------------------------

    @Test fun landscape1080pToBalancedGives720p() {
        val p = OutputPlanner.plan(src(1920, 1080), balanced)
        assertEquals(1280 to 720, p.width to p.height)
        assertTrue(p.needsResize)
    }

    @Test fun portrait1080x1920ToBalancedGives720x1280() {
        val p = OutputPlanner.plan(src(1080, 1920), balanced)
        assertEquals(720 to 1280, p.width to p.height)
    }

    @Test fun squareToBalancedFitsShortSide() {
        val p = OutputPlanner.plan(src(1080, 1080), balanced)
        assertEquals(720 to 720, p.width to p.height)
    }

    @Test fun portrait4kToDetailedGives1080x1920() {
        val p = OutputPlanner.plan(src(2160, 3840), detailed)
        assertEquals(1080 to 1920, p.width to p.height)
    }

    @Test fun neverUpscalesSmallerVideo() {
        val p = OutputPlanner.plan(src(640, 360), detailed)
        assertEquals(640 to 360, p.width to p.height)
        assertFalse(p.needsResize)
    }

    @Test fun smallerPortraitVideoIsKept() {
        val p = OutputPlanner.plan(src(540, 960), balanced)
        assertEquals(540 to 960, p.width to p.height)
        assertFalse(p.needsResize)
    }

    @Test fun exactProfileSizeNeedsNoResize() {
        val p = OutputPlanner.plan(src(1280, 720), balanced)
        assertFalse(p.needsResize)
    }

    @Test fun oddDimensionsBecomeEvenWithoutUpscaling() {
        val p = OutputPlanner.plan(src(1001, 563), balanced)
        assertEquals(0, p.width % 2)
        assertEquals(0, p.height % 2)
        assertTrue(p.width <= 1001 && p.height <= 563)
        assertTrue(p.needsResize)
    }

    @Test fun aspectRatioIsPreservedForVariousSources() {
        val sizes = listOf(
            1920 to 1080, 1080 to 1920, 1080 to 1080, 3840 to 2160, 1440 to 1080, 1080 to 1440,
            2340 to 1080, 1080 to 2340, 1001 to 563, 721 to 1281, 2048 to 858, 640 to 360,
        )
        for (spec in listOf(balanced, detailed)) {
            for ((w, h) in sizes) {
                val p = OutputPlanner.plan(src(w, h), spec)
                assertAspectPreserved(w, h, p)
                assertTrue(maxOf(p.width, p.height) <= maxOf(w, h))
                assertTrue(maxOf(p.width, p.height) <= spec.resolution.longSide)
                assertTrue(minOf(p.width, p.height) <= spec.resolution.shortSide)
            }
        }
    }

    @Test fun portraitAndLandscapeGiveMirroredResults() {
        val l = OutputPlanner.plan(src(1920, 1080), balanced)
        val p = OutputPlanner.plan(src(1080, 1920), balanced)
        assertEquals(l.width, p.height)
        assertEquals(l.height, p.width)
    }

    @Test fun rotationMetadataSwapsDisplaySize() {
        assertEquals(1080 to 1920, displaySize(1920, 1080, 90))
        assertEquals(1080 to 1920, displaySize(1920, 1080, 270))
        assertEquals(1080 to 1920, displaySize(1920, 1080, -90))
        assertEquals(1920 to 1080, displaySize(1920, 1080, 0))
        assertEquals(1920 to 1080, displaySize(1920, 1080, 180))
        // Une vidéo codée en paysage mais tournée de 90° est un portrait : cadre 720x1280.
        val (w, h) = displaySize(1920, 1080, 90)
        val p = OutputPlanner.plan(src(w, h), balanced)
        assertEquals(720 to 1280, p.width to p.height)
    }

    // ---- Débit --------------------------------------------------------------------------

    @Test fun balancedTargetsAbout2_5Mbps() {
        val p = OutputPlanner.plan(src(1920, 1080, bitrate = 20_000_000), balanced)
        assertEquals(2_500_000, p.videoBitrate)
        assertFalse(p.bitrateLimitedBySource)
    }

    @Test fun detailedTargetsAbout5Mbps() {
        val p = OutputPlanner.plan(src(1920, 1080, bitrate = 20_000_000), detailed)
        assertEquals(5_000_000, p.videoBitrate)
    }

    @Test fun lowBitrateSourceIsNotInflated() {
        val p = OutputPlanner.plan(src(1920, 1080, bitrate = 1_200_000), balanced)
        assertEquals(1_200_000, p.videoBitrate)
        assertTrue(p.bitrateLimitedBySource)
    }

    @Test fun efficientCodecSourceGetsHeadroom() {
        val p = OutputPlanner.plan(src(1920, 1080, bitrate = 1_200_000, mime = "video/hevc"), balanced)
        assertEquals(1_800_000, p.videoBitrate)
    }

    @Test fun unknownBitrateUsesProfileTarget() {
        val p = OutputPlanner.plan(src(1920, 1080, bitrate = null), balanced)
        assertEquals(2_500_000, p.videoBitrate)
    }

    @Test fun tinyVideoDoesNotWasteBitrate() {
        val p = OutputPlanner.plan(src(320, 180), detailed)
        assertTrue(p.videoBitrate < 5_000_000)
        assertTrue(p.videoBitrate >= EncodingConfig.MIN_VIDEO_BITRATE)
    }

    // ---- Cadence & audio -----------------------------------------------------------------

    @Test fun framesAbove30AreCapped() {
        assertEquals(30, OutputPlanner.plan(src(1920, 1080, fps = 60f), balanced).frameRateCap)
    }

    @Test fun lowerFrameRateIsNeverRaised() {
        assertNull(OutputPlanner.plan(src(1920, 1080, fps = 24f), balanced).frameRateCap)
        assertNull(OutputPlanner.plan(src(1920, 1080, fps = 30f), balanced).frameRateCap)
        assertNull(OutputPlanner.plan(src(1920, 1080, fps = 29.97f), balanced).frameRateCap)
    }

    @Test fun unknownFrameRateIsCappedDefensively() {
        assertEquals(30, OutputPlanner.plan(src(1920, 1080, fps = null), balanced).frameRateCap)
    }

    @Test fun noAudioTrackMeansNoAudioBitrate() {
        assertNull(OutputPlanner.plan(src(1920, 1080, audio = false), balanced).audioBitrate)
    }

    @Test fun audioBitratesStayInTargetRange() {
        for (spec in listOf(balanced, detailed)) {
            val a = OutputPlanner.plan(src(1920, 1080), spec).audioBitrate!!
            assertTrue(a in 96_000..128_000)
        }
    }

    // ---- Estimation ----------------------------------------------------------------------

    @Test fun estimateIsDerivedFromBitratesAndDuration() {
        val p = OutputPlanner.plan(src(1920, 1080, durationMs = 10_000), balanced)
        // (2,5 Mb/s + 96 kb/s) * 10 s / 8 * 1,02
        assertEquals(3_310_500L, p.estimatedBytes)
    }

    @Test fun unknownDurationGivesNoEstimate() {
        assertNull(OutputPlanner.plan(src(1920, 1080, durationMs = null), balanced).estimatedBytes)
    }

    // ---- Profils -------------------------------------------------------------------------

    @Test fun customProfileUsesChosenResolutionAndBitrate() {
        val spec = EncodingConfig.specFor(ProfileSelection(ProfileId.CUSTOM, Resolution.P480, 1.5f))
        val p = OutputPlanner.plan(src(1080, 1920), spec)
        assertEquals(480 to 854, p.width to p.height)
        assertEquals(1_500_000, p.videoBitrate)
    }

    @Test fun customBitrateIsClampedToAllowedRange() {
        assertEquals(1_000_000, EncodingConfig.specFor(ProfileSelection(ProfileId.CUSTOM, customMbps = 0.1f)).videoBitrate)
        assertEquals(8_000_000, EncodingConfig.specFor(ProfileSelection(ProfileId.CUSTOM, customMbps = 20f)).videoBitrate)
    }

    @Test fun customResolutionsMatchPortraitAndLandscapeBoxes() {
        for (r in Resolution.entries) {
            assertTrue(r.longSide > r.shortSide)
            val land = OutputPlanner.fitDimensions(4000, 3000, r.longSide, r.shortSide)
            val port = OutputPlanner.fitDimensions(3000, 4000, r.longSide, r.shortSide)
            assertEquals(land.first, port.second)
            assertEquals(land.second, port.first)
        }
    }

    @Test fun predefinedProfilesMatchTheBrief() {
        assertEquals(Resolution.P720, balanced.resolution)
        assertEquals(2_500_000, balanced.videoBitrate)
        assertEquals(Resolution.P1080, detailed.resolution)
        assertEquals(5_000_000, detailed.videoBitrate)
        assertEquals(30, EncodingConfig.MAX_FPS)
    }
}
