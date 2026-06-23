package edu.homework.inspection.navigator;

import edu.homework.inspection.common.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class BidirectionalAStarPathFinder implements PathFinder {
    @Override
    public List<Point> findPath(Point start, Point end, GridMap map) {
        if (start.equals(end)) {
            return Collections.emptyList();
        }

        PriorityQueue<NodeScore> startOpen = new PriorityQueue<>(Comparator.comparingInt(NodeScore::score));
        PriorityQueue<NodeScore> endOpen = new PriorityQueue<>(Comparator.comparingInt(NodeScore::score));
        Map<Point, Point> fromStart = new HashMap<>();
        Map<Point, Point> fromEnd = new HashMap<>();
        Map<Point, Integer> startCost = new HashMap<>();
        Map<Point, Integer> endCost = new HashMap<>();
        Set<Point> closedStart = new HashSet<>();
        Set<Point> closedEnd = new HashSet<>();

        startCost.put(start, 0);
        endCost.put(end, 0);
        startOpen.add(new NodeScore(start, start.manhattan(end)));
        endOpen.add(new NodeScore(end, end.manhattan(start)));

        while (!startOpen.isEmpty() && !endOpen.isEmpty()) {
            Point meet = step(startOpen, closedStart, closedEnd, fromStart, startCost, map, end);
            if (meet != null) {
                return merge(start, end, meet, fromStart, fromEnd);
            }
            meet = step(endOpen, closedEnd, closedStart, fromEnd, endCost, map, start);
            if (meet != null) {
                return merge(start, end, meet, fromStart, fromEnd);
            }
        }
        return Collections.emptyList();
    }

    private Point step(PriorityQueue<NodeScore> open,
                       Set<Point> ownClosed,
                       Set<Point> otherClosed,
                       Map<Point, Point> cameFrom,
                       Map<Point, Integer> cost,
                       GridMap map,
                       Point goal) {
        if (open.isEmpty()) {
            return null;
        }
        Point current = open.poll().point;
        if (!ownClosed.add(current)) {
            return null;
        }
        if (otherClosed.contains(current)) {
            return current;
        }
        for (Point next : map.neighbors(current)) {
            int newCost = cost.get(current) + map.moveCost(next);
            if (!cost.containsKey(next) || newCost < cost.get(next)) {
                cost.put(next, newCost);
                cameFrom.put(next, current);
                open.add(new NodeScore(next, newCost + next.manhattan(goal)));
            }
        }
        return null;
    }

    private List<Point> merge(Point start, Point end, Point meet,
                              Map<Point, Point> fromStart,
                              Map<Point, Point> fromEnd) {
        List<Point> first = new ArrayList<>();
        Point current = meet;
        while (!current.equals(start)) {
            first.add(current);
            current = fromStart.get(current);
            if (current == null) {
                return Collections.emptyList();
            }
        }
        Collections.reverse(first);

        current = meet;
        while (!current.equals(end)) {
            current = fromEnd.get(current);
            if (current == null) {
                return Collections.emptyList();
            }
            first.add(current);
        }
        return first;
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
