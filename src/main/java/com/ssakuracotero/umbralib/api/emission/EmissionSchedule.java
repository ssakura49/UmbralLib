package com.ssakuracotero.umbralib.api.emission;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic per-tick particle emission demand (steady rate plus scheduled bursts).
 *
 * <p>An {@link Emitter} answers "how many particles does this emitter want this tick" for a window starting at
 * local tick 0. It only computes demand: callers still reserve every spawn through the shared client budget
 * and may get less. Exact mode accumulates the decimal rate as an exact rational; stochastic mode draws the
 * fractional part per emitter and tick from {@link #eventSample}, a SplitMix64-derived sample, so results depend only on (seed, emitter, tick, channel) and not on call order or frame rate.
 */
public final class EmissionSchedule {
    public static final int MAX_BURSTS = 256;
    private static final long MAX_DENOMINATOR = 1_000_000_000_000L;
    private static final BigDecimal MAX_RATE = BigDecimal.valueOf(10_000L);

    public enum Mode { EXACT, STOCHASTIC }

    /**
     * Starts at {@code startTick}, repeats every {@code intervalTicks} for {@code cycles} occurrences
     * ({@code 0} repeats for as long as the emitter runs); each occurrence adds {@code count} with
     * {@code probability}.
     */
    public record Burst(long startTick, int count, int intervalTicks, int cycles, double probability) {
        public Burst {
            if (startTick < 0L || count < 0 || count > 1_000_000 || intervalTicks < 1 || cycles < 0
                    || !(probability >= 0d && probability <= 1d)) {
                throw new IllegalArgumentException("Invalid burst");
            }
        }

        public static Burst once(long tick, int count) {
            return new Burst(tick, count, 1, 1, 1d);
        }

        boolean firesAt(long tick) {
            if (tick < startTick) return false;
            long offset = tick - startTick;
            return offset % intervalTicks == 0L && (cycles == 0 || offset / intervalTicks < cycles);
        }
    }

    private EmissionSchedule() { }

    /** Stable sample in {@code [0, 1)}; bit-identical to the offline {@code _event_sample}. */
    public static double eventSample(long seed, long emitterIndex, long tick, long channel) {
        long value = seed ^ ((emitterIndex + 1L) * 0x9E3779B97F4A7C15L)
                ^ ((tick + 1L) * 0xBF58476D1CE4E5B9L)
                ^ ((channel + 1L) * 0x94D049BB133111EBL);
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    public static final class Emitter {
        private final Mode mode;
        private final long rateNumerator;
        private final long denominator;
        private final long whole;
        private final double fractional;
        private final long seed;
        private final int emitterIndex;
        private final List<Burst> bursts;
        private long remainderNumerator;
        private long tick;

        private Emitter(Mode mode, long rateNumerator, long denominator, long remainderNumerator,
                        long seed, int emitterIndex, List<Burst> bursts) {
            this.mode = mode;
            this.rateNumerator = rateNumerator;
            this.denominator = denominator;
            this.remainderNumerator = remainderNumerator;
            this.whole = rateNumerator / denominator;
            this.fractional = (double) (rateNumerator % denominator) / (double) denominator;
            this.seed = seed;
            this.emitterIndex = emitterIndex;
            this.bursts = bursts;
        }

        /**
         * @param ratePerTick      decimal text, 0..10000 particles per tick for this emitter
         * @param initialRemainder decimal text in {@code [0, 1)}; exact mode only, otherwise must be 0
         * @param seed             0..2147483647; also drives probabilistic bursts in exact mode
         * @param emitterIndex     independent stream index (0-based)
         */
        public static Emitter create(Mode mode, String ratePerTick, String initialRemainder, long seed,
                                     int emitterIndex, List<Burst> bursts) {
            Objects.requireNonNull(mode, "mode");
            if (seed < 0L || seed > Integer.MAX_VALUE) throw new IllegalArgumentException("seed must be 0..2147483647");
            if (emitterIndex < 0) throw new IllegalArgumentException("emitterIndex must be >= 0");
            List<Burst> copy = List.copyOf(Objects.requireNonNull(bursts, "bursts"));
            if (copy.size() > MAX_BURSTS) throw new IllegalArgumentException("At most 256 bursts");
            BigDecimal rate = decimal(ratePerTick, "ratePerTick");
            if (rate.signum() < 0 || rate.compareTo(MAX_RATE) > 0) throw new IllegalArgumentException("ratePerTick must be 0..10000");
            BigDecimal initial = decimal(initialRemainder == null ? "0" : initialRemainder, "initialRemainder");
            if (initial.signum() < 0 || initial.compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException("initialRemainder must be within [0, 1)");
            }
            if (mode == Mode.STOCHASTIC && initial.signum() != 0) {
                throw new IllegalArgumentException("initialRemainder is only supported by exact mode");
            }
            BigInteger[] r = rational(rate);
            BigInteger[] i = rational(initial);
            BigInteger lcm = r[1].divide(r[1].gcd(i[1])).multiply(i[1]);
            if (lcm.compareTo(BigInteger.valueOf(MAX_DENOMINATOR)) > 0) {
                throw new IllegalArgumentException("Rate and remainder need at most 12 decimal places");
            }
            long denominator = lcm.longValueExact();
            long rateNumerator = r[0].multiply(lcm.divide(r[1])).longValueExact();
            long remainder = i[0].multiply(lcm.divide(i[1])).longValueExact();
            return new Emitter(mode, rateNumerator, denominator, remainder, seed, emitterIndex, copy);
        }

        /** Births requested at the current local tick; advances to the next tick. */
        public int next() {
            long now = tick++;
            long continuous;
            if (mode == Mode.EXACT) {
                remainderNumerator += rateNumerator;
                continuous = remainderNumerator / denominator;
                remainderNumerator -= continuous * denominator;
            } else {
                continuous = whole + (fractional > 0d && eventSample(seed, emitterIndex, now, 0L) < fractional ? 1L : 0L);
            }
            long burstCount = 0L;
            for (int index = 0; index < bursts.size(); index++) {
                Burst burst = bursts.get(index);
                if (!burst.firesAt(now)) continue;
                double probability = burst.probability();
                if (probability == 1d || (probability > 0d
                        && eventSample(seed, emitterIndex, now, index + 1L) < probability)) {
                    burstCount += burst.count();
                }
            }
            return Math.toIntExact(continuous + burstCount);
        }

        /** Local tick that the next call to {@link #next()} will evaluate. */
        public long tick() {
            return tick;
        }

        /** Exact remainder as {@code numerator / denominator} (exact mode; zero otherwise). */
        public long remainderNumerator() {
            return mode == Mode.EXACT ? remainderNumerator : 0L;
        }

        public long denominator() {
            return denominator;
        }
    }

    private static BigDecimal decimal(String text, String name) {
        Objects.requireNonNull(text, name);
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be decimal text", exception);
        }
    }

    private static BigInteger[] rational(BigDecimal value) {
        BigInteger numerator;
        BigInteger denominator;
        if (value.scale() > 0) {
            numerator = value.unscaledValue();
            denominator = BigInteger.TEN.pow(value.scale());
        } else {
            numerator = value.toBigIntegerExact();
            denominator = BigInteger.ONE;
        }
        BigInteger gcd = numerator.gcd(denominator);
        if (gcd.signum() == 0) return new BigInteger[] {BigInteger.ZERO, BigInteger.ONE};
        return new BigInteger[] {numerator.divide(gcd), denominator.divide(gcd)};
    }
}
