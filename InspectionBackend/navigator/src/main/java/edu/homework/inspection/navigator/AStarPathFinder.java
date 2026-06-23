package edu.homework.inspection.navigator;

import edu.homework.inspection.common.Point;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class AStarPathFinder implements PathFinder {
    @Override
    public List<Point> findPath(Point start, Point end, GridMap map) {
        if (start.equals(end)) {
            return Collections.emptyList();
        }
        PriorityQueue<NodeScore> open = new PriorityQueue<>(Comparator.comparingInt(NodeScore::score));
        Map<Point, Point> cameFrom = new HashMap<>();
        Map<Point, Integer> cost = new HashMap<>();
        Set<Point> closed = new HashSet<>();

        cost.put(start, 0);
        open.add(new NodeScore(start, start.manhattan(end)));

        while (!open.isEmpty()) {
            Point current = open.poll().point;
            if (!closed.add(current)) {
                continue;
            }
            if (current.equals(end)) {
                return PathUtils.reconstruct(cameFrom, start, end);
            }
            for (Point next : map.neighbors(current)) {
                int newCost = cost.get(current) + map.moveCost(next);
                if (!cost.containsKey(next) || newCost < cost.get(next)) {
                    cost.put(next, newCost);
                    cameFrom.put(next, current);
                    open.add(new NodeScore(next, newCost + next.manhattan(end)));
                }
            }
        }
        return Collections.emptyList();
    }

    private static final class NodeScore {
        private final Point point;
        private final int score;

        private NodeScore(Point point, int score) {
            this.point = point;
            this.score = score;
        }

        private int score() {
            return score;
        }
    }
}
