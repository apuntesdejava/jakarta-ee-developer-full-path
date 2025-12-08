package com.mycompany.projecttracker.service;

import com.mycompany.projecttracker.entity.AuditInfo;
import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.entity.Task;
import com.mycompany.projecttracker.event.ProjectCreatedEvent;
import com.mycompany.projecttracker.mapper.ProjectMapper;
import com.mycompany.projecttracker.model.ProjectDTO;
import com.mycompany.projecttracker.model.TaskDTO;
import com.mycompany.projecttracker.repository.ProjectRepository;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class ProjectService {

    private static final Logger LOGGER = Logger.getLogger(ProjectService.class.getName());

    @Inject
    private ProjectRepository repository;

    @Inject
    private ProjectMapper mapper;

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:app/jms/TaskQueue")
    private Queue taskQueue;

    @PersistenceContext(unitName = "project-tracker-pu")
    private EntityManager em;

    // 1. Inyectamos el disparador de eventos
    @Inject
    private Event<ProjectCreatedEvent> projectEvent;

    public List<ProjectDTO> findAll() {
        return repository.findAll()
            .map(mapper::toDTO)
            .collect(Collectors.toList());
    }

    public Optional<ProjectDTO> findById(Long id) {
        return repository.findById(id)
            .map(mapper::toDTO);
    }

    public List<ProjectDTO> findByStatus(String status) {
        return repository.findByStatus(status).stream()
            .map(mapper::toDTO)
            .collect(Collectors.toList());
    }

    public ProjectDTO create(ProjectDTO projectRequest) {
        // ... (lógica de mapeo y guardado en repository) ...
        Project newEntity = mapper.toEntity(projectRequest);
        newEntity.setStatus("Nuevo");
        newEntity.setAuditInfo(new AuditInfo("admin_user", LocalDate.now()));

        newEntity = repository.save(newEntity);

        ProjectDTO createdDto = mapper.toDTO(newEntity);

        // 2. ¡DISPARAMOS EL EVENTO!
        // Esto notificará a cualquier @Observes en la aplicación de forma síncrona
        // (o asíncrona si usamos fireAsync, pero usaremos fire() por simplicidad).
        projectEvent.fire(new ProjectCreatedEvent(createdDto));

        LOGGER.info("--> Evento CDI disparado para Proyecto ID: " + createdDto.id());

        return createdDto;
    }

    /**
     * Crea una tarea asociada a un proyecto y notifica por JMS.
     */
    public TaskDTO createTask(Long projectId, TaskDTO taskDto) {
        // A. Buscar el proyecto (usando repository)
        Project project = repository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado: " + projectId));

        Task newTask = new Task();
        newTask.setTitle(taskDto.title());
        newTask.setStatus("Pendiente");


        // --- NUEVO: Guardar fecha actual ---
        newTask.setAuditInfo(new AuditInfo("sistema", LocalDate.now()));

        project.addTask(newTask);

        em.persist(newTask);
        em.flush();
        String messagePayload = project.getId() + ":" + newTask.getId();

        jmsContext.createProducer().send(taskQueue, messagePayload);

        LOGGER.info("--> JMS: Mensaje enviado a la cola para la tarea " + newTask.getId());

        return new TaskDTO(newTask.getId(), newTask.getTitle(), newTask.getStatus());
    }
}