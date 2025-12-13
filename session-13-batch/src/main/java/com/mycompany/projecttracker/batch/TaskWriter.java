package com.mycompany.projecttracker.batch;


import com.mycompany.projecttracker.entity.Task;
import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.logging.Logger;

@Named
@Dependent
public class TaskWriter extends AbstractItemWriter {

    private static final Logger LOGGER = Logger.getLogger(TaskWriter.class.getName());

    @PersistenceContext
    private EntityManager em;

    @Override
    public void writeItems(List<Object> items) throws Exception {
        LOGGER.info("--> Batch Writer: Guardando chunk de " + items.size() + " tareas.");

        for (Object obj : items) {
            Task task = (Task) obj;
            em.persist(task);
        }
        // Nota: No hace falta em.flush() ni commit.
        // El contenedor de Batch maneja la transacción JTA por cada chunk.
    }
}