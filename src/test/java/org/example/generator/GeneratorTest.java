package org.example.generator;

import org.example.classes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class GeneratorTest {
    private final Generator generator = new Generator();

    static Stream<Arguments> scalarTypes() {
        return Stream.of(Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
                        Float.class, Double.class, Character.class)
                .flatMap(box -> Stream.of(Arguments.of(box, box), Arguments.of(primitive(box), box)));
    }

    private static Class<?> primitive(Class<?> box) {
        try {
            return (Class<?>) box.getField("TYPE").get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @ParameterizedTest
    @MethodSource("scalarTypes")
    void producesRequestedScalar(Class<?> requested, Class<?> boxed) {
        assertInstanceOf(boxed, generator.generateValueOfType(requested));
    }

    public enum Color { RED, BLUE }
    public enum Empty {}
    public record Sample(Color color, Map<String, List<Integer>> readings) {}

    @Test
    void generatesCharactersAndEnumMembers() {
        assertTrue(generator.generateValueOfType(String.class).matches("[a-z]{1,12}"));
        assertTrue(Arrays.asList(Color.values()).contains(generator.generateValueOfType(Color.class)));
    }

    static Stream<Class<?>> arrays() {
        return Stream.of(int[].class, String[].class, Color[].class, Rectangle[].class);
    }

    @ParameterizedTest
    @MethodSource("arrays")
    void fillsArraysOfRequestedComponentType(Class<?> requested) {
        Object array = generator.generateValueOfType(requested);
        assertEquals(requested, array.getClass());
        assertTrue(Array.getLength(array) > 0);
        for (int i = 0; i < Array.getLength(array); i++) assertNotNull(Array.get(array, i));
    }

    @Test
    void fillsRawCollectionsWithDocumentedTypes() {
        List<?> list = generator.generateValueOfType(List.class);
        Map<?, ?> map = generator.generateValueOfType(Map.class);
        assertFalse(list.isEmpty());
        list.forEach(item -> assertInstanceOf(String.class, item));
        assertFalse(map.isEmpty());
        map.forEach((key, value) -> {
            assertInstanceOf(String.class, key);
            assertInstanceOf(Integer.class, value);
        });
    }

    @Test
    void resolvesGenericParametersOfANewClass() {
        Sample sample = generator.generateValueOfType(Sample.class);
        assertNotNull(sample.color());
        assertFalse(sample.readings().isEmpty());
        sample.readings().forEach((key, values) -> {
            assertFalse(key.isEmpty());
            assertFalse(values.isEmpty());
            values.forEach(value -> assertInstanceOf(Integer.class, value));
        });
    }

    @Test
    void generatesTemplateObjectsAndTheirArguments() {
        assertTrue(generator.generateValueOfType(Example.class).toString().matches("Example\\(-?\\d+\\)"));
        Cart cart = generator.generateValueOfType(Cart.class);
        assertFalse(cart.getItems().isEmpty());
        for (Product item : cart.getItems()) {
            assertFalse(item.getName().isEmpty());
            assertTrue(item.getPrice() >= 0 && item.getPrice() < 1);
        }
        BinaryTreeNode leaf = generator.generateValueOfType(BinaryTreeNode.class);
        assertNotNull(leaf.getData());
        assertNull(leaf.getLeft());
        assertNull(leaf.getRight());
        assertTrue(generator.generateValueOfType(Triangle.class).getPerimeter() >= 0);
        Shape shape = generator.generateValueOfType(Shape.class);
        assertInstanceOf(Rectangle.class, shape);
        assertTrue(Double.isFinite(shape.getArea()));
    }

    @Generatable(implementation = FixedSensor.class)
    public abstract static class Sensor {}
    public static class FixedSensor extends Sensor {}
    @Generatable(implementation = String.class)
    public interface Invalid {}
    public record First(Second next) {}
    public record Second(First next) {}
    public static class Closed { private Closed() {} }
    public static class Broken {
        public Broken() { throw new IllegalStateException("rejected"); }
    }

    @Test
    void resolvesAbstractTypeFromAnnotation() {
        assertInstanceOf(FixedSensor.class, generator.generateValueOfType(Sensor.class));
    }

    static Stream<Arguments> invalidTypes() {
        return Stream.of(Arguments.of(Runnable.class, "Missing @Generatable"),
                Arguments.of(Invalid.class, "Invalid implementation"), Arguments.of(First.class, "Cyclic"),
                Arguments.of(Empty.class, "Empty enum"), Arguments.of(Closed.class, "No public constructor"),
                Arguments.of(Void.TYPE, "void"));
    }

    @ParameterizedTest
    @MethodSource("invalidTypes")
    void explainsGenerationFailure(Class<?> type, String reason) {
        GenerationException failure = assertThrows(GenerationException.class, () -> generator.generateValueOfType(type));
        assertTrue(failure.getMessage().contains(reason), failure::getMessage);
    }

    @Test
    void preservesConstructorFailure() {
        GenerationException failure = assertThrows(GenerationException.class, () -> generator.generateValueOfType(Broken.class));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getMessage().contains(Broken.class.getName()));
    }
}
