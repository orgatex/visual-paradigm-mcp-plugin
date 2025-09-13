package com.brunnen.vp.usecase.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test class for UseCaseUtils
 */
public class UseCaseUtilsTest {

    @Test
    public void testIsValidUseCaseName() {
        assertTrue("Valid use case name should return true",
                   UseCaseUtils.isValidUseCaseName("Login User"));
        assertTrue("Valid use case name should return true",
                   UseCaseUtils.isValidUseCaseName("Manage Products"));

        assertFalse("Null name should return false",
                    UseCaseUtils.isValidUseCaseName(null));
        assertFalse("Empty name should return false",
                    UseCaseUtils.isValidUseCaseName(""));
        assertFalse("Whitespace name should return false",
                    UseCaseUtils.isValidUseCaseName("   "));
        assertFalse("Too short name should return false",
                    UseCaseUtils.isValidUseCaseName("AB"));
    }

    @Test
    public void testIsValidActorName() {
        assertTrue("Valid actor name should return true",
                   UseCaseUtils.isValidActorName("User"));
        assertTrue("Valid actor name should return true",
                   UseCaseUtils.isValidActorName("Administrator"));

        assertFalse("Null name should return false",
                    UseCaseUtils.isValidActorName(null));
        assertFalse("Empty name should return false",
                    UseCaseUtils.isValidActorName(""));
        assertFalse("Whitespace name should return false",
                    UseCaseUtils.isValidActorName("   "));
        assertFalse("Too short name should return false",
                    UseCaseUtils.isValidActorName("A"));
    }
}
