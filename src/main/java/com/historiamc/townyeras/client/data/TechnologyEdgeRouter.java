package com.historiamc.townyeras.client.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Orthogonal prerequisite edges routed through column gutters and empty row gaps. */
public final class TechnologyEdgeRouter {
    private TechnologyEdgeRouter() {}
    public record Point(int x, int y) {}
    public record Box(int left, int top, int right, int bottom) {}

    public static List<Point> route(Point source, Point target, List<Box> obstacles) {
        int exit = source.x() + 90;
        int entry = target.x() - 90;
        List<List<Point>> candidates = new ArrayList<>();
        candidates.add(List.of(source, new Point(exit, source.y()), new Point(exit, target.y()), target));
        candidates.add(List.of(source, new Point(entry, source.y()), new Point(entry, target.y()), target));
        List<Integer> gaps = new ArrayList<>();
        gaps.add(source.y());
        gaps.add(target.y());
        for (Box box : obstacles) {
            gaps.add(box.top() - 12);
            gaps.add(box.bottom() + 12);
        }
        for (int gap : gaps) candidates.add(List.of(source, new Point(exit, source.y()),
                new Point(exit, gap), new Point(entry, gap), new Point(entry, target.y()), target));
        return candidates.stream().filter(path -> clear(path, obstacles))
                .min(Comparator.comparingInt(TechnologyEdgeRouter::length))
                .map(TechnologyEdgeRouter::compact)
                .orElseThrow(() -> new IllegalArgumentException("No obstacle-free technology edge route"));
    }

    public static boolean clear(List<Point> path, List<Box> obstacles) {
        for (int i = 1; i < path.size(); i++) {
            Point a = path.get(i - 1), b = path.get(i);
            for (Box box : obstacles) {
                if (a.x() == b.x() && a.x() > box.left() && a.x() < box.right()
                        && Math.max(a.y(), b.y()) > box.top() && Math.min(a.y(), b.y()) < box.bottom()) return false;
                if (a.y() == b.y() && a.y() > box.top() && a.y() < box.bottom()
                        && Math.max(a.x(), b.x()) > box.left() && Math.min(a.x(), b.x()) < box.right()) return false;
            }
        }
        return true;
    }

    private static int length(List<Point> path) {
        int length = path.size() * 15; // Prefer fewer elbows when routes have similar length.
        for (int i = 1; i < path.size(); i++) length += Math.abs(path.get(i).x() - path.get(i - 1).x())
                + Math.abs(path.get(i).y() - path.get(i - 1).y());
        return length;
    }

    private static List<Point> compact(List<Point> path) {
        List<Point> result = new ArrayList<>();
        for (Point point : path) {
            if (!result.isEmpty() && result.getLast().equals(point)) continue;
            while (result.size() > 1) {
                Point a = result.get(result.size() - 2), b = result.getLast();
                if ((a.x() == b.x() && b.x() == point.x()) || (a.y() == b.y() && b.y() == point.y()))
                    result.removeLast();
                else break;
            }
            result.add(point);
        }
        return List.copyOf(result);
    }
}
