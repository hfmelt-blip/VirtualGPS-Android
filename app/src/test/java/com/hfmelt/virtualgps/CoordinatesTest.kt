package com.hfmelt.virtualgps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoordinatesTest {
    @Test fun arabicAndPersianDigits() {
        assertEquals(Coordinates(33.315, -44.36), Coordinates.parse("٣٣٫٣١٥", "−۴۴٫۳۶"))
    }
    @Test fun boundaries() {
        assertEquals(Coordinates(-90.0, 180.0), Coordinates.parse("-90", "180"))
    }
    @Test fun invalidInput() {
        listOf("NaN", "Infinity", "91", "", "abc").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { Coordinates.parse(value, "44") }
        }
        assertThrows(IllegalArgumentException::class.java) { Coordinates.parse("33", "181") }
    }
}
