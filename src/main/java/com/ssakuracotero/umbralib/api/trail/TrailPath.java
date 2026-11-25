package com.ssakuracotero.umbralib.api.trail;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Bounded ribbon preparation for recorded trail positions.
 *
 * <p>Takes already-recorded positions (for example {@link VisualTrail#samples()}) in the caller's own space and
 * prepares bounded draw samples: original corners are kept, edges are subdivided by {@code spacing}, edges longer
 * than {@code maxGap} start an independent segment (no line across teleports), cross sections are carried by
 * shortest-arc parallel transport, and U follows arc length per segment. It never mutates the trail history.
 *
 * <p>Capacity overflow or invalid input rejects the whole preparation ({@link Optional#empty()}); output is
 * never silently truncated. Results are deterministic up to floating-point rounding of the length
 * computation; 180-degree turns keep the previous side, which is a deterministic but nonunique choice.
 */
public final class TrailPath {
    public static final int MAX_INPUT_POINTS = 256;
    public static final int MAX_OUTPUT_POINTS = 2048;
    private static final double TURN_EPSILON = 1.0e-12d;
    private static final double COORDINATE_LIMIT = 1.0e9d;

    /**
     * @param spacing        maximum subdivided edge length in blocks
     * @param maxGap         edges longer than this start a new segment
     * @param uvRepeatBlocks blocks of arc length per texture repeat (U = arc / uvRepeatBlocks)
     * @param halfWidth      half ribbon width in blocks along the transported side
     * @param maxPoints      total output sample capacity, 2..2048
     * @param initialUp      reference used to choose the first side of every segment
     */
    public record Settings(double spacing, double maxGap, double uvRepeatBlocks, double halfWidth,
                           int maxPoints, Vec3 initialUp) {
        public static final Settings DEFAULT = new Settings(0.25d, 8d, 1d, 0.05d, 1024, new Vec3(0d, 1d, 0d));

        public Settings {
            bounded(spacing, "spacing");
            bounded(maxGap, "maxGap");
            bounded(uvRepeatBlocks, "uvRepeatBlocks");
            bounded(halfWidth, "halfWidth");
            if (maxPoints < 2 || maxPoints > MAX_OUTPUT_POINTS) {
                throw new IllegalArgumentException("maxPoints must be within 2.." + MAX_OUTPUT_POINTS);
            }
            if (initialUp == null || !finite(initialUp) || maxAbs(initialUp) == 0d) {
                throw new IllegalArgumentException("initialUp must be a finite nonzero vector");
            }
        }

        private static void bounded(double value, String name) {
            if (!Double.isFinite(value) || value < 1.0e-6d || value > 1.0e6d) {
                throw new IllegalArgumentException(name + " is outside 1e-6..1e6");
            }
        }
    }

    /**
     * One draw sample. {@code inputIndex} is the first input index merged into this corner, or -1 for an
     * interpolated point. Frame vectors and edges are null for a single-point segment, which is not drawable.
     */
    public record Sample(Vec3 position, int segment, int inputIndex, double arcLength, double u,
                         Vec3 tangent, Vec3 side, Vec3 binormal, Vec3 left, Vec3 right) {
        public boolean drawable() {
            return tangent != null;
        }
    }

    public record Segment(int index, int start, int count, double length) {
        public boolean drawable() {
            return count > 1;
        }
    }

    public record Result(List<Sample> samples, List<Segment> segments, int duplicates, int breaks,
                         int antiparallelTurns, int initialUpFallbacks) { }

    private record Node(Vec3 position, int index) { }

    private record Side(Vec3 side, boolean flag) { }

    private TrailPath() { }

    /** Positions of recorded trail samples, oldest first. */
    public static List<Vec3> positions(List<VisualTrail.Sample> history) {
        List<Vec3> positions = new ArrayList<>(history.size());
        for (VisualTrail.Sample sample : history) positions.add(sample.position());
        return positions;
    }

    public static Optional<Result> prepare(List<Vec3> points, Settings settings) {
        if (settings == null || points == null || points.size() < 2 || points.size() > MAX_INPUT_POINTS) {
            return Optional.empty();
        }
        for (Vec3 point : points) {
            if (point == null || !finite(point) || maxAbs(point) > COORDINATE_LIMIT) return Optional.empty();
        }
        List<List<Node>> paths = new ArrayList<>();
        List<Node> current = new ArrayList<>();
        current.add(new Node(points.get(0), 0));
        paths.add(current);
        int duplicates = 0;
        int breaks = 0;
        for (int index = 1; index < points.size(); index++) {
            Vec3 point = points.get(index);
            double length = distance(current.get(current.size() - 1).position(), point);
            if (length == 0d) {
                duplicates++;
            } else if (length > settings.maxGap()) {
                breaks++;
                current = new ArrayList<>();
                current.add(new Node(point, index));
                paths.add(current);
            } else {
                current.add(new Node(point, index));
            }
        }
        int capacity = settings.maxPoints();
        long outputCount = paths.size();
        if (outputCount > capacity) return Optional.empty();
        for (List<Node> path : paths) {
            for (int i = 1; i < path.size(); i++) {
                outputCount += steps(distance(path.get(i - 1).position(), path.get(i).position()), settings.spacing());
                if (outputCount > capacity) return Optional.empty();
            }
        }

        List<Sample> samples = new ArrayList<>((int) outputCount);
        List<Segment> segments = new ArrayList<>(paths.size());
        int antiparallel = 0;
        int fallbacks = 0;
        double width = settings.halfWidth();
        for (int segmentIndex = 0; segmentIndex < paths.size(); segmentIndex++) {
            List<Node> path = paths.get(segmentIndex);
            List<Vec3> positions = new ArrayList<>();
            List<Integer> inputs = new ArrayList<>();
            List<Double> arcs = new ArrayList<>();
            positions.add(path.get(0).position());
            inputs.add(path.get(0).index());
            arcs.add(0d);
            double arc = 0d;
            for (int i = 1; i < path.size(); i++) {
                Vec3 first = path.get(i - 1).position();
                Vec3 second = path.get(i).position();
                double length = distance(first, second);
                long steps = steps(length, settings.spacing());
                for (long step = 1; step <= steps; step++) {
                    if (step == steps) {
                        positions.add(second);
                        inputs.add(path.get(i).index());
                    } else {
                        double fraction = (double) step / steps;
                        positions.add(new Vec3(first.x + (second.x - first.x) * fraction,
                                first.y + (second.y - first.y) * fraction,
                                first.z + (second.z - first.z) * fraction));
                        inputs.add(-1);
                    }
                    arcs.add(arc + length * step / steps);
                }
                arc += length;
            }
            int start = samples.size();
            Vec3 previousTangent = null;
            Vec3 previousSide = null;
            for (int index = 0; index < positions.size(); index++) {
                Vec3 position = positions.get(index);
                double arcLength = arcs.get(index);
                double u = arcLength / settings.uvRepeatBlocks();
                if (positions.size() == 1) {
                    samples.add(new Sample(position, segmentIndex, inputs.get(index), arcLength, u,
                            null, null, null, null, null));
                    continue;
                }
                Vec3 delta = index + 1 < positions.size()
                        ? positions.get(index + 1).subtract(position)
                        : position.subtract(positions.get(index - 1));
                Vec3 tangent = unit(delta);
                if (tangent == null) return Optional.empty();
                Side side;
                if (previousTangent == null) {
                    side = initialSide(tangent, settings.initialUp());
                    if (side == null) return Optional.empty();
                    if (side.flag()) fallbacks++;
                } else {
                    side = transport(previousTangent, tangent, previousSide);
                    if (side == null) return Optional.empty();
                    if (side.flag()) antiparallel++;
                }
                Vec3 s = side.side();
                Vec3 binormal = unit(tangent.cross(s));
                if (binormal == null) return Optional.empty();
                Vec3 left = new Vec3(position.x - width * s.x, position.y - width * s.y, position.z - width * s.z);
                Vec3 right = new Vec3(position.x + width * s.x, position.y + width * s.y, position.z + width * s.z);
                samples.add(new Sample(position, segmentIndex, inputs.get(index), arcLength, u,
                        tangent, s, binormal, left, right));
                previousTangent = tangent;
                previousSide = s;
            }
            segments.add(new Segment(segmentIndex, start, positions.size(), arc));
        }
        return Optional.of(new Result(Collections.unmodifiableList(samples), Collections.unmodifiableList(segments),
                duplicates, breaks, antiparallel, fallbacks));
    }

    private static long steps(double length, double spacing) {
        return Math.max(1L, (long) Math.ceil(length / spacing));
    }

    private static Side initialSide(Vec3 tangent, Vec3 up) {
        Vec3 unitUp = unit(up);
        if (unitUp == null) return null;
        Vec3 side = tangent.cross(unitUp);
        boolean fallback = length(side) <= TURN_EPSILON;
        if (fallback) {
            double ax = Math.abs(tangent.x);
            double ay = Math.abs(tangent.y);
            double az = Math.abs(tangent.z);
            int axis = ax <= ay && ax <= az ? 0 : ay <= az ? 1 : 2;
            Vec3 reference = new Vec3(axis == 0 ? 1d : 0d, axis == 1 ? 1d : 0d, axis == 2 ? 1d : 0d);
            side = tangent.cross(reference);
        }
        Vec3 result = unit(side);
        return result == null ? null : new Side(result, fallback);
    }

    /** Shortest-arc transport with orthogonal reprojection; flag marks an antiparallel (180-degree) turn. */
    private static Side transport(Vec3 previousTangent, Vec3 tangent, Vec3 previousSide) {
        double cosine = Math.max(-1d, Math.min(1d, previousTangent.dot(tangent)));
        Vec3 rotationAxis = previousTangent.cross(tangent);
        double sine = length(rotationAxis);
        boolean reversed = sine <= TURN_EPSILON && cosine < 0d;
        Vec3 candidate;
        if (sine <= TURN_EPSILON) {
            candidate = previousSide;
        } else {
            Vec3 axis = new Vec3(rotationAxis.x / sine, rotationAxis.y / sine, rotationAxis.z / sine);
            Vec3 crossSide = axis.cross(previousSide);
            double along = axis.dot(previousSide) * (1d - cosine);
            candidate = new Vec3(
                    cosine * previousSide.x + sine * crossSide.x + along * axis.x,
                    cosine * previousSide.y + sine * crossSide.y + along * axis.y,
                    cosine * previousSide.z + sine * crossSide.z + along * axis.z);
        }
        double projection = candidate.dot(tangent);
        candidate = new Vec3(candidate.x - projection * tangent.x, candidate.y - projection * tangent.y,
                candidate.z - projection * tangent.z);
        if (length(candidate) <= TURN_EPSILON) {
            Side reselected = initialSide(tangent, previousSide);
            if (reselected == null) return null;
            candidate = reselected.side();
        }
        Vec3 result = unit(candidate);
        return result == null ? null : new Side(result, reversed);
    }

    /** Length with pre-scaling so subnormal or very large components keep their direction. */
    private static double length(Vec3 value) {
        double scale = maxAbs(value);
        if (scale == 0d || !Double.isFinite(scale)) return scale == 0d ? 0d : Double.NaN;
        double x = value.x / scale;
        double y = value.y / scale;
        double z = value.z / scale;
        return Math.sqrt(x * x + y * y + z * z) * scale;
    }

    private static double distance(Vec3 a, Vec3 b) {
        return length(b.subtract(a));
    }

    private static Vec3 unit(Vec3 value) {
        double scale = maxAbs(value);
        if (scale == 0d || !Double.isFinite(scale)) return null;
        double x = value.x / scale;
        double y = value.y / scale;
        double z = value.z / scale;
        double length = Math.sqrt(x * x + y * y + z * z);
        return new Vec3(x / length, y / length, z / length);
    }

    private static double maxAbs(Vec3 value) {
        return Math.max(Math.abs(value.x), Math.max(Math.abs(value.y), Math.abs(value.z)));
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
