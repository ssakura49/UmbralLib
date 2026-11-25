package com.ssakuracotero.umbralib.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.IntSupplier;

/**
 * Client-only budgets for decorative particles and distance-based visual detail.
 *
 * <p>Each mod registers its own particle channels with {@link #registerChannel}. A channel's limit is per client tick
 * and is scaled down by the vanilla particle setting. Distance detail and the global active-effect cap come from the
 * shared library settings.
 */
public final class ClientVisualBudget {
    /** A registered per-tick particle budget; obtain one with {@link #registerChannel}. */
    public static final class Channel {
        private final ResourceLocation id;
        private final IntSupplier limitPerTick;
        private final Quota quota = new Quota();

        private Channel(ResourceLocation id, IntSupplier limitPerTick) {
            this.id = id;
            this.limitPerTick = limitPerTick;
        }

        public ResourceLocation id() {
            return id;
        }

        /** Current per-tick limit after the vanilla particle setting is applied. */
        public int limit() {
            return scaledLimit(limitPerTick.getAsInt());
        }

        public boolean reserve(ClientLevel level, int amount) {
            return level != null && level == Minecraft.getInstance().level && !Minecraft.getInstance().isPaused()
                    && quota.take(level, level.getGameTime(), limit(), amount);
        }

        public boolean reserve(ClientLevel level, Vec3 position, int amount) {
            return level != null && level == Minecraft.getInstance().level
                    && detailScale(level, position) > 0f && reserve(level, amount);
        }

        /** One-particle reservation in the shape of a particle type's local gate. */
        public boolean reserveLocal(Level level, double x, double y, double z) {
            return level instanceof ClientLevel client && client == Minecraft.getInstance().level
                    && detailScale(client, new Vec3(x, y, z)) > 0f && reserve(client, 1);
        }
    }

    private static final Map<ResourceLocation, Channel> CHANNELS = new LinkedHashMap<>();
    private static volatile List<Channel> channels = List.of();
    private static final Map<String, Integer> ACTIVE_EFFECTS = new TreeMap<>();
    private static long rejectedEffects;

    private ClientVisualBudget() { }

