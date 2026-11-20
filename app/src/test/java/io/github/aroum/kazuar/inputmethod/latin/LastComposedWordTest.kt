package io.github.aroum.kazuar.inputmethod.latin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class LastComposedWordTest {
    @Test fun correctionCanOnlyBeRevertedWhenWordAndSeparatorAreStillAtCursor() {
        val corrected = LastComposedWord(arrayListOf(), null, "teh", "the", " ", null, 0)
        assertTrue(corrected.matchesTextBeforeCursor("the "))
        assertFalse(corrected.matchesTextBeforeCursor(" the"))
        assertFalse(corrected.matchesTextBeforeCursor("the"))
        assertFalse(corrected.matchesTextBeforeCursor("he "))
        assertFalse(corrected.matchesTextBeforeCursor(null))

        val punctuation = LastComposedWord(arrayListOf(), null, "teh", "the", ",", null, 0)
        assertTrue(punctuation.matchesTextBeforeCursor("the,"))
        assertFalse(punctuation.matchesTextBeforeCursor("the "))
    }
}
