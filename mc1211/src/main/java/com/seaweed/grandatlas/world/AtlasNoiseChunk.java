package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.core.AtlasCore;
import javax.annotation.Nullable;

/** Added to every {@code NoiseChunk} by {@code mixin/NoiseChunkMixin} (kept outside the mixin package, which other code may not reference): the Grand Atlas core it is generating from, if any. */
public interface AtlasNoiseChunk {
    @Nullable AtlasCore grandatlas$core();
}
