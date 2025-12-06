package com.mycompany.projecttracker.service;

import com.mycompany.projecttracker.entity.AuditInfo;
import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.entity.Task;
import com.mycompany.projecttracker.mapper.ProjectMapper;
import com.mycompany.projecttracker.model.ProjectDTO;
import com.mycompany.projecttracker.model.TaskDTO;
import com.mycompany.projecttracker.repository.ProjectRepository;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
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

    // 0. Siempre es bueno usar un Logger
    private static final Logger LOGGER = Logger.getLogger(ProjectService.class.getName());

    @Inject
    private ProjectRepository repository;

    @Inject
    private ProjectMapper mapper;

    // 1. Inyectamos el contexto JMS (Maneja conexiones automáticamente)
    @Inject
    private JMSContext jmsContext;

    // 2. Inyectamos la cola que definimos en el Paso 1
    @Resource(lookup = "java:app/jms/TaskQueue")
    private Queue taskQueue;

    // 3. Para manejar directamente Jakarta Persistence
    @PersistenceContext(unitName = "project-tracker-pu")
    private EntityManager em;

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
        Project newEntity = mapper.toEntity(projectRequest);
        newEntity.setStatus("Nuevo");
        newEntity.setAuditInfo(new AuditInfo("admin_user", LocalDate.now()));
        newEntity = repository.save(newEntity);

        return mapper.toDTO(newEntity);
    }

    /**
     * Crea una tarea asociada a un proyecto y notifica por JMS.
     */
    public TaskDTO createTask(Long projectId, TaskDTO taskDto) {
        // A. Buscar el proyecto (usando repository)
        Project project = repository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado: " + projectId));

        // B. Crear la entidad Tarea
        Task newTask = new Task();
        newTask.setTitle(taskDto.title());
        newTask.setStatus("Pendiente");

        // C. Usar el método helper del Proyecto para mantener la coherencia
        project.addTask(newTask);

        // D. Guardar la nueva Tarea directamente por el Jakarta Persistence.
        em.persist(newTask);
        em.flush(); //al hacer flush, obtenemos su ID inmediatamente

        // E. ENVIAR MENSAJE JMS (¡Aquí ocurre la magia!)
        // Enviamos un String simple: "ID_PROYECTO:ID_TAREA"
        String messagePayload = project.getId() + ":" + newTask.getId();

        jmsContext.createProducer().send(taskQueue, messagePayload);

        LOGGER.info("--> JMS: Mensaje enviado a la cola para la tarea " + newTask.getId());

        return new TaskDTO(newTask.getId(), newTask.getTitle(), newTask.getStatus());
    }
}