package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.core.AtlasCore;
import javax.annotation.Nullable;

/**
 * Added to every {@code RandomState} by {@code mixin/RandomStateMixin}: the Grand Atlas core its
 * noise router was wired to, or null for a world that isn't Grand Atlas's. (Kept outside the mixin
 * package, which other code may not reference.)
 */
public interface AtlasRandomState {
    @Nullable AtlasCore grandatlas$core();
}
