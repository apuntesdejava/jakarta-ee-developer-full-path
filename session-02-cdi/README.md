# Sesión 2: El Corazón de la Aplicación (CDI)

En la Sesión 1, todo nuestro código (endpoints REST y lógica de negocio) vivía en una sola clase: `ProjectResource`. Esto funciona para "Hola Mundo", pero es una mala práctica. La capa API no debería saber *cómo* se guardan o procesan los datos.

En esta sesión, vamos a **separar las responsabilidades**. Crearemos una capa de "Servicio" que contendrá la lógica de negocio (nuestra base de datos simulada) y usaremos la especificación más importante de Jakarta EE para conectarlo todo.

**Especificaciones de Jakarta EE 11 a cubrir:**

* **Jakarta Contexts and Dependency Injection 4.1 (CDI):** El "pegamento" de la plataforma. Es el estándar para la **Inyección de Dependencias** y la gestión del ciclo de vida de los componentes (conocidos como "Beans").

-----

## 1\. ¿Qué es CDI y por qué lo necesitamos?

Piensa en CDI como el sistema nervioso de tu aplicación. En lugar de que un objeto "A" cree manualmente una instancia de un objeto "B" (`B b = new B();`), el objeto "A" simplemente *pide* una instancia de "B" (`@Inject B b;`).

CDI se encarga de encontrar o crear esa instancia "B" y "conectarla" (inyectarla) en "A".

**Ventajas:**

* **Desacoplamiento:** `ProjectResource` no necesita saber *nada* sobre cómo funciona `ProjectService`, solo que existe.
* **Facilidad de Pruebas:** Podemos "inyectar" un servicio falso (`MockProjectService`) durante las pruebas.
* **Mantenibilidad:** El código es más limpio y sigue el Principio de Responsabilidad Única.

## 2\. Paso 1: Crear el Servicio de Negocio

Primero, creemos la clase que manejará la lógica.

Crea un nuevo paquete: `com.mycompany.projecttracker.service`.
Dentro, crea la clase [`ProjectService.java`](src/main/java/com/mycompany/projecttracker/service/ProjectService.java):

```java
package com.mycompany.projecttracker.service;

import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de lógica de negocio para gestionar Proyectos.
 * Especificación: CDI 4.1.
 */
@ApplicationScoped // ¡La anotación clave de CDI!
public class ProjectService {

    // --- ¡Nuestra base de datos simulada se ha mudado aquí! ---
    private final Map<Long, ProjectDTO> projectDatabase = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    // Constructor para poblar datos de prueba
    public ProjectService() {
        System.out.println("ProjectService inicializado... (Singleton)");
        long id1 = idCounter.incrementAndGet();
        projectDatabase.put(id1,
            new ProjectDTO(id1, "Sitio Web Corporativo", "Desarrollo del nuevo sitio web v2", "Activo"));
        long id2 = idCounter.incrementAndGet();
        projectDatabase.put(id2,
            new ProjectDTO(id2, "App Móvil (ProjectTracker)", "Lanzamiento de la app nativa", "Planificado"));
    }

    // --- Métodos de Lógica de Negocio ---

    public List<ProjectDTO> findAll() {
        return new ArrayList<>(projectDatabase.values());
    }

    public Optional<ProjectDTO> findById(Long id) {
        // Optional es una mejor práctica que devolver null
        return Optional.ofNullable(projectDatabase.get(id));
    }

    public ProjectDTO create(ProjectDTO projectRequest) {
        // Genera un nuevo ID y crea el DTO
        long newId = idCounter.incrementAndGet();
        ProjectDTO newProject = new ProjectDTO(
            newId,
            projectRequest.name(),
            projectRequest.description(),
            "Nuevo" // Estado por defecto
        );

        projectDatabase.put(newId, newProject);
        return newProject;
    }
}
```

**Análisis del código:**

* `@ApplicationScoped`: Esta es la magia. Le dice a CDI: "Quiero que crees **una sola instancia** de esta clase (`ProjectService`) y la mantengas viva mientras la aplicación esté funcionando (un Singleton)".
* Movimos toda la lógica del `Map`, el `AtomicLong` y el bloque `static` (ahora en el constructor) desde `ProjectResource` a esta clase.

## 3\. Paso 2: Refactorizar el Recurso REST

Ahora, limpiemos nuestra clase `ProjectResource` para que *use* el nuevo servicio.

Modifica tu archivo [`ProjectResource.java`](src/main/java/com/mycompany/projecttracker/rest/ProjectResource.java):

