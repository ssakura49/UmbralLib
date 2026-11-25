package com.ssakuracotero.umbralib.api.particle;

/**
 * Variant table of the mote particle: how many sprite variants exist and the motion of each. Install it through
 * {@link MoteType#installVariants} during common init, before particle providers are registered.
 *
 * <p>{@link #count()} must equal the number of textures listed in that type's {@code particles/<id>.json}.
 */
public interface MoteVariants {
    int count();

    /** Combined motion for {@code variant} in {@code [0, count())}; never null. */
    MoteMotion motion(int variant);

    /** Single neutral variant, used until a table is installed. */
    MoteVariants SINGLE_NEUTRAL = new MoteVariants() {
        @Override public int count() { return 1; }
        @Override public MoteMotion motion(int variant) { return MoteMotion.NEUTRAL; }
    };
}
