package edu.homework.inspection.car;

import edu.homework.inspection.common.Point;

import java.util.ArrayList;
import java.util.List;

public final class Lighting {
    private Lighting() {
    }

    public static List<Point> area3x3(int width, int height, Point center) {
        List<Point> points = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = center.getX() + dx;
                int ny = center.getY() + dy;
                if (nx >= 0 && nx < height && ny >= 0 && ny < width) {
                    points.add(new Point(nx, ny));
                }
            }
        }
        return points;
    }
}
