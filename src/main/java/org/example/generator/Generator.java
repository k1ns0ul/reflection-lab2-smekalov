package org.example.generator;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;

public class Generator {

    public Object generateValueOfType(Class<?> clazz) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        int randomConstructorIndex = new Random().nextInt(constructors.length);
        Constructor<?> randomConstructor = constructors[randomConstructorIndex];
        return randomConstructor.newInstance(111);
    }


}
