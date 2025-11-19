# Sesión 1: La Capa API REST (Endpoints)

¡Seguimos\! En la Sesión 0, preparamos el cohete. En esta sesión, ¡lo vamos a encender\!

Vamos a construir la **Interfaz de Programación de Aplicaciones (API)**. Esta es la puerta de entrada HTTP que permitirá a futuras aplicaciones (web, móviles, etc.) interactuar con nuestro `ProjectTracker`.

**Especificaciones de Jakarta EE 11 a cubrir:**

* **Jakarta RESTful Web Services 4.0 (JAX-RS):** El estándar para crear APIs REST. Usaremos sus anotaciones (`@Path`, `@GET`, `@POST`, etc.) para definir nuestros endpoints.
* **Jakarta JSON Binding 3.1 (JSON-B):** El estándar para convertir (serializar) objetos Java a texto JSON y viceversa (deserializar). Lo mejor es que... ¡casi no la veremos\! Funciona automáticamente.

-----

## 1\. El Modelo de Datos (DTO)

Antes de crear endpoints, necesitamos un "contrato" de datos. ¿Qué forma tendrá un "Proyecto" cuando viaje por la red?

Usaremos un **DTO (Data Transfer Object)**. Y para esto, un `record` de Java (disponible desde Java 16) es perfecto por su simplicidad e inmutabilidad.

Crea un nuevo paquete `com.mycompany.projecttracker.model`.
Dentro, crea un nuevo archivo [`ProjectDTO.java`](src/main/java/com/mycompany/projecttracker/model/ProjectDTO.java):

```java
package com.mycompany.projecttracker.model;

/**
 * DTO (Data Transfer Object) para un Proyecto.
 * Usamos un 'record' de Java para una definición concisa e inmutable.
 * JSON-B 3.1 sabe cómo manejar 'records' automáticamente.
 */
public record ProjectDTO(
    Long id,
    String name,
    String description,
    String status
) {
    // ¡Eso es todo! Un record nos da constructores, getters,
    // equals(), hashCode() y toString() gratis.
}
```

## 2\. El Recurso REST (Endpoint)

Ahora, creemos la clase que manejará las peticiones HTTP. Esta es la clase "Recurso" (Resource) en la jerga de JAX-RS.

Vamos a reemplazar nuestro `HelloResource` de la sesión 0. Si quieres, puedes borrarlo. O mejor, modifiquémoslo para que sea nuestro `ProjectResource`.

Renombra (o crea) el archivo [`ProjectResource.java`](src/main/java/com/mycompany/projecttracker/rest/ProjectResource.java) en el paquete `com.mycompany.projecttracker.rest`:

```java
package com.mycompany.projecttracker.rest;

import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Endpoint REST para gestionar Proyectos.
 * Especificaciones: JAX-RS 4.0 y JSON-B 3.1.
 */
@Path("/projects")
@Produces(MediaType.APPLICATION_JSON) // Por defecto, todos los métodos aquí devuelven JSON
@Consumes(MediaType.APPLICATION_JSON) // Por defecto, todos los métodos aquí esperan recibir JSON
public class ProjectResource {

    // --- Simulación de Base de Datos ---
    // (Esto desaparecerá en futuras sesiones cuando usemos CDI y JPA)
    private static final Map<Long, ProjectDTO> projectDatabase = new ConcurrentHashMap<>();
    private static final AtomicLong idCounter = new AtomicLong(0);

    // Bloque estático para poblar con datos de prueba
    static {
        long id1 = idCounter.incrementAndGet();
        projectDatabase.put(id1,
            new ProjectDTO(id1, "Sitio Web Corporativo", "Desarrollo del nuevo sitio web v2", "Activo"));
        long id2 = idCounter.incrementAndGet();
        projectDatabase.put(id2,
            new ProjectDTO(id2, "App Móvil (ProjectTracker)", "Lanzamiento de la app nativa", "Planificado"));
    }
    // --- Fin de la simulación ---


    /**
     * Inyecta información sobre la URI de la petición actual.
     * Nota EE 11: Usamos @Inject (CDI) en lugar del antiguo @Context (JAX-RS).
     * Esto muestra la profunda integración de CDI en toda la plataforma.
     */
    @Context
    private UriInfo uriInfo;

    /**
     * Método para OBTENER todos los proyectos.
     * Responde a: GET /resources/projects
     */
    @GET
    public Response getAllProjects() {
        List<ProjectDTO> projects = new ArrayList<>(projectDatabase.values());
        // JAX-RS y JSON-B se encargan de convertir la Lista a un array JSON.
        return Response.ok(projects).build();
    }

    /**
     * Método para OBTENER un proyecto por su ID.
     * Responde a: GET /resources/projects/{id}
     */
    @GET
    @Path("/{id}")
    public Response getProjectById(@PathParam("id") Long id) {
        ProjectDTO project = projectDatabase.get(id);

        if (project == null) {
            // Devuelve un error 404 Not Found si no existe
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(project).build();
    }

    /**
     * Método para CREAR un nuevo proyecto.
     * Responde a: POST /resources/projects
     */
    @POST
    public Response createProject(ProjectDTO projectRequest) {
        // Valida la entrada (simple por ahora)
        if (projectRequest == null || projectRequest.name() == null || projectRequest.name().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("El nombre del proyecto es obligatorio.")
                .build();
        }

        // Genera un nuevo ID y crea el DTO
        long newId = idCounter.incrementAndGet();
        ProjectDTO newProject = new ProjectDTO(
            newId,
            projectRequest.name(),
            projectRequest.description(),
            "Nuevo" // Estado por defecto
        );

        projectDatabase.put(newId, newProject);

        // Buena práctica REST: Devolver 201 Created con la URL del nuevo recurso
        URI location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(newId)).build();
        return Response.created(location).entity(newProject).build();
    }
}
```

