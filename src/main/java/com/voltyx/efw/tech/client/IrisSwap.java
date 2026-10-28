package com.voltyx.efw.tech.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Lets the bodycam picture be drawn while a shader pack is on: for the duration of the feed render Iris (Oculus) is given its plain
 * vanilla pipeline in place of the pack's, so the picture is the world as without shaders. The pack's own pipeline (its buffers, its
 * frame state) is left alone and put back at once. Works on Iris internals by reflection; when they are not as expected nothing is
 * swapped and the caller skips the feed.
 */
final class IrisSwap {
    private static Object manager;
    private static Field pipelinesField;
    private static Field pipelineField;
    private static Object vanilla;
    private static boolean broken;

    private static Map<Object, Object> savedMap;
    private static Object savedPipeline;

    private IrisSwap() {
    }

    @SuppressWarnings("unchecked")
    static boolean enter() {
        if (broken) {
            return false;
        }
        try {
            if (manager == null) {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                Method get = iris.getMethod("getPipelineManager");
                manager = get.invoke(null);
                Class<?> pm = manager.getClass();
                pipelinesField = pm.getDeclaredField("pipelinesPerDimension");
                pipelinesField.setAccessible(true);
                pipelineField = pm.getDeclaredField("pipeline");
                pipelineField.setAccessible(true);
                Constructor<?> ctor = Class.forName("net.irisshaders.iris.pipeline.VanillaRenderingPipeline").getConstructor();
                vanilla = ctor.newInstance();
            }
            Map<Object, Object> map = (Map<Object, Object>) pipelinesField.get(manager);
            if (map.isEmpty()) {
                return false; // no pipeline made yet: a nested render would make the pack's own
            }
            savedMap = new HashMap<>(map);
            savedPipeline = pipelineField.get(manager);
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                e.setValue(vanilla);
            }
            pipelineField.set(manager, vanilla);
            return true;
        } catch (Throwable t) {
            broken = true;
            savedMap = null;
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    static void exit() {
        if (savedMap == null) {
            return;
        }
        try {
            Map<Object, Object> map = (Map<Object, Object>) pipelinesField.get(manager);
            map.putAll(savedMap);
            pipelineField.set(manager, savedPipeline);
        } catch (Throwable t) {
            broken = true;
        } finally {
            savedMap = null;
            savedPipeline = null;
        }
    }
}
