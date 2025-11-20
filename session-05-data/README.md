# Sesión 5: Acceso a Datos Simplificado (Jakarta Data)

Hasta ahora, en nuestro `ProjectService`, hemos estado usando el `EntityManager` de JPA directamente. Esto implica:

1.  Escribir consultas JPQL manuales (`SELECT p FROM Project p...`).
2.  Gestionar transacciones manualmente (o confiar en el contenedor).
3.  Repetir código CRUD (Create, Read, Update, Delete) básico.

En **Jakarta EE 11**, esto cambia radicalmente con la llegada de **[Jakarta Data 1.0](https://jakarta.ee/specifications/data/)**. Esta especificación nos permite definir **interfaces** declarativas, y el servidor (Payara) genera automáticamente la implementación por nosotros.

**Objetivo:** Reemplazar el `EntityManager` manual por un `Repository` declarativo.

-----

## 1\. Paso 1: Crear el Repositorio

Vamos a crear una interfaz que se encargará de hablar con la base de datos.

Crea un nuevo paquete: `com.mycompany.projecttracker.repository`.
Dentro, crea la interfaz [`ProjectRepository.java`](src/main/java/com/mycompany/projecttracker/repository/ProjectRepository.java):

```java
package com.mycompany.projecttracker.repository;

import com.mycompany.projecttracker.entity.Project;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import java.util.List;

/**
 * Repositorio de Jakarta Data 1.0.
 *
 * @Repository: Indica a CDI que debe crear una implementación de esta interfaz.
 * extends BasicRepository<Project, Long>: Nos da métodos CRUD gratis 
 * (save, findById, deleteById, findAll, etc.).
 */
@Repository
public interface ProjectRepository extends BasicRepository<Project, Long> {

    /**
     * ¡Método de Consulta Personalizado!
     * * Jakarta Data analiza el nombre del método: "findByStatus".
     * Sabe que debe buscar proyectos donde el campo 'status' coincida con el parámetro.
     * No hace falta escribir JPQL ni SQL.
     */
    List<Project> findByStatus(String status);

    // Ejemplo adicional: Buscar por nombre
    // Optional<Project> findByName(String name);
}
```

**Nota Importante:** Fíjate que extendemos `BasicRepository`. Esta es la interfaz estándar de Jakarta Data que incluye los métodos más comunes (`save`, `findById`, `findAll`, etc.).

## 2\. Paso 2: Limpiar el Servicio (Refactorización Masiva)

Ahora viene la parte satisfactoria. Vamos a borrar mucho código del `ProjectService` y reemplazar el `EntityManager` por nuestro nuevo `ProjectRepository`.

Modifica [`src/main/java/.../service/ProjectService.java`](src/main/java/com/mycompany/projecttracker/service/ProjectService.java):

```java
package com.mycompany.projecttracker.service;

import com.mycompany.projecttracker.entity.AuditInfo;
import com.mycompany.projecttracker.entity.Project;
import com.mycompany.projecttracker.mapper.ProjectMapper;
import com.mycompany.projecttracker.model.ProjectDTO;
import com.mycompany.projecttracker.repository.ProjectRepository; // <-- Importar Repositorio
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class ProjectService {

    // 1. ¡ADIÓS EntityManager!
    // @PersistenceContext(unitName = "project-tracker-pu")
    // private EntityManager em;  <-- BORRADO

    // 2. ¡HOLA ProjectRepository!
    @Inject
    private ProjectRepository repository;

    @Inject
    private ProjectMapper mapper;

    public List<ProjectDTO> findAll() {
        // ANTES: 
        // List<Project> entities = em.createQuery("SELECT p FROM ...").getResultList();
        
        // AHORA:
        // repository.findAll() devuelve un Stream en Jakarta Data
        return repository.findAll() 
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<ProjectDTO> findById(Long id) {
        // ANTES:
        // Project entity = em.find(Project.class, id);
        // return Optional.ofNullable(entity)...

        // AHORA:
        // repository.findById(id) ya devuelve un Optional<Project> nativamente
        return repository.findById(id)
                       .map(mapper::toDTO);
    }
    
    // Nuevo método para probar nuestra consulta personalizada
    public List<ProjectDTO> findByStatus(String status) {
        return repository.findByStatus(status).stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    public ProjectDTO create(ProjectDTO projectRequest) {
        Project newEntity = mapper.toEntity(projectRequest);
        
        // Lógica de negocio
        newEntity.setStatus("Nuevo");
        newEntity.setAuditInfo(new AuditInfo("admin_user", LocalDate.now()));

        // ANTES: em.persist(newEntity);
        
        // AHORA: El repositorio devuelve la entidad guardada (con el ID generado)
        newEntity = repository.save(newEntity);

        return mapper.toDTO(newEntity);
    }
}
```

## 3\. Paso 3: Exponer el nuevo filtro en la API (Opcional)

Ya que creamos `findByStatus` en el repositorio y el servicio, ¿por qué no exponerlo en nuestra API REST?

Modifica [`ProjectResource.java`](src/main/java/com/mycompany/projecttracker/rest/ProjectResource.java) para aceptar un parámetro de consulta:

```java
    /**
     * Método para OBTENER proyectos, opcionalmente filtrando por estado.
     * Responde a: GET /resources/projects?status=Activo
     */
    @GET
    public Response getProjects(@QueryParam("status") String status) {
        List<ProjectDTO> projects;

        if (status != null && !status.isBlank()) {
            // Usamos nuestro nuevo método de Jakarta Data
            projects = projectService.findByStatus(status);
        } else {
            projects = projectService.findAll();
        }
        
        return Response.ok(projects).build();
    }
```

-----

## 4\. ¿Qué acaba de pasar? (Explicación Técnica)

Quizás te preguntes: **"¿Cómo funciona `findByStatus` si nunca escribí la consulta SQL?"**

1.  **Análisis en Tiempo de Compilación/Despliegue:** Cuando Payara arranca (o al compilar, dependiendo de la implementación), la especificación Jakarta Data inspecciona tu interfaz `ProjectRepository`.
2.  **Patrón de Nombre:** Ve el método `findByStatus`.
3.  **Mapeo:** Busca en la entidad `Project` un campo llamado `status`.
4.  **Generación de Query:** Automáticamente genera el equivalente a `SELECT p FROM Project p WHERE p.status = :status`.
5.  **Inyección:** Crea una clase dinámica que implementa tu interfaz e inyecta esa instancia en tu `ProjectService`.

Esto reduce drásticamente los errores de sintaxis SQL/JPQL y hace el código mucho más limpio.

-----

## 5\. Probar la Magia

1.  **Construye y Despliega:**

    ```sh
    mvn clean package
    ```

    Copia el `.war` a `autodeploy`.

2.  **Prueba Básica:**
    Verifica que `GET /resources/projects` y `POST /resources/projects` siguen funcionando. ¡Si funcionan, significa que Jakarta Data ha reemplazado a JPA correctamente\!

3.  **Prueba el Filtro (Nuevo):**
    El `import.sql` de la sesión anterior creó un proyecto con estado "Activo" y otro "Planificado".

    * **Buscar activos:**

      ```sh
      curl "http://localhost:8080/project-tracker/resources/projects?status=Activo"
      ```

      *Debería devolver solo el "Sitio Web Corporativo".*\
      \
      ![](https://i.imgur.com/vejGae4.png) 

    * **Buscar planificados:**

      ```sh
      curl "http://localhost:8080/project-tracker/resources/projects?status=Planificado"
      ```

      *Debería devolver solo la "App Móvil".*\
      \
      ![](https://i.imgur.com/D4OML7K.png)  

-----

¡Enhorabuena\! Has modernizado tu aplicación para usar **Jakarta Data 1.0**. Has eliminado el código "boilerplate" de acceso a datos y has hecho que tu capa de servicio sea mucho más legible y fácil de mantener.

En la próxima sesión, dejaremos el backend por un momento y construiremos una interfaz de usuario para todo esto usando **Jakarta Faces (JSF)**.