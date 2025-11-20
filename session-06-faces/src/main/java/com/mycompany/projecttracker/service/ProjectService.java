package com.mycompany.projecttracker.service;

import com.mycompany.projecttracker.entity.AuditInfo;
import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.mapper.ProjectMapper;
import com.mycompany.projecttracker.model.ProjectDTO;
import com.mycompany.projecttracker.repository.ProjectRepository; // <-- Importar Repositorio
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class ProjectService {

    // 1. ¡ADIÓS EntityManager!
    // @PersistenceContext(unitName = "project-tracker-pu")
    // private EntityManager em;  <-- BORRADO

    // 2. ¡HOLA ProjectRepository!
    @Inject
    private ProjectRepository repository;

    @Inject
    private ProjectMapper mapper;

    public List<ProjectDTO> findAll() {
        // ANTES:
        // List<Project> entities = em.createQuery("SELECT p FROM ...").getResultList();

        // AHORA:
        // repository.findAll() devuelve un Stream en Jakarta Data
        return repository.findAll()
            .map(mapper::toDTO)
            .collect(Collectors.toList());
    }

    public Optional<ProjectDTO> findById(Long id) {
        // ANTES:
        // Project entity = em.find(Project.class, id);
        // return Optional.ofNullable(entity)...

        // AHORA:
        // repository.findById(id) ya devuelve un Optional<Project> nativamente
        return repository.findById(id)
            .map(mapper::toDTO);
    }

    // Nuevo método para probar nuestra consulta personalizada
    public List<ProjectDTO> findByStatus(String status) {
        return repository.findByStatus(status).stream()
            .map(mapper::toDTO)
            .collect(Collectors.toList());
    }

    public ProjectDTO create(ProjectDTO projectRequest) {
        Project newEntity = mapper.toEntity(projectRequest);

        // Lógica de negocio
        newEntity.setStatus("Nuevo");
        newEntity.setAuditInfo(new AuditInfo("admin_user", LocalDate.now()));

        // ANTES: em.persist(newEntity);

        // AHORA: El repositorio devuelve la entidad guardada (con el ID generado)
        newEntity = repository.save(newEntity);

        return mapper.toDTO(newEntity);
    }
}