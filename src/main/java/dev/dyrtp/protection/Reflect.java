package dev.dyrtp.protection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Reflect {

    private static final ConcurrentMap<MethodKey, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private Reflect() {
    }

    public static Object staticField(String className, String fieldName) throws Exception {
        Field field = Class.forName(className).getField(fieldName);
        return field.get(null);
    }

    public static Object staticCall(String className, String methodName, Object... args) throws Exception {
        Class<?> type = Class.forName(className);
        Method method = find(type, methodName, args);
        return method.invoke(null, args);
    }

    public static Object call(Object target, String methodName, Object... args) throws Exception {
        Method method = find(target.getClass(), methodName, args);
        return method.invoke(target, args);
    }

    public static Method find(Class<?> type, String methodName, Object... args) throws NoSuchMethodException {
        MethodKey key = MethodKey.of(type, methodName, args);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Method found = findUncached(type, methodName, args);
        METHOD_CACHE.putIfAbsent(key, found);
        return found;
    }

    private static Method findUncached(Class<?> type, String methodName, Object... args) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            if (compatible(method.getParameterTypes(), args)) {
                method.setAccessible(true);
                return method;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            if (compatible(method.getParameterTypes(), args)) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + methodName);
    }

    public static Object tryStaticNoArg(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getMethod(methodName);
            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            return method.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean compatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            Class<?> parameter = wrap(parameterTypes[i]);
            if (!parameter.isAssignableFrom(arg.getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private record MethodKey(Class<?> type, String name, List<Class<?>> argumentTypes) {

        private static MethodKey of(Class<?> type, String name, Object[] args) {
            return new MethodKey(
                    type,
                    name,
                    Arrays.stream(args)
                            .map(arg -> arg != null ? arg.getClass() : Void.class)
                            .toList()
            );
        }
    }
}
