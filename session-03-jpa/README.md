# Sesión 3: La Capa de Persistencia (Jakarta Persistence) y Relaciones

¡Adiós, `Map` estático\! En esta sesión, conectaremos nuestra aplicación a una **base de datos real**. Para hacer esto, no escribiremos SQL (directamente), y usaremos una característica **nueva de Jakarta EE 11** para que nuestro código sea más moderno.

Nuestra arquitectura cambiará de `REST -> Servicio -> Map` a `REST -> Servicio -> JPA -> Base de Datos`.

**Especificaciones de Jakarta EE 11 a cubrir:**

* **Jakarta Persistence 3.2 (JPA):** El estándar para la persistencia. Define cómo "mapear" una clase Java (un Objeto) a una tabla en una base de datos relacional.
* **Jakarta Transactions (JTA):** (Implícitamente) Lo usaremos para que nuestras operaciones de base de datos sean "atómicas": o se hacen todas, o no se hace ninguna.
* **Novedad EE 11:** Soporte nativo para `java.time` (como `LocalDate`).
* **Novedad EE 11:** Soporte para **Java Records como `@Embeddable`**.


-----

## 1\. Novedad EE 11: El Record `@Embeddable`

En lugar de añadir campos `createdBy` y `createdAt` a todas nuestras entidades, podemos agruparlos en un componente reutilizable. Con Jakarta EE 11, ¡podemos usar un `Record` de Java para esto\!

Crea un nuevo paquete `com.mycompany.projecttracker.entity`.
Dentro, crea el *record* `AuditInfo.java`:

```java
package com.mycompany.projecttracker.entity;

import jakarta.persistence.Embeddable;
import java.time.LocalDate;

/**
 * Novedad de Jakarta EE 11 / JPA 3.2:
 * Podemos usar un Java Record (inmutable, conciso)
 * como un componente "incrustable" en nuestras entidades.
 */
@Embeddable
public record AuditInfo(
    String createdBy,
    LocalDate createdAt
) {
    // Constructor sin argumentos requerido por JPA
    // para un record @Embeddable (un poco peculiar, pero necesario)
    public AuditInfo() {
        this(null, null);
    }
}
```

* `@Embeddable`: Le dice a JPA: "Esta clase no es una tabla, sino un grupo de columnas que se 'incrustarán' en otra entidad".


-----

## 1\. La Arquitectura: Entidad vs. DTO

Hasta ahora, usamos un `record` de Java llamado `ProjectDTO`. Un **DTO (Data Transfer Object)** es perfecto para enviar datos *hacia* y *desde* nuestra API REST.

Pero para JPA, necesitamos una **Entidad (`@Entity`)**. Esta es una clase Java que representa una *tabla* en la base de datos.

> **¿Por qué no usar la Entidad como DTO?**
> Es una mala práctica. Las Entidades a menudo contienen relaciones complejas (que no queremos exponer en la API) y lógica de base de datos. Los DTO son "contratos" limpios para la API.
>
> **Necesitaremos:**
>
> 1.  `Project.java` y `Task.java`: La clase Entidad (`@Entity`) que se guarda en la BBDD.
> 2.  `ProjectDTO.java`: El `record` DTO que se envía como JSON por la API.
> 3.  Un **Mapper:** Una clase simple para convertir entre la entidad y su respectivo DTO.

-----

## 2\. Definir las Entidades y Relaciones

Ahora creamos nuestras entidades `Project` y `Task`, y definimos la relación entre ellas.

### Paso 2.1: La Entidad `Task`

Creemos primero la entidad "hija".
Dentro de `com.mycompany.projecttracker.entity`, crea `Task.java`:

```java
package com.mycompany.projecttracker.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "TASK")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;
    
    private String status;

    /**
     * Esta es la relación "Dueño" (ManyToOne).
     * Muchas tareas (Many) pertenecen a Un Proyecto (One).
     *
     * FetchType.LAZY: Le dice a JPA "No cargues el objeto Project
     * completo de la BBDD hasta que yo llame a getProject()".
     * Es una práctica recomendada para el rendimiento.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROJECT_ID", nullable = false) // Define la columna de la clave foránea
    private Project project;

    // Constructor, Getters y Setters...

    public Task() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
}
```

