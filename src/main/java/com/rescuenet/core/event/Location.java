package com.rescuenet.core.event;

/**
 * Represents a 2D Cartesian location in Novaterra.
 * Spatial proximity is evaluated with standard Euclidean distance.
 * No GPS or Haversine formula needed.
 */
public final class Location {

    private final double x;
    private final double y;

    public Location(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }
    public double getY() { return y; }

    public double distanceTo(Location other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return String.format("(%.1f, %.1f)", x, y);
    }
}
