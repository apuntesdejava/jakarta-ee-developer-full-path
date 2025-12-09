package com.mycompany.projecttracker.service.qualifier;

import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Nuestro Qualifier personalizado para seleccionar un tipo de saludo.
 */
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER, TYPE})
public @interface GreetingType {
    /**
     * Definimos un miembro para poder especificar qué tipo queremos.
     * (Podríamos usar un Enum, pero String es simple)
     */
    String value() default "default";
}