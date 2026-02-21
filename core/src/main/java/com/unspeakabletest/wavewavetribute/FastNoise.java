package com.unspeakabletest.wavewavetribute;

import com.badlogic.gdx.math.MathUtils;

public class FastNoise {
    // Simple 2D noise implementation (Value Noise or similar simple pseudo-noise
    // for speed)
    // Based on standard pseudo-random hashing

    private int seed;

    public FastNoise(int seed) {
        this.seed = seed;
    }

    public float GetNoise(float x, float y) {
        return valueNoise(x, y);
    }

    private float valueNoise(float x, float y) {
        int ix = MathUtils.floor(x);
        int iy = MathUtils.floor(y);

        float fx = x - ix;
        float fy = y - iy;

        // Smoothstep interpolation
        float sx = fx * fx * (3 - 2 * fx);
        float sy = fy * fy * (3 - 2 * fy);

        // Hash corners
        float n00 = hash(ix, iy);
        float n10 = hash(ix + 1, iy);
        float n01 = hash(ix, iy + 1);
        float n11 = hash(ix + 1, iy + 1);

        // Interpolate
        float lx0 = MathUtils.lerp(n00, n10, sx);
        float lx1 = MathUtils.lerp(n01, n11, sx);

        return MathUtils.lerp(lx0, lx1, sy);
    }

    private float hash(int x, int y) {
        int n = x + y * 57 + seed * 131;
        n = (n << 13) ^ n;
        return (1.0f - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0f);
    }
}
