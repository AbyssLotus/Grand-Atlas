package com.seaweed.grandatlas.core.micro;

import com.seaweed.grandatlas.core.BiomeKind;
import java.util.List;

/**
 * A meso site: a place 140-600 blocks across inside a province, with its own character and the
 * micro features that make it (a pond country and its ponds, a sinkhole field and its pits).
 *
 * @param biome the surface biome at its centre, which picks materials for its detail
 */
public record MesoSite(MesoKind kind, double x, double z, double radius, long seed, BiomeKind biome, List<MicroFeature> micros) {
    public boolean covers(double px, double pz) {
        double dx = px - x, dz = pz - z;
        return dx * dx + dz * dz <= radius * radius;
    }
}
