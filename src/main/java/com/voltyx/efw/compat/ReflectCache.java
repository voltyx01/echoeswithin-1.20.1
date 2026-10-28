package com.voltyx.efw.compat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Some client mods look classes and methods up by reflection every frame (Class.forName that fails, Class.getMethod on the
 * player); both cost a lot more than the call itself, a failed forName also builds an exception with a stack trace.
 * Redirects in those mods route the lookups here: the answer for a class and name never changes, so it is cached,
 * misses included, and a miss throws a shared exception without a stack trace.
 */
public final class ReflectCache {
    private static final Map<String, Optional<Class<?>>> CLASSES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Optional<Method>>> METHODS = new ConcurrentHashMap<>();
    private static final ClassNotFoundException NO_CLASS = new ClassNotFoundException("efw: cached miss", null) {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    };
    private static final NoSuchMethodException NO_METHOD = new NoSuchMethodException("efw: cached miss") {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    };

    private ReflectCache() {
    }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        return forName(name, true, ReflectCache.class.getClassLoader());
    }

    public static Class<?> forName(String name, boolean init, ClassLoader loader) throws ClassNotFoundException {
        String key = System.identityHashCode(loader) + "/" + name;
        Optional<Class<?>> found = CLASSES.get(key);
        if (found == null) {
            try {
                found = Optional.of(Class.forName(name, init, loader));
            } catch (ClassNotFoundException | LinkageError e) {
                found = Optional.empty();
            }
            CLASSES.put(key, found);
        }
        if (found.isEmpty()) {
            throw NO_CLASS;
        }
        return found.get();
    }

    public static Method getMethod(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        String key = params.length == 0 ? name : name + Arrays.toString(params);
        Map<String, Optional<Method>> byName = METHODS.computeIfAbsent(owner, c -> new ConcurrentHashMap<>());
        Optional<Method> found = byName.get(key);
        if (found == null) {
            try {
                found = Optional.of(owner.getMethod(name, params));
            } catch (NoSuchMethodException | SecurityException e) {
                found = Optional.empty();
            }
            byName.put(key, found);
        }
        if (found.isEmpty()) {
            throw NO_METHOD;
        }
        return found.get();
    }
}
