package com.ynu.marginx.domain.service;

import java.util.Random;

/**
 * The normal deviates the Monte Carlo optimisers draw. The C++ tool seeds mt19937 from
 * random_device on every call, so its runs are not reproducible even against itself; keeping the
 * source behind an interface lets a test pin a seed while production keeps the same behaviour.
 */
@FunctionalInterface
public interface RandomSource {

    double nextNormal(double mean, double standardDeviation);

    static RandomSource seeded(long seed) {
        Random random = new Random(seed);
        return random::nextGaussian;
    }

    static RandomSource unseeded() {
        Random random = new Random();
        return random::nextGaussian;
    }
}
