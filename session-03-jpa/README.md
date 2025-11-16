# Sesión 3: La Capa de Persistencia (Jakarta Persistence) y Relaciones

¡Adiós, `Map` estático\! En esta sesión, conectaremos nuestra aplicación a una **base de datos real**. Para hacer esto, no escribiremos SQL (directamente). Usaremos el estándar de Jakarta EE para el Mapeo Objeto-Relacional (ORM).

Nuestra arquitectura cambiará de `REST -> Servicio -> Map` a `REST -> Servicio -> JPA -> Base de Datos`.

**Especificaciones de Jakarta EE 11 a cubrir:**

* **Jakarta Persistence 3.2 (JPA):** El estándar para la persistencia. Define cómo "mapear" una clase Java (un Objeto) a una tabla en una base de datos relacional.
* **Jakarta Transactions (JTA):** (Implícitamente) Lo usaremos para que nuestras operaciones de base de datos sean "atómicas": o se hacen todas, o no se hace ninguna.

-----

## 1\. La Arquitectura: Entidad vs. DTO

Hasta ahora, usamos un `record` de Java llamado `ProjectDTO`. Un **DTO (Data Transfer Object)** es perfecto para enviar datos *hacia* y *desde* nuestra API REST.

Pero para JPA, necesitamos una **Entidad (`@Entity`)**. Esta es una clase Java que representa una *tabla* en la base de datos.

> **¿Por qué no usar la Entidad como DTO?**
> Es una mala práctica. Las Entidades a menudo contienen relaciones complejas (que no queremos exponer en la API) y lógica de base de datos. Los DTO son "contratos" limpios para la API.
>
> **Necesitaremos:**
>
> 1.  `Project.java`: La clase Entidad (`@Entity`) que se guarda en la BBDD.
> 2.  `ProjectDTO.java`: El `record` DTO que se envía como JSON por la API.
> 3.  Un **Mapper:** Una clase simple para convertir entre los dos.

## 2\. Paso 1: Crear la Entidad `Project`

Crea un nuevo paquete `com.mycompany.projecttracker.entity`.
Dentro, crea la clase [`Project.java`](src/main/java/com/mycompany/projecttracker/entity/Project.java):

```java
package com.mycompany.projecttracker.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entidad JPA que representa la tabla 'Project' en la base de datos.
 * Especificación: Jakarta Persistence 3.2.
 */
@Entity
@Table(name = "PROJECT") // Opcional, pero buena práctica
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob // Large Object, para textos largos (mapea a CLOB o TEXT)
    private String description;

    @Column(length = 20)
    private String status;

    /**
     * Novedad JPA 3.2 (EE 11): Soporte nativo mejorado para java.time.
     * No necesitamos @Convert ni nada extra.
     */
    private LocalDate deadline;

    // --- Constructores ---

    // JPA requiere un constructor sin argumentos
    public Project() {
    }

    // --- Getters y Setters (necesarios para que JPA funcione) ---

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

    // (Opcional: puedes añadir equals() y hashCode() si lo deseas)
}
```

## 2\. Paso 2: Configurar la Base de Datos (Datasource)

Usaremos la base de datos en memoria **H2**, que viene incluida con Payara (perfecta para desarrollo).

Primero, necesitamos decirle a Payara dónde encontrar H2. El JAR de H2 ya está en Payara 7, así que solo necesitamos crear el "Pool" y el "Recurso".

Abre una terminal en la carpeta `payara7/bin` y ejecuta estos 2 comandos `asadmin`:

1.  **Crear el Pool de Conexiones:**
 
    \
    **Linux/macOS**
     ```shell
    ./asadmin create-jdbc-connection-pool \
        --datasourceclassname=org.h2.jdbcx.JdbcDataSource \
        --restype=javax.sql.DataSource \
        --property="url=jdbc\:h2\:mem\:projecttrackerdb;DB_CLOSE_DELAY\=-1" \
        ProjectTrackerPool
    ```
    \
    **Windows**
     ```powershell
    .\asadmin create-jdbc-connection-pool `
        --datasourceclassname="org.h2.jdbcx.JdbcDataSource" `
        --restype="javax.sql.DataSource" `
        --property="url=jdbc\:h2\:mem\:projecttrackerdb;DB_CLOSE_DELAY\=-1" `
        ProjectTrackerPool
    ```

    *Esto crea un pool llamado `ProjectTrackerPool` que apunta a una BBDD H2 en memoria llamada `projecttrackerdb`.*

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

        <class>com.mycompany.projecttracker.entity.Project</class>

        <properties>
            <property name="jakarta.persistence.schema-generation.database.action" value="drop-and-create"/>
            
            <property name="jakarta.persistence.sql-load-script-source" value="META-INF/sql/import.sql"/>
            <property name="eclipselink.logging.level.sql" value="FINE"/>
            <property name="eclipselink.logging.parameters" value="true"/>
        </properties>

    </persistence-unit>
