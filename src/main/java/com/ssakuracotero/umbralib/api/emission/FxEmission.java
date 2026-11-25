package com.ssakuracotero.umbralib.api.emission;

public final class FxEmission {
    private final int limit;
    private double remainder;
    private int lastTick = -1;

    public FxEmission(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Positive emission limit required");
        this.limit = limit;
    }

    public boolean crosses(int tick, int scheduledTick) {
        return scheduledTick >= 0 && lastTick < scheduledTick && tick >= scheduledTick;
    }

    public int sample(int tick, double rate, int burst) {
        if (tick < 0 || tick <= lastTick) return 0;
        lastTick = tick;
        double amount = (Double.isFinite(rate) ? Math.max(0d, Math.min(limit, rate)) : 0d) + remainder;
        int continuous = (int) amount;
        remainder = amount - continuous;
        return continuous + Math.min(limit - continuous, Math.max(0, burst));
    }
}
