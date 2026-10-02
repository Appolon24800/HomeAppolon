package dev.appolon.homelab.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderCutoutTest {
    private fun padding(
        cutout: HeaderCutout?,
        titleWidth: Int = 145,
        width: Int = 393,
        leftInset: Int = 0,
        titleOnLeft: Boolean = true,
    ) = headerCutoutTopPadding(
        width = width,
        titleWidth = titleWidth,
        actionsWidth = 148,
        barHeight = 64,
        cutouts = listOfNotNull(cutout),
        leftInset = leftInset,
        titlePadding = 16,
        gap = 8,
        titleOnLeft = titleOnLeft,
    )

    @Test fun noCutoutNeedsNoTopPadding() {
        assertEquals(0, padding(null))
    }

    @Test fun centerCameraFitsBetweenTitleAndActions() {
        assertEquals(0, padding(HeaderCutout(181, 0, 212, 32)))
    }

    @Test fun wideNotchReservesSpaceAboveHeader() {
        assertEquals(40, padding(HeaderCutout(100, 0, 293, 32)))
    }

    @Test fun largeFontOrNarrowScreenFallsBack() {
        assertEquals(40, padding(HeaderCutout(181, 0, 212, 32), titleWidth = 180))
        assertEquals(40, padding(HeaderCutout(145, 0, 175, 32), width = 320))
    }

    @Test fun leftCameraCannotCoverTitle() {
        assertEquals(40, padding(HeaderCutout(24, 0, 56, 32)))
    }

    @Test fun landscapeSideInsetAlreadyAvoidsCutout() {
        assertEquals(0, padding(HeaderCutout(0, 140, 32, 180), width = 800, leftInset = 32))
    }

    @Test fun tallCutoutCannotExtendIntoServiceList() {
        assertEquals(88, padding(HeaderCutout(181, 0, 212, 80)))
    }

    @Test fun rightToLeftHeaderAlsoFitsBesideCenterCamera() {
        assertEquals(0, padding(HeaderCutout(181, 0, 212, 32), titleOnLeft = false))
    }
}
