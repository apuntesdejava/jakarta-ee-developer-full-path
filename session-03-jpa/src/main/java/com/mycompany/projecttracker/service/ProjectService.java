package com.mycompany.projecttracker.service;

import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.mapper.ProjectMapper;
import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional // ¡MUY IMPORTANTE!
public class ProjectService {

    // 1. Ya no hay Map, ni AtomicLong, ni constructor.

    /**
     * Inyecta el EntityManager.
     * @PersistenceContext es la forma de JPA de pedirle a CDI
     * el EntityManager para nuestra unidad de persistencia.
     */
    @PersistenceContext(unitName = "project-tracker-pu")
    private EntityManager em;

    // 2. Inyectamos nuestro Mapper
    @Inject
    private ProjectMapper mapper;

    // 3. ¡@Transactional!
    // Esto le dice a Payara (JTA) que inicie una transacción de BBDD
    // antes de llamar a este método, y haga 'commit' al final.
    // Si algo falla, hace 'rollback' automáticamente.
    // Lo ponemos a nivel de clase para que aplique a todos los métodos.


    public List<ProjectDTO> findAll() {
        // Usamos JPQL (Java Persistence Query Language) - similar a SQL
        List<Project> entities = em.createQuery("SELECT p FROM Project p", Project.class)
            .getResultList();

        // Convertimos la lista de Entidades a lista de DTOs
        return entities.stream()
            .map(mapper::toDTO)
            .collect(Collectors.toList());
    }

    public Optional<ProjectDTO> findById(Long id) {
        // em.find es la forma más eficiente de buscar por Clave Primaria
        Project entity = em.find(Project.class, id);

        // Usamos Optional.ofNullable para manejar si es nulo
        return Optional.ofNullable(entity)
            .map(mapper::toDTO);
    }

    public ProjectDTO create(ProjectDTO projectRequest) {
        // 1. Convertir DTO a Entidad
        Project newEntity = mapper.toEntity(projectRequest);
        newEntity.setStatus("Nuevo"); // Lógica de negocio

        // 2. Persistir en la BBDD
        // ¡Gracias a @Transactional, esto se guardará!
        em.persist(newEntity);

        // 3. Devolver el DTO con el ID generado
        // (em.persist() actualiza el objeto 'newEntity' con el ID)
        return mapper.toDTO(newEntity);
    }
}