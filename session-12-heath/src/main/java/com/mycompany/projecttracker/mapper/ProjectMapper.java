package com.mycompany.projecttracker.mapper;

import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Bean CDI para mapear entre Entidad (JPA) y DTO (API).
 */
@ApplicationScoped
public class ProjectMapper {

    public ProjectDTO toDTO(Project entity) {
        if (entity == null) {
            return null;
        }
        return new ProjectDTO(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getStatus()
            // Nota: El DTO no incluye 'deadline' (por ahora, decisión de diseño)
        );
    }

    public Project toEntity(ProjectDTO dto) {
        if (dto == null) {
            return null;
        }
        Project entity = new Project();
        // No seteamos el ID, debe ser generado por la BBDD
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setStatus(dto.status());
        // (Podríamos añadir 'deadline' al DTO si quisiéramos)
        return entity;
    }
}