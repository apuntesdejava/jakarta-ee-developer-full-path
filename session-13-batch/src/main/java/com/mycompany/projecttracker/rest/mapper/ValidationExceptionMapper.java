package com.mycompany.projecttracker.rest.mapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.Map;

/**
 * Captura las excepciones de validación (@Valid) y las transforma
 * en una respuesta 400 Bad Request clara para el cliente.
 */
@Provider // Le dice a JAX-RS que esta clase es un "proveedor" que debe registrar
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {

        // Convertimos el Set de violaciones en una lista de mensajes simples
        List<String> errors = exception.getConstraintViolations().stream()
            .map(this::formatError)
            .toList();

        // Creamos un cuerpo de respuesta JSON
        // (Podríamos usar un Record/Clase si quisiéramos)
        Map<String, Object> responseBody = Map.of(
            "message", "La petición tiene errores de validación",
            "errors", errors
        );

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(responseBody)
            .build();
    }

    /**
     * Formatea un error de 'campo: mensaje'
     * Ej: "name: El nombre no puede ser nulo"
     */
    private String formatError(ConstraintViolation<?> violation) {
        // Obtenemos el nombre del campo (ej. "name")
        String field = violation.getPropertyPath().toString();
        // Obtenemos el mensaje (ej. "El nombre no puede ser nulo")
        String message = violation.getMessage();

        // 'violation.getPropertyPath()' puede ser complejo (ej. 'createProject.arg0.name')
        // Lo simplificamos para obtener solo el nombre del campo final.
        String[] parts = field.split("\\.");
        if (parts.length > 0) {
            field = parts[parts.length - 1];
        }

        return field + ": " + message;
    }
}