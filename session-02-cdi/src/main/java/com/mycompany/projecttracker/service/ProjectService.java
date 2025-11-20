package com.mycompany.projecttracker.service;

import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de lógica de negocio para gestionar Proyectos.
 * Especificación: CDI 4.1.
 */
@ApplicationScoped // ¡La anotación clave de CDI!
public class ProjectService {

    // --- ¡Nuestra base de datos simulada se ha mudado aquí! ---
    private final Map<Long, ProjectDTO> projectDatabase = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    // Constructor para poblar datos de prueba
    public ProjectService() {
        System.out.println("ProjectService inicializado... (Singleton)");
        long id1 = idCounter.incrementAndGet();
        projectDatabase.put(id1,
            new ProjectDTO(id1, "Sitio Web Corporativo", "Desarrollo del nuevo sitio web v2", "Activo"));
        long id2 = idCounter.incrementAndGet();
        projectDatabase.put(id2,
            new ProjectDTO(id2, "App Móvil (ProjectTracker)", "Lanzamiento de la app nativa", "Planificado"));
    }

    // --- Métodos de Lógica de Negocio ---

    public List<ProjectDTO> findAll() {
        return new ArrayList<>(projectDatabase.values());
    }

    public Optional<ProjectDTO> findById(Long id) {
        // Optional es una mejor práctica que devolver null
        return Optional.ofNullable(projectDatabase.get(id));
    }

    public ProjectDTO create(ProjectDTO projectRequest) {
        // Genera un nuevo ID y crea el DTO
        long newId = idCounter.incrementAndGet();
        ProjectDTO newProject = new ProjectDTO(
            newId,
            projectRequest.name(),
            projectRequest.description(),
            "Nuevo" // Estado por defecto
        );

        projectDatabase.put(newId, newProject);
        return newProject;
    }
}