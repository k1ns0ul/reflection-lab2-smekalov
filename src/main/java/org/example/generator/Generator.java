package org.example.generator;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Generator {
    private final Random random = new Random();

    public <T> T generateValueOfType(Class<T> type) {
        if (type == null) {
            throw new GenerationException("Requested type is null");
        }
        // Reflection boxes primitive results, including requests such as int.class.
        @SuppressWarnings("unchecked")
        T result = (T) generate(type, new HashSet<>());
        return result;
    }

    private Object generate(Type type, Set<Class<?>> constructing) {
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (parameterized.getRawType() == List.class) {
                return list(arguments[0], constructing);
            }
            if (parameterized.getRawType() == Map.class) {
                return map(arguments[0], arguments[1], constructing);
            }
        }
        if (!(type instanceof Class<?> cls)) {
            throw new GenerationException("Unsupported type: " + type.getTypeName());
        }
        if (cls == List.class) return list(String.class, constructing);
        if (cls == Map.class) return map(String.class, Integer.class, constructing);
        if (cls == boolean.class || cls == Boolean.class) return random.nextBoolean();
        if (cls == byte.class || cls == Byte.class) return (byte) random.nextInt(256);
        if (cls == short.class || cls == Short.class) return (short) random.nextInt(65536);
        if (cls == int.class || cls == Integer.class) return random.nextInt();
        if (cls == long.class || cls == Long.class) return random.nextLong();
        if (cls == float.class || cls == Float.class) return random.nextFloat();
        if (cls == double.class || cls == Double.class) return random.nextDouble();
        if (cls == char.class || cls == Character.class) return letter();
        if (cls == String.class) {
            StringBuilder text = new StringBuilder();
            int length = 1 + random.nextInt(12);
            for (int i = 0; i < length; i++) text.append(letter());
            return text.toString();
        }
        if (cls.isEnum()) {
            Object[] constants = cls.getEnumConstants();
            if (constants.length == 0) throw new GenerationException("Empty enum: " + cls.getName());
            return constants[random.nextInt(constants.length)];
        }
        if (cls.isArray()) {
            int length = size();
            Object array = Array.newInstance(cls.getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Array.set(array, i, generate(cls.getComponentType(), constructing));
            }
            return array;
        }
        if (cls == void.class || cls == Void.class) {
            throw new GenerationException("Cannot generate void");
        }
        if (cls.isInterface() || Modifier.isAbstract(cls.getModifiers())) {
            Generatable rule = cls.getDeclaredAnnotation(Generatable.class);
            if (rule == null) throw new GenerationException("Missing @Generatable on " + cls.getName());
            Class<?> implementation = rule.implementation();
            if (!cls.isAssignableFrom(implementation) || Modifier.isAbstract(implementation.getModifiers())) {
                throw new GenerationException("Invalid implementation " + implementation.getName() + " for " + cls.getName());
            }
            return generate(implementation, constructing);
        }
        return construct(cls, constructing);
    }

    private Object construct(Class<?> cls, Set<Class<?>> constructing) {
        if (!constructing.add(cls)) {
            throw new GenerationException("Cyclic constructor dependency: " + cls.getName());
        }
        try {
            Constructor<?> constructor = Arrays.stream(cls.getConstructors())
                    .max(Comparator.<Constructor<?>>comparingInt(Constructor::getParameterCount)
                            .thenComparing(Constructor::toGenericString))
                    .orElseThrow(() -> new GenerationException("No public constructor: " + cls.getName()));
            Type[] types = constructor.getGenericParameterTypes();
            Object[] arguments = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                // A direct self-reference is a terminal child (e.g. a tree leaf).
                if (types[i] != cls) arguments[i] = generate(types[i], constructing);
            }
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            throw new GenerationException("Constructor failed: " + cls.getName(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new GenerationException("Cannot instantiate " + cls.getName(), e);
        } finally {
            constructing.remove(cls);
        }
    }

    private char letter() {
        return (char) ('a' + random.nextInt(26));
    }

    private int size() {
        return 1 + random.nextInt(4);
    }

    private List<?> list(Type element, Set<Class<?>> constructing) {
        int length = size();
        List<Object> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) result.add(generate(element, constructing));
        return result;
    }

    private Map<?, ?> map(Type key, Type value, Set<Class<?>> constructing) {
        int attempts = size();
        Map<Object, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < attempts; i++) {
            result.put(generate(key, constructing), generate(value, constructing));
        }
        return result;
    }
}
