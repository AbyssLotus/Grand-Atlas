package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Surface rules another mod adds round Grand Atlas's own, kept from running on every block.
 *
 * <p>Vanilla Backport wraps the overworld's surface rule at run time with its own (the Sulfur
 * Caves' sulfur and cinnabar bands, the Dappled Forest's dirt patches). Each starts by asking the
 * block's biome, on every block of rock, and in a world 1,536 blocks deep that lookup alone was
 * 14% of chunk generation (profiled 2026-11-11): the very cost {@link CaveGate} exists to avoid.
 *
 * <p>{@code mixin/NoiseBasedChunkGeneratorMixin} hands the wrapped rule here. The added rules are
 * put behind the same gate as the cave rules: they run only where the core says one of their
 * biomes can be, and exactly as written there. Grand Atlas never places a Dappled Forest, so
 * only the Sulfur Caves count. Its own rule is passed through untouched.
 */
public final class ForeignRules {
    private ForeignRules() {}

    /** The biomes a foreign rule can apply in, of those Grand Atlas places. */
    private static final EnumSet<BiomeKind> KINDS = EnumSet.of(BiomeKind.SULFUR_CAVES);

    /** The last wrapped rule and what it became, as one value: chunks build on several threads at once. */
    private record Pair(SurfaceRules.RuleSource in, SurfaceRules.RuleSource out) {}

    private static volatile Pair last;

    /**
     * @param rule what the game is about to build the surface with
     * @param own  the rule from the noise settings (Grand Atlas's, if this is its world)
     */
    public static SurfaceRules.RuleSource gate(SurfaceRules.RuleSource rule, SurfaceRules.RuleSource own) {
        if (rule == own) return rule;
        Pair known = last;
        if (known != null && known.in == rule) return known.out;
        SurfaceRules.RuleSource out = rule;
        if (isAtlas(own) && rule instanceof SurfaceRules.SequenceRuleSource wrapped && wrapped.sequence().contains(own)) {
            List<SurfaceRules.RuleSource> all = new ArrayList<>(), run = new ArrayList<>();
            for (SurfaceRules.RuleSource r : wrapped.sequence()) {
                if (r != own) {
                    run.add(r);
                    continue;
                }
                if (!run.isEmpty()) all.add(new Gated(SurfaceRules.sequence(run.toArray(SurfaceRules.RuleSource[]::new))));
                run = new ArrayList<>();
                all.add(own);
            }
            if (!run.isEmpty()) all.add(new Gated(SurfaceRules.sequence(run.toArray(SurfaceRules.RuleSource[]::new))));
            out = SurfaceRules.sequence(all.toArray(SurfaceRules.RuleSource[]::new));
        }
        last = new Pair(rule, out);
        return out;
    }

    private static boolean isAtlas(SurfaceRules.RuleSource own) {
        if (!(own instanceof SurfaceRules.SequenceRuleSource s)) return false;
        for (SurfaceRules.RuleSource r : s.sequence()) {
            if (r instanceof CaveGate || r == Strata.INSTANCE) return true;
        }
        return false;
    }

    /** Never read from or written to data: it exists only between the mixin and the surface builder. */
    private record Gated(SurfaceRules.RuleSource rule) implements SurfaceRules.RuleSource {
        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
            throw new UnsupportedOperationException("a run-time wrapper, not data");
        }

        @Override
        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            SurfaceRules.SurfaceRule inner = rule.apply(context);
            AtlasCore core = CoreHandle.coreFrom(context.randomState);
            return core == null ? inner : new CaveGate.Evaluator(core, inner, KINDS);
        }
    }
}
