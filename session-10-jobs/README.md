# Sesión 10: Tareas Programadas (Background Jobs)

En esta sesión, implementaremos un proceso de "limpieza" automático. Ninguna aplicación empresarial está completa sin tareas que corren en segundo plano: reportes nocturnos, limpieza de temporales o archivado de datos históricos.

Vamos a implementar un **"Cron Job"** que se ejecute automáticamente para archivar tareas antiguas y mantener nuestra base de datos optimizada.

**Especificaciones de Jakarta EE 11 a cubrir:**

* **Jakarta Enterprise Beans 4.0 Lite (EJB):** Usaremos el servicio de temporizador (`TimerService`) mediante la anotación `@Schedule`. Es robusto, transaccional y resistente a reinicios.

-----

## 1\. Paso 1: Preparar el Modelo de Datos

Para saber si una tarea es "vieja", necesitamos saber cuándo se creó. En sesiones anteriores añadimos auditoría al *Proyecto*, pero no a la *Tarea*.

### 1.1 Modificar la Entidad `Task`

Abre [`com.mycompany.projecttracker.entity.Task`](src/main/java/com/mycompany/projecttracker/entity/Task.java) y añade el campo `auditInfo`.

```java
// ... imports
import java.time.LocalDate;

@Entity
@Table(name = "TASK")
public class Task {

    // ... (id, title, status, project...)

    // --- NUEVO: Auditoría para saber cuándo se creó ---
    @Embedded
    private AuditInfo auditInfo;

    // --- Getter y Setter ---
    public AuditInfo getAuditInfo() { return auditInfo; }
    public void setAuditInfo(AuditInfo auditInfo) { this.auditInfo = auditInfo; }
}
```

### 1.2 Actualizar el Servicio

Abre [`com.mycompany.projecttracker.service.ProjectService`](src/main/java/com/mycompany/projecttracker/service/ProjectService.java). En el método `createTask`, asegúrate de inicializar este dato.

```java
    public TaskDTO createTask(Long projectId, TaskDTO taskDto) {
        // ... (lógica previa) ...
        
        Task newTask = new Task();
        newTask.setTitle(taskDto.title());
        newTask.setStatus("Pendiente");
        
        // --- NUEVO: Guardar fecha actual ---
        newTask.setAuditInfo(new AuditInfo("sistema", LocalDate.now()));

        // ... (lógica de persistencia y JMS) ...
    }
```

-----

## 2\. Paso 2: Crear el Repositorio de Tareas

Necesitamos una consulta eficiente para encontrar las tareas candidatas a ser archivadas. Usaremos **Jakarta Data** para crear esta consulta sin escribir implementación.

Crea la interfaz [`com.mycompany.projecttracker.repository.TaskRepository`](src/main/java/com/mycompany/projecttracker/repository/TaskRepository.java):

```java
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

```

-----

## 3\. Paso 3: Crear el Servicio Programado (TaskCleanupService)

Aquí es donde ocurre la magia de Jakarta EE. Usaremos un **Singleton EJB**.

* `@Singleton`: Solo existe una instancia de esta clase en el servidor.
* `@Startup`: Se inicializa apenas arranca la aplicación (no espera a la primera petición HTTP).
* `@Schedule`: Define cuándo se ejecuta.

Crea el paquete `com.mycompany.projecttracker.service.timer` y la clase [`TaskCleanupService.java`](src/main/java/com/mycompany/projecttracker/service/timer/TaskCleanupService.java):

```java
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
    @Schedule(hour = "0", minute = "0", second = "0", persistent = false)
    @Transactional
    public void archiveOldTasks() {
        logger.info("--> [JOB] Iniciando limpieza de tareas antiguas...");

        // 1. Definir la regla de negocio: Tareas de más de 90 días
        LocalDate thresholdDate = LocalDate.now().minusDays(90);

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
```

-----

## 4\. Paso 4: Probar la Tarea (El Truco)

Esperar a medianoche o crear datos con fecha de hace 90 días es complicado para un tutorial rápido. Vamos a modificar el código temporalmente para ver que funciona **ahora mismo**.

### 4.1 Modificar `import.sql`

Añade una tarea "vieja" en [`src/main/resources/META-INF/import.sql`](src/main/resources/META-INF/sql/import.sql).
*(Nota: Como las tablas se vuelven a crear para este ejemplo,  esto asegura que siempre tengamos datos de prueba).*

```sql
-- Insertar una tarea que ya está completada, con fecha antigua (simulada)
-- Asumiendo que el Proyecto 1 existe.
INSERT INTO TASK (TITLE, STATUS, PROJECT_ID, CREATEDBY, CREATEDAT) VALUES ('Tarea Vieja de Prueba', 'Completada', 1, 'admin', '2020-01-01');
```

### 4.2 Modificar el `@Schedule` temporalmente

En `[TaskCleanupService.java`](src/main/java/com/mycompany/projecttracker/service/timer/TaskCleanupService.java), cambia la anotación para que corra **cada minuto**:

```java
// Ejecutar cada minuto, en el segundo 10
@Schedule(hour = "*", minute = "*", second = "10", persistent = false)
public void archiveOldTasks() {
    // ...
    // CAMBIO TEMPORAL: Buscar tareas creadas antes de "Mañana" (para que incluya la de hoy y la del import.sql)
    LocalDate thresholdDate = LocalDate.now().plusDays(1); 
    // ...
}
```

### 4.3 Ejecutar

1.  Haz `mvn clean package`.
2.  Copia el `.war` a `autodeploy`.
3.  Abre el log (`server.log`).
4.  Espera a que el reloj del sistema marque el segundo `:10`.

**Deberías ver:**

```text
INFO: --> [JOB] Iniciando limpieza de tareas antiguas...
INFO: --> [JOB] Se encontraron 1 tareas antiguas.
INFO: ----> Tarea [ID] archivada.
INFO: --> [JOB] Limpieza finalizada.
```

Si ves esto, ¡felicidades\! Has implementado un sistema de Jobs automatizado.

![](https://i.imgur.com/gSQYylT.png)
-----

## Nota sobre Jakarta Concurrency vs EJB

En este tutorial hemos usado **EJB (`@Schedule`)**. ¿Por qué?

* **Declarativo:** Una sola línea de código define la frecuencia.
* **Transaccional:** Los EJBs inician transacciones automáticamente. Si el archivado falla a la mitad, se hace rollback de todo.
* **Jakarta Concurrency 3.1:** Aunque permite crear `ManagedScheduledExecutorService`, su uso es **programático** (tienes que escribir código Java para inicializarlo y calcular los periodos). Para tareas tipo "Cron" (horarios fijos), EJB sigue siendo superior y más simple en Jakarta EE 11.

-----

¡Y con esto terminamos la Sesión 10\! Tu aplicación ahora tiene mantenimiento automático.