## 3\. ¿Magia? No, ¡JSON-B 3.1\!

Quizás te preguntes: "¿Cómo sabe `Response.ok(projects)` que debe convertir esa `List<ProjectDTO>` en un JSON?"

**Respuesta: Jakarta JSON Binding (JSON-B).**

1.  JAX-RS (nuestro `@Produces(MediaType.APPLICATION_JSON)`) dice: "Necesito convertir este objeto Java en JSON".
2.  Busca un proveedor de JSON. ¡Encuentra a JSON-B\!
3.  JSON-B 3.1 (incluido en Jakarta EE 11) se encarga del trabajo sucio.
4.  **Novedad clave:** JSON-B 3.1 tiene soporte nativo para **Java Records**. Por eso no tuvimos que añadir *ninguna* anotación (como `@JsonProperty`) a nuestro `ProjectDTO`. Simplemente funciona.

## 4\. Probar la API

¡Es hora de probar nuestro trabajo\!

1.  **Construye y Despliega:**

    ```sh
    mvn clean package
    ```

    Copia el `target/project-tracker.war` a la carpeta `autodeploy` de Payara 7 (como en la sesión 0).

2.  **Prueba con cURL o Postman:**

    * **Obtener Todos (GET):**

      ```sh
      curl http://localhost:8080/project-tracker/resources/projects
      ```

      *Verás el JSON con los dos proyectos que pusimos de prueba.*

    * **Obtener Uno (GET):**

      ```sh
      curl http://localhost:8080/project-tracker/resources/projects/1
      ```

      *Verás el JSON solo del "Sitio Web Corporativo".*

    * **Crear Uno (POST):**

      ```sh
      curl -X POST http://localhost:8080/project-tracker/resources/projects \
           -H "Content-Type: application/json" \
           -d '{"name":"Proyecto con cURL", "description":"Esto fue creado desde la terminal"}'
      ```

      *Verás una respuesta `201 Created` y el JSON del nuevo proyecto (¡con `id: 3`\!).*
      
      Ejemplo en consola usando httpie
      ![](https://i.imgur.com/Ch6t6As.png)
    * **Verifica la creación:**
      Vuelve a ejecutar el `GET` de todos los proyectos y verás que tu nuevo proyecto ahora está en la lista.

      ![](https://i.imgur.com/GqlEuyH.png)
-----

¡Felicidades\! Acabas de crear una API REST funcional usando JAX-RS 4.0, con serialización JSON automática gracias a JSON-B 3.1.

En la próxima sesión, **introduciremos CDI (Contexts and Dependency Injection)** para desacoplar nuestra lógica y eliminar esa "base de datos" estática de nuestra clase `ProjectResource`. Haremos que el código sea limpio y profesional.