package edu.homework.inspection.navigator;

import edu.homework.inspection.common.Point;

import java.util.ArrayList;
import java.util.List;

public class GridMap {
    private final int width;
    private final int height;
    private final boolean[] blocked;
    private final boolean[] explored;

    public GridMap(int width, int height, boolean[] blocked, boolean[] explored) {
        this.width = width;
        this.height = height;
        this.blocked = blocked;
        this.explored = explored;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(Point point) {
        return point.getX() >= 0 && point.getX() < height && point.getY() >= 0 && point.getY() < width;
    }

    public boolean isBlocked(Point point) {
        return !inBounds(point) || blocked[point.index(width)];
    }

    public void setBlocked(Point point, boolean value) {
        if (inBounds(point)) {
            blocked[point.index(width)] = value;
        }
    }

    public boolean isExplored(Point point) {
        return inBounds(point) && explored[point.index(width)];
    }

    public int moveCost(Point point) {
        return isExplored(point) ? 2 : 1;
    }

    public List<Point> neighbors(Point point) {
        List<Point> result = new ArrayList<>(4);
        addIfOpen(result, new Point(point.getX() - 1, point.getY()));
        addIfOpen(result, new Point(point.getX() + 1, point.getY()));
        addIfOpen(result, new Point(point.getX(), point.getY() - 1));
        addIfOpen(result, new Point(point.getX(), point.getY() + 1));
        return result;
    }

    private void addIfOpen(List<Point> result, Point point) {
        if (inBounds(point) && !isBlocked(point)) {
            result.add(point);
        }
    }
}
