package org.jetlinks.marketplace;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapabilityVersionRangeTest {

    @Test
    public void shouldMatchBlankRange() {
        assertTrue(CapabilityVersionRange.matches("1.0.0", null));
        assertTrue(CapabilityVersionRange.matches("1.0.0", ""));
    }

    @Test
    public void shouldMatchCombinedRange() {
        assertTrue(CapabilityVersionRange.matches("1.2.0", ">=1.0.0,<2.0.0"));
        assertFalse(CapabilityVersionRange.matches("2.0.0", ">=1.0.0,<2.0.0"));
    }

    @Test
    public void shouldTreatPlainVersionAsExactMatch() {
        assertTrue(CapabilityVersionRange.matches("1.0.0", "1.0"));
        assertFalse(CapabilityVersionRange.matches("1.0.1", "1.0"));
    }

    @Test
    public void shouldRejectInvalidRange() {
        assertInvalidRange(">=1.0.0,");
        assertInvalidRange(">=");
    }

    private void assertInvalidRange(String versionRange) {
        boolean thrown = false;
        try {
            CapabilityVersionRange.matches("1.0.0", versionRange);
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        assertTrue(thrown);
    }
}