### Paso 2.2: La Entidad `Project` (Actualizada)

Ahora actualicemos nuestra entidad `Project` para que "conozca" a sus tareas y use nuestro `AuditInfo`.

Modifica `Project.java`:

```java
package com.mycompany.projecttracker.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PROJECT")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob
    private String description;

    @Column(length = 20)
    private String status;

    // Soporte nativo de java.time (JPA 3.2)
    private LocalDate deadline;

    /**
     * ¡Aquí usamos nuestro Record @Embeddable!
     * JPA creará las columnas 'CREATEDBY' y 'CREATEDAT' en la tabla 'PROJECT'.
     */
    @Embedded
    private AuditInfo auditInfo;

    /**
     * Esta es la relación "Inversa" (OneToMany).
     * Un Proyecto (One) tiene Muchas Tareas (Many).
     *
     * mappedBy = "project": Le dice a JPA "Esta relación ya está
     * definida en la clase Task, en el campo 'project'".
     *
     * cascade = CascadeType.ALL: Si guardo un Proyecto, también
     * guarda sus tareas. Si borro un Proyecto, borra sus tareas.
     *
     * orphanRemoval = true: Si quito una tarea de esta lista,
     * bórrala de la BBDD.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

    // Constructor, Getters y Setters...

    public Project() {
    }

    // --- Métodos de ayuda para la relación (buena práctica) ---
    public void addTask(Task task) {
        tasks.add(task);
        task.setProject(this);
    }
    public void removeTask(Task task) {
        tasks.remove(task);
        task.setProject(null);
    }

    // --- Getters y Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public AuditInfo getAuditInfo() { return auditInfo; }
    public void setAuditInfo(AuditInfo auditInfo) { this.auditInfo = auditInfo; }
    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) { this.tasks = tasks; }
}
```

## 2\. Paso 2: Configurar la Base de Datos (Datasource)

Usaremos la base de datos PostgreSQL. Para ello necesitaremos dos cosas:

- Docker. Así será más fácil de ejecutar y configurar la base de datos. Aquí tengo preparado el [`docker-compose.yml`](../docker/database/docker-compose.yaml) que permite ejecutar la imagen de la base de datos.
- El driver JDBC de PostgreSQL

Para ejecutar la imagen, nos debemos encontrar en el mismo directorio del archivo `docker-compose.yml` y debemos ejecutar el comando:

```shell
docker compose up -d
```
Y en el momento, se descargará la imagen, se ejecutará el motor de base de datos, y se creará la base de datos llamada `PROJECT_TRACKER`. Su usuario y contraseña son `PROJECT_TRACKER`

Seguidamente, debemos conseguir el archivo `.jar` que es el driver para el JDBC de PostgreSQL. Este archivo lo podemos descargar desde aquí: https://jdbc.postgresql.org/download/

Bajamos la versión que indica "Java 8":

