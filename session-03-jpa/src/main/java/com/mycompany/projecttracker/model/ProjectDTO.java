package com.mycompany.projecttracker.model;

/**
 * DTO (Data Transfer Object) para un Proyecto.
 * Usamos un 'record' de Java para una definición concisa e inmutable.
 * JSON-B 3.1 sabe cómo manejar 'records' automáticamente.
 */
public record ProjectDTO(
    Long id,
    String name,
    String description,
    String status
) {
    // ¡Eso es todo! Un record nos da constructores, getters,
    // equals(), hashCode() y toString() gratis.
}