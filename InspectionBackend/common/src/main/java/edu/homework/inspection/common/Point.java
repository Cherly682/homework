package edu.homework.inspection.common;

import java.util.Objects;

public final class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int index(int width) {
        return x * width + y;
    }

    public int manhattan(Point other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }

    public String toQueueValue() {
        return x + "," + y;
    }

    public static Point parseQueueValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Point value is null");
        }
        String[] parts = value.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Point value must be x,y: " + value);
        }
        return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Point)) {
            return false;
        }
        Point point = (Point) o;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