![](https://i.imgur.com/yiZZ4tv.png)

Este archivo lo debemos colocar el directorio de Payara: `$PAYARA_HOME/glassfish/domains/domain/lib`.

Si estaba iniciado Payara, lo reiniciamos. De lo contrario, iniciamos.

Abre una terminal en la carpeta `payara7/bin` y ejecuta estos 2 comandos `asadmin`:

1.  **Crear el Pool de Conexiones:**
 
    \
    **Linux/macOS**
     ```shell
    ./asadmin create-jdbc-connection-pool \
        --datasourceclassname=org.postgresql.ds.PGPoolingDataSource \
        --restype=javax.sql.DataSource \
        --property="URL=jdbc\:postgresql\://localhost\:5432/PROJECT_TRACKER:user=PROJECT_TRACKER:password=PROJECT_TRACKER" \
        ProjectTrackerPool
    ```
    \
    **Windows**
     ```powershell
    .\asadmin create-jdbc-connection-pool `
        --datasourceclassname="org.postgresql.ds.PGPoolingDataSource" `
        --restype="javax.sql.DataSource" `
        --property="URL=jdbc\:postgresql\://localhost\:5432/PROJECT_TRACKER:user=PROJECT_TRACKER:password=PROJECT_TRACKER" `
        ProjectTrackerPool
    ```

    *Esto crea un pool llamado `ProjectTrackerPool` que apunta a la base de datos que hemos creado en PostgreSQL.*
 
    > Para más información de las propiedades de JDBC de PosgreSQL, revisar la siguiente documentación: https://jdbc.postgresql.org/documentation/use/ 

2.  **Crear el Recurso JNDI:**

    \
    **Linux/macOS**
     ```shell
    ./asadmin create-jdbc-resource \
        --connectionpoolid ProjectTrackerPool \
        jdbc/projectTracker
    ```

    \
    **Windows**
     ```powershell
    .\asadmin create-jdbc-resource `
        --connectionpoolid ProjectTrackerPool `
        jdbc/projectTracker
    ```

    *Esto le da a nuestra aplicación un "nombre" fácil de encontrar para el pool: `jdbc/projectTracker`.*

## 3\. Paso 3: Crear el `persistence.xml`

JPA necesita un archivo de configuración que le diga dónde está la base de datos y qué entidades debe gestionar.

Crea la carpeta `src/main/resources/META-INF`.
Dentro, crea el archivo [`persistence.xml`](src/main/resources/META-INF/persistence.xml):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="https://jakarta.ee/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_2.xsd"
             version="3.2"> <persistence-unit name="project-tracker-pu" transaction-type="JTA">

        <jta-data-source>jdbc/projectTracker</jta-data-source>

        <properties>
            <property name="jakarta.persistence.schema-generation.database.action" value="drop-and-create"/>            
            <property name="jakarta.persistence.sql-load-script-source" value="META-INF/sql/import.sql"/>
            <property name="eclipselink.logging.level.sql" value="FINE"/>
            <property name="eclipselink.logging.parameters" value="true"/>
        </properties>

    </persistence-unit>
</persistence>
```

## 4\. Paso 4: Insertando registros de prueba `import.sql`

Dejemos que Jakarta Persistence cree las tablas.  

Además, para tener datos de prueba (ya que borramos nuestro constructor en `ProjectService`), podemos añadir un script SQL.

Crea el archivo [`src/main/resources/META-INF/sql/import.sql`](src/main/resources/META-INF/sql/import.sql):

```sql
-- Inserta Proyectos
-- (No especificamos ID, dejamos que IDENTITY lo genere)
INSERT INTO PROJECT (NAME, DESCRIPTION, STATUS, DEADLINE, CREATEDBY, CREATEDAT) VALUES ('Sitio Web Corporativo', 'Desarrollo del nuevo sitio web v2', 'Activo', '2025-12-31', 'import_user', '2025-01-01');

INSERT INTO PROJECT (NAME, DESCRIPTION, STATUS, DEADLINE, CREATEDBY, CREATEDAT) VALUES ('App Móvil (ProjectTracker)', 'Lanzamiento de la app nativa', 'Planificado', '2026-03-15', 'import_user', '2025-01-10');

-- Inserta Tareas y las vincula a los proyectos
-- Asumimos que los proyectos anteriores tendrán ID 1 y 2
INSERT INTO TASK (TITLE, STATUS, PROJECT_ID) VALUES ('Diseñar Homepage', 'Completada', 1), ('Desarrollar formulario de contacto', 'En Progreso', 1), ('Definir API de Tareas', 'Completada', 2), ('Testear login de usuario', 'Pendiente', 2);
```
> Tiene que ser un comando por línea. No intente darle formato, ya que cada línea será interpretado como comando, y dará error.

## 5\. Paso 5: Crear el Mapper (DTO \<-\> Entidad)

Crea el paquete `com.mycompany.projecttracker.mapper`.
Dentro, crea la clase [`ProjectMapper.java`](src/main/java/com/mycompany/projecttracker/mapper/ProjectMapper.java):

```java
package com.mycompany.projecttracker.mapper;

import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Bean CDI para mapear entre Entidad (JPA) y DTO (API).
 */
@ApplicationScoped
public class ProjectMapper {

    public ProjectDTO toDTO(Project entity) {
        if (entity == null) {
            return null;
        }
        return new ProjectDTO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus()
                // Nota: El DTO no incluye 'deadline' (por ahora, decisión de diseño)
        );
    }

    public Project toEntity(ProjectDTO dto) {
        if (dto == null) {
            return null;
        }
        Project entity = new Project();
        // No seteamos el ID, debe ser generado por la BBDD
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setStatus(dto.status());
        // (Podríamos añadir 'deadline' al DTO si quisiéramos)
        return entity;
    }
}
```

## 6\. Paso 6: Refactorizar `ProjectService`

¡Aquí es donde todo se une\! Vamos a reemplazar el `Map` por el `EntityManager` de JPA.

Modifica [`ProjectService.java`](src/main/java/com/mycompany/projecttracker/service/ProjectService.java):

```java
package com.mycompany.projecttracker.service;

