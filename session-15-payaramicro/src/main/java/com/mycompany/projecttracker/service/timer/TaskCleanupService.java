package com.mycompany.projecttracker.service.timer;


import com.mycompany.projecttracker.entity.Task;
import com.mycompany.projecttracker.repository.TaskRepository;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

@Singleton
@Startup
public class TaskCleanupService {

    private static final Logger logger = Logger.getLogger(TaskCleanupService.class.getName());

    @Inject
    private TaskRepository taskRepository;

    /**
     * Tarea programada: Ejecutar todos los días a media noche.
     * Sintaxis tipo CRON: hour=0, minute=0, second=0.
     * persistent=false: Si el servidor está apagado a medianoche, no intentes recuperar la ejecución perdida al encender.
     */
//    @Schedule(hour = "0", minute = "0", second = "0", persistent = false)

    // Ejecutar cada minuto, en el segundo 10
    @Schedule(hour = "*", minute = "*", second = "10", persistent = false)
    @Transactional
    public void archiveOldTasks() {
        logger.info("--> [JOB] Iniciando limpieza de tareas antiguas...");

        // 1. Definir la regla de negocio: Tareas de más de 90 días
//        LocalDate thresholdDate = LocalDate.now().minusDays(90);

        // CAMBIO TEMPORAL: Buscar tareas creadas antes de "Mañana" (para que incluya la de hoy y la del import.sql)
        LocalDate thresholdDate = LocalDate.now().plusDays(1);

        // 2. Buscar tareas candidatas
        // Buscamos tareas que estén 'Completada' y sean viejas
        List<Task> tasksToArchive = taskRepository.findOldTasks("Completada", thresholdDate);

        if (tasksToArchive.isEmpty()) {
            logger.info("--> [JOB] El sistema está limpio. No hay tareas para archivar.");
            return;
        }

        // 3. Procesar
        logger.info("--> [JOB] Se encontraron " + tasksToArchive.size() + " tareas antiguas.");

        for (Task task : tasksToArchive) {
            task.setStatus("Archivada");
            // No necesitamos llamar a repository.save(task) explícitamente.
            // Al estar en una transacción (@Transactional implícito en EJB o explícito aquí),
            // JPA detecta que cambiamos el estado y hace el UPDATE automáticamente al terminar el método.
            logger.info("----> Tarea " + task.getId() + " archivada.");
        }

        logger.info("--> [JOB] Limpieza finalizada.");
    }
}