```java
package com.mycompany.projecttracker.rest;

import com.mycompany.projecttracker.model.ProjectDTO;
import com.mycompany.projecttracker.service.ProjectService;
import jakarta.inject.Inject;
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
import java.util.List;

/**
 * Endpoint REST para gestionar Proyectos.
 * ¡Ahora delegando la lógica a un servicio CDI!
 */
@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    // --- ¡Toda la lógica de la "base de datos" ha desaparecido! ---

    @Inject // 3. ¡La magia de CDI!
    private ProjectService projectService; // CDI inyectará el singleton aquí

    @Context
    private UriInfo uriInfo;

    /**
     * Método para OBTENER todos los proyectos.
     * Responde a: GET /resources/projects
     */
    @GET
    public Response getAllProjects() {
        // La capa REST solo coordina. La lógica está en el servicio.
        List<ProjectDTO> projects = projectService.findAll();
        return Response.ok(projects).build();
    }

    /**
     * Método para OBTENER un proyecto por su ID.
     * Responde a: GET /resources/projects/{id}
     */
    @GET
    @Path("/{id}")
    public Response getProjectById(@PathParam("id") Long id) {
        // Usamos Optional para un manejo de "no encontrado" más limpio
        return projectService.findById(id)
            .map(project -> Response.ok(project).build()) // 200 OK si se encuentra
            .orElse(Response.status(Response.Status.NOT_FOUND).build()); // 404 si no
    }

    /**
     * Método para CREAR un nuevo proyecto.
     * Responde a: POST /resources/projects
     */
    @POST
    public Response createProject(ProjectDTO projectRequest) {
        // Validación (aún simple, la mejoraremos en la Sesión 4)
        if (projectRequest == null || projectRequest.name() == null || projectRequest.name().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("El nombre del proyecto es obligatorio.")
                .build();
        }

        ProjectDTO newProject = projectService.create(projectRequest);

        // Construir la URL del nuevo recurso
        URI location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(newProject.id())).build();
        return Response.created(location).entity(newProject).build();
    }
}
```

**Análisis del código:**

* `@Inject private ProjectService projectService;`: Esta línea es todo. Le pedimos a CDI que nos proporcione la instancia `@ApplicationScoped` de `ProjectService`.
* ¡Mira qué limpios quedaron los métodos\! `getAllProjects` es ahora una sola línea de lógica.
* El `ProjectResource` ahora solo se preocupa de cosas de **HTTP**: recibir peticiones, validar (un poco), llamar al servicio y formatear respuestas (200, 404, 201).
* El `ProjectService` solo se preocupa de la **lógica de negocio**: gestionar la lista de proyectos.

## 4\. Probar la Nueva Arquitectura

Esto es lo mejor. Aunque hemos cambiado drásticamente la estructura interna del código, la API externa sigue siendo idéntica.

1.  **Construye y Desplega:**

    ```sh
    mvn clean package
    ```

    Copia el `target/project-tracker.war` a la carpeta `autodeploy` de Payara 7.

2.  **Prueba (Re-usa las pruebas de la Sesión 1):**
    Usa `curl` o Postman exactamente como antes.

    * `GET /resources/projects`: Seguirá devolviendo los dos proyectos de prueba.
    * `POST /resources/projects`: Seguirá creando el proyecto con `id: 3`.
    * `GET /resources/projects/3`: Ahora encontrará el proyecto que acabas de crear.

Si miras la consola de Payara (o el `server.log`), deberías ver nuestro mensaje: `ProjectService inicializado... (Singleton)`. Y solo aparecerá una vez, demostrando que es un Singleton.

