package com.mycompany.projecttracker.repository;

import com.mycompany.projecttracker.entity.Task;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends CrudRepository<Task, Long> {

    /**
     * Encuentra tareas con un estado específico (ej. 'Completada')
     * Y que hayan sido creadas ANTES de una fecha límite.
     */
    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.auditInfo.createdAt < :thresholdDate")
    List<Task> findOldTasks(@Param("status") String status, @Param("thresholdDate") LocalDate thresholdDate);
}
