package com.mycompany.projecttracker.event;

import com.mycompany.projecttracker.model.ProjectDTO;

/**
 * Evento CDI que se dispara cuando un proyecto es creado exitosamente.
 * Es un simple contenedor de datos.
 */
public record ProjectCreatedEvent(ProjectDTO project) {
}