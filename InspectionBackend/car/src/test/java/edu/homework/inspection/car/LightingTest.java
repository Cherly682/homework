package edu.homework.inspection.car;

import edu.homework.inspection.common.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightingTest {
    @Test
    void centerLightsNineCells() {
        assertEquals(9, Lighting.area3x3(5, 5, new Point(2, 2)).size());
    }

    @Test
    void cornerLightsFourCells() {
        assertEquals(4, Lighting.area3x3(5, 5, new Point(0, 0)).size());
    }
}