![](https://i.imgur.com/1oU9HcQ.png)


-----

## 💡 Resolviendo ambigüedad con Qualifiers

En nuestra sesión, inyectamos `ProjectService`:

```java
@Inject
private ProjectService projectService;
```

CDI supo qué inyectar porque solo teníamos **una clase** (`ProjectService`) anotada con `@ApplicationScoped`.

**¿Pero qué pasa si tuviéramos dos?**

Imagina que queremos tener un "servicio de saludo" en nuestra app, y tenemos dos implementaciones:

1.  Una interfaz: [`GreetingService.java`](src/main/java/com/mycompany/projecttracker/service/GreetingService.java)
2.  Una implementación "normal": [`DefaultGreeting.java`](src/main/java/com/mycompany/projecttracker/service/impl/DefaultGreeting.java)
3.  Una implementación "para pruebas" o "especial": [`MockGreeting.java`](src/main/java/com/mycompany/projecttracker/service/impl/MockGreeting.java)

**El Problema: Dependencia Ambigua**

Si ambas clases `DefaultGreeting` y `MockGreeting` implementan `GreetingService` y ambas están anotadas con `@ApplicationScoped`, cuando intentes hacer esto:

```java
@Inject
private GreetingService greetingService; // <-- ¡ERROR!
```

CDI te dará un error de `WELD-001409: Ambiguous dependencies for type GreetingService with qualifiers @Default` (Excepción de Dependencia Ambigua) durante el despliegue. CDI dirá: "¡Me diste dos opciones\! No sé cuál debo inyectar."

**La Solución: `@Qualifier`**
 

### 1\. Crear nuestras "Etiquetas" (Qualifiers)

Primero, creamos una anotación que actuará como nuestra etiqueta.

Crea un nuevo paquete `com.mycompany.projecttracker.service.qualifier`.
Dentro, crea la anotación [`GreetingType.java`](src/main/java/com/mycompany/projecttracker/service/qualifier/GreetingType.java):

```java
package com.mycompany.projecttracker.service.qualifier;

import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Nuestro Qualifier personalizado para seleccionar un tipo de saludo.
 */
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER, TYPE})
public @interface GreetingType {
    /**
     * Definimos un miembro para poder especificar qué tipo queremos.
     * (Podríamos usar un Enum, pero String es simple)
     */
    String value() default "default";
}
```

* `@Qualifier`: Esto le dice a CDI que esta anotación es una "etiqueta" de calificación.

### 2\. Etiquetar nuestras Implementaciones

Ahora, usamos nuestra nueva etiqueta en las clases de servicio:

[`DefaultGreeting.java`](src/main/java/com/mycompany/projecttracker/service/impl/DefaultGreeting.java)

```java
package com.mycompany.projecttracker.service.impl;

import com.mycompany.projecttracker.service.qualifier.GreetingType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@GreetingType("default") // <-- Etiqueta 1
public class DefaultGreeting implements GreetingService {
    public String greet(String name) {
        return "¡Hola, " + name + "!";
    }
}
```

[`MockGreeting.java`](src/main/java/com/mycompany/projecttracker/service/impl/MockGreeting.java)

```java
package com.mycompany.projecttracker.service.impl;

import com.mycompany.projecttracker.service.qualifier.GreetingType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@GreetingType("mock") // <-- Etiqueta 2
public class MockGreeting implements GreetingService {
    public String greet(String name) {
        return "Modo de Prueba: Hola, " + name;
    }
}
```

### 3\. Inyectar Específicamente

Ahora, cuando inyectamos, ya no somos ambiguos. Le decimos a CDI *exactamente* cuál queremos. Lo usaremos en el ejemplo de [`HelloWorlResource`](C:\proys\jakartaee-tutorial\jakarta-ee-developer-full-path\session-02-cdi\src\main\java\com\mycompany\resource\HelloWorldResource.java)

```java
package com.mycompany.resource;

import com.mycompany.projecttracker.service.GreetingService;
import com.mycompany.projecttracker.service.qualifier.GreetingType;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("hello")
public class HelloWorldResource {

    @Inject
    @GreetingType("default")
    GreetingService greetingService;

    @Inject
    @GreetingType("mock")
    GreetingService greetingMockService;

    @GET
    public Response hello(@QueryParam("name") @DefaultValue("world") String name) {
        return Response
            .ok(greetingService.greet(name))
            .build();
    }

    @GET
    @Path("mock")
    public Response helloMock(@QueryParam("name") @DefaultValue("world") String name) {
        return Response
            .ok(greetingMockService.greet(name))
            .build();
    }


} 
```

### Probando

Como hemos creado un endpoint para cada implementación, probemos:

**Llamando a la implementación `default`**

![](https://i.imgur.com/cEqPNZd.png)

**Llamando a la implementación `mock`**

![](https://i.imgur.com/gmroGzg.png)

-----

¡Felicidades\! Has refactorizado la aplicación a una arquitectura limpia y profesional usando **CDI 4.1**. Tu código ahora está desacoplado, es más fácil de mantener y está listo para el siguiente paso.

En la **Sesión 3**, reemplazaremos nuestra "base de datos" simulada (`Map`) por una base de datos real usando **Jakarta Persistence (JPA)**.