package com.mycompany.projecttracker.batch;

import com.mycompany.projecttracker.entity.AuditInfo;
import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.entity.Task;
import com.mycompany.projecttracker.repository.ProjectRepository;
import jakarta.batch.api.chunk.ItemProcessor;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.LocalDate;
import java.util.Optional;
import java.util.logging.Logger;

@Named
@Dependent
public class TaskProcessor implements ItemProcessor {

    @Inject
    private ProjectRepository projectRepository;

    private static final Logger LOGGER = Logger.getLogger(TaskProcessor.class.getName());

    @Override
    public Object processItem(Object item) throws Exception {
        String line = (String) item;
        String[] parts = line.split(",");

        String title = parts[0];
        String status = parts[1];
        Long projectId = Long.parseLong(parts[2]);

        // Validar que el proyecto exista
        Optional<Project> projectOpt = projectRepository.findById(projectId);

        if (projectOpt.isEmpty()) {
            LOGGER.info("--> Batch Error: Proyecto ID " + projectId + " no encontrado. Saltando línea.");
            return null; // Al retornar null, este ítem se descarta y no pasa al Writer
        }

        Task task = new Task();
        task.setTitle(title);
        task.setStatus(status);
        task.setProject(projectOpt.get());
        task.setAuditInfo(new AuditInfo("batch_import", LocalDate.now()));

        return task; // Retornamos la entidad lista para guardar
    }
}