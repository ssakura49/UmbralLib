package com.ssakuracotero.umbralib.api.math;

import com.ssakuracotero.umbralib.api.math.VisualMath;
import net.minecraft.util.Mth;

public final class FxEnvelope {
    private FxEnvelope() { }

    public static float smooth(float fraction) {
        return VisualMath.smoothstep(fraction);
    }

    public static float env(float age, float rise, float hold, float decay) {
        if (age < 0 || age >= rise + hold + decay) return 0;
        float attack = rise > 0 ? smooth(age / rise) : 1;
        return decay > 0 ? attack * (1 - smooth((age - rise - hold) / decay)) : attack;
    }

    public static float pop(float age, float peak, float decay) {
        if (age < 0 || age >= peak + decay) return 0;
        if (age < peak) return age / peak;
        float remaining = 1 - Mth.clamp((age - peak) / decay, 0, 1);
        return remaining * remaining;
    }
}