</persistence>
```

## 4\. Paso 4: Script DDL `create.sql`

Podemos dejar que Jakarta Persistence cree las entidades. O - lo más recomendable es - poner el script de creación de las tablas.

Este script de creación de las tablas **debe tener un comando por línea**.

Por ello, vamos a crear el archivo [`src/main/resources/META-INF/sql/create.sql`](src/main/resources/META-INF/sql/create.sql) con el siguiente contenido:

```sql
CREATE TABLE PROJECT (  ID BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,  DEADLINE DATE,  DESCRIPTION LONGVARCHAR,  NAME VARCHAR(100) NOT NULL,  STATUS VARCHAR(20),  PRIMARY KEY (ID) );
```

También, podemos tener un script para eliminar las tablas. El archivo se llamará [`src/main/resources/META-INF/sql/drop.sql`](src/main/resources/META-INF/sql/drop.sql) con el siguiente conteido:

```sql
DROP TABLE PROJECT IF EXISTS;
```

Además, para tener datos de prueba (ya que borramos nuestro constructor en `ProjectService`), podemos añadir un script SQL.

Crea el archivo [`src/main/resources/META-INF/sql/import.sql`](src/main/resources/META-INF/sql/import.sql):

```sql
-- Inserta datos de prueba al iniciar la aplicación
INSERT INTO PROJECT (NAME, DESCRIPTION, STATUS, DEADLINE) VALUES ('Sitio Web Corporativo', 'Desarrollo del nuevo sitio web v2', 'Activo', '2025-12-31');
INSERT INTO PROJECT (NAME, DESCRIPTION, STATUS, DEADLINE) VALUES ('App Móvil (ProjectTracker)', 'Lanzamiento de la app nativa', 'Planificado', '2026-03-15');
```

Finalmente, debemos considerar las siguientes propiedades en el archivo [`persistence.xml`](src/main/resources/META-INF/persistence.xml)

```xml
    <properties>
<!--- las demás propiedades -->
      <property name="jakarta.persistence.schema-generation.create-script-source" value="META-INF/sql/create.sql"/>
      <property name="jakarta.persistence.schema-generation.drop-script-source" value="META-INF/sql/drop.sql"/>
<!-- otras propiedades -->
    </properties>
```

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

import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.mapper.ProjectMapper;
import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional // ¡MUY IMPORTANTE!
public class ProjectService {

    // 1. Ya no hay Map, ni AtomicLong, ni constructor.

    /**
     * Inyecta el EntityManager.
     * @PersistenceContext es la forma de JPA de pedirle a CDI
     * el EntityManager para nuestra unidad de persistencia.
     */
    @PersistenceContext(unitName = "project-tracker-pu")
    private EntityManager em;

    // 2. Inyectamos nuestro Mapper
    @Inject
    private ProjectMapper mapper;

    // 3. ¡@Transactional!
    // Esto le dice a Payara (JTA) que inicie una transacción de BBDD
    // antes de llamar a este método, y haga 'commit' al final.
    // Si algo falla, hace 'rollback' automáticamente.
    // Lo ponemos a nivel de clase para que aplique a todos los métodos.


    public List<ProjectDTO> findAll() {
        // Usamos JPQL (Java Persistence Query Language) - similar a SQL
        List<Project> entities = em.createQuery("SELECT p FROM Project p", Project.class)
                                   .getResultList();
        
        // Convertimos la lista de Entidades a lista de DTOs
        return entities.stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<ProjectDTO> findById(Long id) {
        // em.find es la forma más eficiente de buscar por Clave Primaria
        Project entity = em.find(Project.class, id);
        
        // Usamos Optional.ofNullable para manejar si es nulo
        return Optional.ofNullable(entity)
                       .map(mapper::toDTO);
    }

    public ProjectDTO create(ProjectDTO projectRequest) {
        // 1. Convertir DTO a Entidad
        Project newEntity = mapper.toEntity(projectRequest);
        newEntity.setStatus("Nuevo"); // Lógica de negocio

        // 2. Persistir en la BBDD
        // ¡Gracias a @Transactional, esto se guardará!
        em.persist(newEntity);

        // 3. Devolver el DTO con el ID generado
        // (em.persist() actualiza el objeto 'newEntity' con el ID)
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

    * **Crear Uno (POST):**

      ```sh
      curl -X POST http://localhost:8080/project-tracker/resources/projects \
           -H "Content-Type: application/json" \
           -d '{"name":"Proyecto con JPA", "description":"¡Guardado en H2!"}'
      ```

      *Verás una respuesta `201 Created` y el JSON del nuevo proyecto (¡con `id: 3`\!).*

    * **Verifica la creación:**
      `curl http://localhost:8080/project-tracker/resources/projects/3`
      *Verás tu proyecto recién creado.*

    * **Importante:** Si reinicias Payara, la base de datos H2 en memoria se borrará y se recreará. ¡Perfecto para desarrollo\!

-----

¡Felicidades\! Acabas de construir el núcleo de una aplicación empresarial. Tu API REST (JAX-RS) se comunica con tu lógica de negocio (CDI) que ahora persiste datos en una base de datos real usando **JPA 3.2** y **JTA**.