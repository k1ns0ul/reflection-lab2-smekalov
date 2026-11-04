package org.example;

import org.example.classes.Shape;
import org.example.generator.Generator;

public class GenerateExample {
    public static void main(String[] args) {
        Shape shape = new Generator().generateValueOfType(Shape.class);
        System.out.printf("%s: area=%f, perimeter=%f%n",
                shape.getClass().getSimpleName(), shape.getArea(), shape.getPerimeter());
    }
}
