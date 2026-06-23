package edu.homework.inspection.navigator;

import edu.homework.inspection.common.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class PathUtils {
    private PathUtils() {
    }

    static List<Point> reconstruct(Map<Point, Point> cameFrom, Point start, Point end) {
        List<Point> path = new ArrayList<>();
        Point current = end;
        while (!current.equals(start)) {
            path.add(current);
            current = cameFrom.get(current);
            if (current == null) {
                return Collections.emptyList();
            }
        }
        Collections.reverse(path);
        return path;
    }
}
