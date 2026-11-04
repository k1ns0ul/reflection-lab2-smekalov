package org.example.classes;

import org.example.generator.Generatable;

@Generatable(implementation = Rectangle.class)
public interface Shape {
    double getArea();
    double getPerimeter();
}