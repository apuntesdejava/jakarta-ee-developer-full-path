package com.mycompany.projecttracker.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para un Proyecto.
 * ¡Ahora con reglas de validación de Jakarta Validation 3.1!
 *
 * Estas anotaciones se aplican a los componentes del Record.
 */
public record ProjectDTO(
    Long id,

    @NotNull(message = "El nombre no puede ser nulo")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    String name,

    @Size(max = 5000, message = "La descripción no puede exceder los 5000 caracteres")
    String description,

    String status
) {
    // El cuerpo del record sigue vacío.
    // Las anotaciones se colocan directamente en los componentes.
}