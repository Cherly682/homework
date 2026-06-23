package edu.homework.inspection.navigator;

import edu.homework.inspection.common.Point;

import java.util.List;

public interface PathFinder {
    List<Point> findPath(Point start, Point end, GridMap map);
}
