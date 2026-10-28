package com.voltyx.efw.gender.main.config;

import com.google.gson.JsonPrimitive;

/** A whole number setting with bounds (the blinking eyes). */
public class IntConfigKey extends NumberConfigKey<Integer> {

    public IntConfigKey(String key, int defaultValue, int minInclusive, int maxInclusive) {
        super(key, defaultValue, minInclusive, maxInclusive);
    }

    @Override
    protected Integer fromPrimitive(JsonPrimitive primitive) {
        return primitive.getAsInt();
    }

    public int getMinInclusive() {
        return minInclusive == null ? Integer.MIN_VALUE : minInclusive;
    }

    public int getMaxInclusive() {
        return maxInclusive == null ? Integer.MAX_VALUE : maxInclusive;
    }
}