    /**
     * Registers a particle channel. {@code limitPerTick} is read live on every reservation (for example from the
     * registering mod's config). Duplicate IDs are rejected.
     */
    public static synchronized Channel registerChannel(ResourceLocation id, IntSupplier limitPerTick) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(limitPerTick, "limitPerTick");
        if (CHANNELS.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate visual budget channel: " + id);
        }
        Channel channel = new Channel(id, limitPerTick);
        CHANNELS.put(id, channel);
        channels = List.copyOf(CHANNELS.values());
        return channel;
    }

    public static List<Channel> channels() {
        return channels;
    }

    public static void beginTick(ClientLevel level) {
        if (level == null) clear();
        else for (Channel channel : channels) channel.quota.advance(level, level.getGameTime());
    }

    /**
     * Global admission for dispatcher-managed effects (new entries only). Each dispatcher calls this before
     * enqueueing and reports its live count via {@link #reportActive(String, int)}. With
     * {@code maxActiveEffects = 0} (default) every effect is admitted and only the diagnostics are updated,
     * so existing per-dispatcher caps keep their behaviour. This is not a second particle budget.
     */
    public static boolean admitEffect(String channel) {
        int cap = VisualLibrary.settings().maxActiveEffects();
        if (!admits(cap, activeEffects())) {
            rejectedEffects++;
            return false;
        }
        ACTIVE_EFFECTS.merge(channel, 1, Integer::sum);
        return true;
    }

    /** Overwrites a channel's live entry count; call after pruning. A count of zero removes the channel. */
    public static void reportActive(String channel, int count) {
        if (count <= 0) ACTIVE_EFFECTS.remove(channel);
        else ACTIVE_EFFECTS.put(channel, count);
    }

    public static int activeEffects() {
        int total = 0;
        for (int count : ACTIVE_EFFECTS.values()) total += count;
        return total;
    }

    /** Read-only snapshot for a diagnostics command; never changes budgets. */
    public static List<String> diagnostics() {
        List<String> lines = new ArrayList<>();
        int cap = VisualLibrary.settings().maxActiveEffects();
        lines.add("active effects " + activeEffects() + " / " + (cap <= 0 ? "unlimited" : cap)
                + ", rejected since reset " + rejectedEffects);
        ACTIVE_EFFECTS.forEach((channel, count) -> lines.add("  " + channel + ": " + count));
        for (Channel channel : channels) {
            lines.add(channel.id + " particles this tick " + channel.quota.used + " / " + channel.limit()
                    + ", denied this tick " + channel.quota.denied + ", denied since reset " + channel.quota.deniedTotal);
        }
        lines.add("shader state " + ClientShaderCompatibility.state()
                + ", custom programs " + (ClientShaderCompatibility.allowsCustomPrograms() ? "allowed" : "fallback"));
        return lines;
    }

    static boolean admits(int cap, int active) {
        return cap <= 0 || active < cap;
    }

    private static final class Quota {
        private Object world;
        private long tick = Long.MIN_VALUE;
        private int used;
        private int denied;
        private long deniedTotal;

        void advance(Object nextWorld, long nextTick) {
            if (world != nextWorld || tick != nextTick) {
                world = nextWorld;
                tick = nextTick;
                used = 0;
                denied = 0;
            }
        }

        boolean take(Object nextWorld, long nextTick, int limit, int amount) {
            advance(nextWorld, nextTick);
            if (amount < 0 || amount > Math.max(0, limit) - used) {
                denied++;
                deniedTotal++;
                return false;
            }
            used += amount;
            return true;
        }

        void clear() {
            world = null;
            tick = Long.MIN_VALUE;
            used = 0;
            denied = 0;
            deniedTotal = 0;
        }
    }

    /**
     * Returns a multiplier for decorative geometry. The main silhouette remains visible at long range;
     * extra rings, glyphs and filaments are reduced first.
     */
    public static float detailScale(ClientLevel level, Vec3 position) {
        if (level == null || level != Minecraft.getInstance().level || position == null) return 0f;
        return detailScale(position.distanceToSqr(cameraPosition()));
    }

    public static float detailScale(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0d) return 0f;
        VisualSettings settings = VisualLibrary.settings();
        double max = Math.max(16d, settings.effectDistance());
        if (distanceSquared > max * max) return 0f;
        double detail = Math.max(16d, Math.min(max, settings.detailDistance()));
        ParticleStatus setting = Minecraft.getInstance().options.particles().get();
        if (setting == ParticleStatus.MINIMAL) return 0.5f;
        double far = max * 0.82d;
        if (distanceSquared > far * far) return 0.5f;
        if (distanceSquared > detail * detail || setting == ParticleStatus.DECREASED) return 0.72f;
        return 1f;
    }

    /** Clears counters on logout, dimension change, or client shutdown. */
    public static void clear() {
        for (Channel channel : channels) channel.quota.clear();
        ACTIVE_EFFECTS.clear();
        rejectedEffects = 0;
    }

    public static boolean contractCheck() {
        Quota quota = new Quota();
        Object first = new Object();
        Object second = new Object();
        boolean valid = !quota.take(first, 5, 0, 1)
                && quota.take(first, 5, 3, 3) && !quota.take(first, 5, 3, 1)
                && !quota.take(first, 5, 2, 1) && quota.take(first, 5, 4, 1)
                && quota.take(first, 6, 3, 3) && !quota.take(first, 6, 3, 1)
                && quota.take(second, 6, 3, 3) && !quota.take(second, 6, 3, 1)
                && !quota.take(second, 6, 3, -1);
        quota.clear();
        if (!valid || !quota.take(second, 6, 3, 3)) return false;
        for (int limit : new int[] {0, 1, 48, 64, 192}) {
            for (int concurrency : new int[] {1, 8, 32}) {
                quota.clear();
                int emitted = 0;
                for (int effect = 0; effect < concurrency; effect++) {
                    for (int particle = 0; particle < 64; particle++) {
                        if (quota.take(first, 10, limit, 1)) emitted++;
                    }
                }
                if (emitted != Math.min(limit, concurrency * 64)) return false;
                quota.advance(first, 10);
                if (quota.take(first, 10, limit, 1) != (emitted < limit)) return false;
                if (quota.take(first, 11, limit, limit + 1)) return false;
                if (!quota.take(first, 11, limit, limit)) return false;
            }
        }
        return true;
    }

    private static int scaledLimit(int limit) {
        return switch (Minecraft.getInstance().options.particles().get()) {
            case MINIMAL -> Math.max(0, limit / 4);
            case DECREASED -> Math.max(0, limit / 2);
            default -> Math.max(0, limit);
        };
    }

    private static Vec3 cameraPosition() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }
}
