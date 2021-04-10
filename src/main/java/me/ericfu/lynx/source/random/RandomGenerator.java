package me.ericfu.lynx.source.random;

import java.util.Random;

public interface RandomGenerator {

    /**
     * Generate one value
     *
     * @param rownum an increasing LONG number starting from 1
     * @param rand   the Random object
     * @return generated value
     */
    Object generate(long rownum, Random rand);

}
