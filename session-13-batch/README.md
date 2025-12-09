# Sesión 13: Procesamiento por Lotes (Jakarta Batch)

Imagina que tu jefe te dice: *"Tenemos un archivo CSV con 10,000 tareas antiguas de otro sistema y necesitamos importarlas a ProjectTracker esta noche"*

Si haces un bucle `for` normal, podrías quedarte sin memoria o bloquear la base de datos (timeouts). **Jakarta Batch** está diseñado para esto: procesa datos en "trozos" (chunks), maneja fallos, permite reiniciar trabajos interrumpidos y no bloquea la memoria.

**Objetivo:** Crear un proceso Batch para importar tareas masivamente desde un archivo CSV.
**Especificaciones de Jakarta EE 11 a cubrir:**

* [**Jakarta Batch 2.1:**](https://jakarta.ee/specifications/batch/2.1/) El estándar para procesamiento por lotes.

-----

## 1\. Paso 1: Dependencias

Jakarta Batch requiere algunas dependencias que a veces no vienen activadas por defecto en el autocompletado de todos los IDEs, aunque están en Payara. Asegurémonos de tener la API.

Añade (o verifica) en tu `pom.xml`:

```xml
<dependency>
    <groupId>jakarta.batch</groupId>
    <artifactId>jakarta.batch-api</artifactId>
    <version>2.1.1</version>
    <scope>provided</scope>
</dependency>
```

-----

## 2\. Paso 2: Los Componentes del Batch (Reader, Processor, Writer)

Jakarta Batch usa el patrón **"Chunk"** (Trozo): Lee un ítem, Procesa un ítem... y cuando junta X ítems (ej. 10), los Escribe todos juntos en la base de datos (commit).

Vamos a crear el paquete `com.mycompany.projecttracker.batch`.

### 2.1 El Lector ([`TaskReader`](src/main/java/com/mycompany/projecttracker/batch/TaskReader.java))

Lee líneas de un CSV ficticio (o real). Para simplificar el tutorial, simularemos que leemos de una lista en memoria, pero la lógica es idéntica a leer un archivo `InputStream`.

```java
package com.mycompany.projecttracker.batch;

import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named; 
@Named
@Dependent
public class TaskReader extends AbstractItemReader {

    private String[] rawCsvData;
    private int index;

    @Override
    public void open(java.io.Serializable checkpoint) throws Exception {
        // Simulamos un archivo CSV cargado
        rawCsvData = new String[]{
            "Importar Datos,Pendiente,1",
            "Analizar Logs,En Progreso,1",
            "Limpiar Cache,Completada,1",
            "Revisar Seguridad,Pendiente,1"
        };
        index = 0;
    }

    @Override
    public Object readItem() throws Exception {
        if (index < rawCsvData.length) {
            // Retornamos la línea cruda
            return rawCsvData[index++]; 
        }
        return null; // Null indica fin del archivo
    }
}
```

### 2.2 El Procesador ([`TaskProcessor`](src/main/java/com/mycompany/projecttracker/batch/TaskProcessor.java))

Recibe la línea de texto (String) y la convierte en una Entidad `Task` válida.

```java
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
```

### 2.3 El Escritor ([`TaskWriter`](src/main/java/com/mycompany/projecttracker/batch/TaskWriter.java))

Recibe una lista de tareas (el "Chunk") y las guarda en la base de datos.

```java
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
```

-----

## 3\. Paso 3: Definir el Job (XML)

Jakarta Batch separa la lógica (Java) de la definición del flujo (XML).

Crea el archivo [`src/main/resources/META-INF/batch-jobs/taskImportJob.xml`](src/main/resources/META-INF/batch-jobs/taskImportJob.xml).
*(La carpeta `batch-jobs` es obligatoria dentro de META-INF).*

```xml
<job id="taskImportJob" xmlns="https://jakarta.ee/xml/ns/jakartaee" version="2.0">
    <step id="importStep">
        <chunk item-count="3"> <reader ref="taskReader"/>
            <processor ref="taskProcessor"/>
            <writer ref="taskWriter"/>
        </chunk>
    </step>
</job>
```

-----

## 4\. Paso 4: Ejecutar el Batch (Endpoint REST)

Los trabajos Batch no se ejecutan solos; alguien debe iniciarlos. Crearemos un endpoint para disparar la importación.

Modifica [`com.mycompany.projecttracker.rest.ProjectResource`](src/main/java/com/mycompany/projecttracker/rest/ProjectResource.java) (o crea un `BatchResource` separado):

```java
// ... imports
import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import java.util.Properties;

// ... dentro de la clase ...

    @POST
    @Path("/import")
    @RolesAllowed("ADMIN")
    public Response runImport() {
        // 1. Obtener el operador de Batch del contenedor
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        
        // 2. Iniciar el trabajo por su nombre XML (sin la extensión .xml)
        long executionId = jobOperator.start("taskImportJob", new Properties());
        
        return Response.accepted()
                .entity("Job de importación iniciado con Execution ID: " + executionId)
                .build();
    }
```

-----

## 5\. Probar la Importación Masiva

1.  **Construye y Despliega:** `mvn clean package`.

2.  **Asegúrate de tener el Proyecto 1:** (El `import.sql` lo crea, así que debería estar ahí).

3.  **Llama al Endpoint:**

    ```sh
    curl -X POST http://localhost:8080/project-tracker/resources/projects/import \
         -u admin:admin123
    ```

4.  **Revisa los Logs:**

    Deberías ver algo fascinante: la división del trabajo.
    Como pusimos `item-count="3"` y tenemos 4 ítems:

    ```text
    INFO: --> Batch Writer: Guardando chunk de 3 tareas.  <-- Primer Commit
    INFO: --> Batch Writer: Guardando chunk de 1 tareas.  <-- Segundo Commit (el sobrante)
    ```
    ![](https://i.imgur.com/3jX5uON.png)
5.  **Verifica en la base de datos:**
    Revisa la base de datos y verás las nuevas tareas ("Importar Datos", "Analizar Logs", etc.) agregadas al Proyecto 1. 
    ![](https://i.imgur.com/4qRWpIv.png)

-----

**Resumen:**
Has implementado un sistema robusto para cargar datos. A diferencia de un simple bucle, si el CSV tuviera 1 millón de filas, este sistema procesaría de 3 en 3 (o de 1000 en 1000), manteniendo la memoria baja y las transacciones de base de datos saludables.
