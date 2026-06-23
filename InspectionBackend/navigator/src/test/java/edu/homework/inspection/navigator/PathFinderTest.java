package edu.homework.inspection.navigator;

import edu.homework.inspection.common.Point;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFinderTest {
    @Test
    void algorithmsFindPathAroundObstacle() {
        boolean[] blocked = new boolean[25];
        blocked[1 * 5 + 0] = true;
        blocked[1 * 5 + 1] = true;
        blocked[1 * 5 + 2] = true;
        boolean[] explored = new boolean[25];
        GridMap map = new GridMap(5, 5, blocked, explored);
        Point start = new Point(0, 0);
        Point end = new Point(4, 4);

        assertValid(new AStarPathFinder().findPath(start, end, map), end, map);
        assertValid(new DijkstraPathFinder().findPath(start, end, map), end, map);
        assertValid(new BidirectionalAStarPathFinder().findPath(start, end, map), end, map);
    }

    private void assertValid(List<Point> path, Point end, GridMap map) {
        assertFalse(path.isEmpty());
        assertTrue(path.get(path.size() - 1).equals(end));
        for (Point point : path) {
            assertFalse(map.isBlocked(point));
        }
    }
}