import com.mycompany.projecttracker.entity.AuditInfo; // Importar
import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.mapper.ProjectMapper;
import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.LocalDate; // Importar
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class ProjectService {

    @PersistenceContext(unitName = "project-tracker-pu")
    private EntityManager em;

    @Inject
    private ProjectMapper mapper;

    public List<ProjectDTO> findAll() {
        List<Project> entities = em.createQuery("SELECT p FROM Project p", Project.class)
            .getResultList();

        return entities.stream()
            .map(mapper::toDTO)
            .collect(Collectors.toList());
    }

    public Optional<ProjectDTO> findById(Long id) {
        Project entity = em.find(Project.class, id);
        return Optional.ofNullable(entity)
            .map(mapper::toDTO);
    }

    public ProjectDTO create(ProjectDTO projectRequest) {
        // 1. Convertir DTO a Entidad
        Project newEntity = mapper.toEntity(projectRequest);

        // 2. ¡Establecer la lógica de negocio y auditoría!
        newEntity.setStatus("Nuevo");
        newEntity.setAuditInfo(new AuditInfo("admin_user", LocalDate.now())); // <-- ¡NUEVO!

        // 3. Persistir en la BBDD
        em.persist(newEntity);

        // 4. Devolver el DTO con el ID generado
        return mapper.toDTO(newEntity);
    }
}
```

## 7\. Probar la Aplicación Real

1.  **Reinicia Payara:** (Necesario para que detecte los nuevos `asadmin commands`).

    ```sh
    ./asadmin stop-domain
    ./asadmin start-domain
    ```

2.  **Construye y Despliega:**

    ```sh
    mvn clean package
    ```

    Copia el `target/project-tracker.war` a la carpeta `autodeploy`.

3.  **Verifica la consola de Payara:**
    Deberías ver el SQL de `drop-and-create` y el de `import.sql` ejecutándose.

4.  **Prueba con cURL o Postman:**

    * **Obtener Todos (GET):**
      `curl http://localhost:8080/project-tracker/resources/projects`
      *Verás el JSON de los dos proyectos del archivo `import.sql`.*
      ![](https://i.imgur.com/DW1Gqaz.png) 

    * **Crear Uno (POST):**

      ```sh
      curl -X POST http://localhost:8080/project-tracker/resources/projects \
           -H "Content-Type: application/json" \
           -d '{"name":"Proyecto con JPA", "description":"¡Guardado en H2!"}'
      ```

      *Verás una respuesta `201 Created` y el JSON del nuevo proyecto.*      
      ![](https://i.imgur.com/Z395pXa.png)     
    
    * **Verifica la creación:**
      `curl http://localhost:8080/project-tracker/resources/projects/3`
      *Verás tu proyecto recién creado.*
      ![](https://i.imgur.com/riiblGC.png) 

    * **Importante:** Si reinicias Payara, la base de datos se recreará por la opción `drop-and-create`. ¡Perfecto para desarrollo\!

-----

¡Felicidades\! Acabas de construir el núcleo de una aplicación empresarial. Tu API REST (JAX-RS) se comunica con tu lógica de negocio (CDI) que ahora persiste datos en una base de datos real usando **JPA 3.2** y **JTA**.