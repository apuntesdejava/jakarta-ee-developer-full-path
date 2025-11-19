# Sesión 4: Validación de Datos con Jakarta Validation

En este momento, si un cliente envía un JSON vacío (`{}`) a nuestro endpoint de creación, nuestro `ProjectService` probablemente fallará con un `NullPointerException` o, peor aún, insertará una fila inútil en la base de datos.

Vamos a solucionar esto implementando validaciones robustas.

**Especificaciones de Jakarta EE 11 a cubrir:**

  * **Jakarta Validation 3.1:** El estándar para la validación de objetos (Beans) usando anotaciones.

**¿Necesito una dependencia?**
¡No\! La especificación `jakarta.validation` ya está incluida en el **Perfil Web de Jakarta EE 11** que seleccionamos en `start.payara.fish`. Y Payara 7 (como servidor certificado) ya incluye un proveedor (como Hibernate Validator) listo para usar.

-----

## 1\. Paso 1: Anotar el DTO (Validar la Entrada)

Nuestro primer punto de defensa es la API. Queremos rechazar peticiones malas *antes* de que lleguen a nuestra lógica de servicio. El mejor lugar para esto es nuestro **DTO (Data Transfer Object)**.

Esto también cumple con el objetivo de **validar un Record de Java**, ya que nuestro `ProjectDTO` es un `record`. ¡Jakarta Validation se integra perfectamente con ellos\!

Modifica [`src/main/java/.../model/ProjectDTO.java`](src/main/java/com/mycompany/projecttracker/model/ProjectDTO.java):

```java
package com.mycompany.projecttracker.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para un Proyecto.
 * ¡Ahora con reglas de validación de Jakarta Validation 3.1!
 *
 * Estas anotaciones se aplican a los componentes del Record.
 */
public record ProjectDTO(
    Long id,

    @NotNull(message = "El nombre no puede ser nulo")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    String name,

    @Size(max = 5000, message = "La descripción no puede exceder los 5000 caracteres")
    String description,

    String status
) {
    // El cuerpo del record sigue vacío.
    // Las anotaciones se colocan directamente en los componentes.
}
```

## 2\. Paso 2: Anotar la Entidad (Defensa en Profundidad)

¿Qué pasa si alguien (quizás otro desarrollador) llama a `projectService.create()` internamente sin pasar por la API? Debemos proteger nuestra base de datos.

Añadiremos las mismas validaciones a la **Entidad JPA** (`@Entity`). JPA puede configurarse para ejecutar estas validaciones *antes* de enviar el `INSERT` o `UPDATE` a la base de datos.

Modifica [`src/main/java/.../entity/Project.java`](src/main/java/com/mycompany/projecttracker/entity/Project.java):

```java
package com.mycompany.projecttracker.entity;

// ... otros imports
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "PROJECT")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull // JPA también puede verificar esto
    @Size(min = 3, max = 100) // Aseguramos consistencia con el DTO
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 5000)
    @Lob
    private String description;
    
    // ... el resto de la clase (status, deadline, auditInfo, tasks, etc.)
    // No es necesario modificar los getters/setters.
}
```

-----

## 3\. Paso 3: Activar la Validación en el Endpoint REST

Ahora que nuestro DTO "sabe" cómo validarse, debemos decirle a JAX-RS (nuestra capa REST) que *ejecute* esa validación.

Esto se hace con una sola anotación: `@Valid`.

Modifica `src/main/java/.../rest/ProjectResource.java`:

```java
package com.mycompany.projecttracker.rest;

// ... otros imports

import com.mycompany.projecttracker.model.ProjectDTO;
import com.mycompany.projecttracker.service.ProjectService;
import jakarta.inject.Inject;
import jakarta.validation.Valid; // <-- ¡IMPORTANTE!
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
// ...

@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    private ProjectService projectService;

    @Context
    private UriInfo uriInfo;

    // ... métodos GET ...

    /**
     * Método para CREAR un nuevo proyecto.
     * Responde a: POST /resources/projects
     */
    @POST
    public Response createProject(@Valid ProjectDTO projectRequest) { // <-- ¡AQUÍ!

        // ¡Podemos borrar nuestra validación manual!
        // if (projectRequest == null || projectRequest.name() == null ...) {
        //     ...
        // }
        // ¡Jakarta Validation se encarga de esto ahora!

        ProjectDTO newProject = projectService.create(projectRequest);

        URI location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(newProject.id())).build();
        return Response.created(location).entity(newProject).build();
    }
}
```

Con solo añadir `@Valid`, JAX-RS interceptará la petición, revisará las anotaciones en `ProjectDTO` y, si alguna falla, lanzará una `ConstraintViolationException`.

-----

## 4\. Paso 4: Manejar los Errores de Forma Elegante

Por defecto, si se lanza una `ConstraintViolationException`, Payara devolverá un error **500 (Internal Server Error)** con una traza de stack enorme. Esto es horrible para el cliente de la API.

Un error de validación es un error del *cliente* (envió datos malos), por lo que debemos devolver un **400 (Bad Request)** con un JSON claro que explique *qué* salió mal.

Lo haremos de la forma más limpia de JAX-RS: un `ExceptionMapper`.

1.  Crea un nuevo paquete: `com.mycompany.projecttracker.rest.mapper`
2.  Dentro, crea la clase `ValidationExceptionMapper.java`:

<!-- end list -->

```java
package com.mycompany.projecttracker.rest.mapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.Map;

/**
 * Captura las excepciones de validación (@Valid) y las transforma
 * en una respuesta 400 Bad Request clara para el cliente.
 */
@Provider // Le dice a JAX-RS que esta clase es un "proveedor" que debe registrar
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {

        // Convertimos el Set de violaciones en una lista de mensajes simples
        List<String> errors = exception.getConstraintViolations().stream()
            .map(this::formatError)
            .toList();

        // Creamos un cuerpo de respuesta JSON
        // (Podríamos usar un Record/Clase si quisiéramos)
        Map<String, Object> responseBody = Map.of(
            "message", "La petición tiene errores de validación",
            "errors", errors
        );

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(responseBody)
            .build();
    }

    /**
     * Formatea un error de 'campo: mensaje'
     * Ej: "name: El nombre no puede ser nulo"
     */
    private String formatError(ConstraintViolation<?> violation) {
        // Obtenemos el nombre del campo (ej. "name")
        String field = violation.getPropertyPath().toString();
        // Obtenemos el mensaje (ej. "El nombre no puede ser nulo")
        String message = violation.getMessage();

        // 'violation.getPropertyPath()' puede ser complejo (ej. 'createProject.arg0.name')
        // Lo simplificamos para obtener solo el nombre del campo final.
        String[] parts = field.split("\\.");
        if (parts.length > 0) {
            field = parts[parts.length - 1];
        }

        return field + ": " + message;
    }
}
```

  * `@Provider`: Esta anotación es clave. JAX-RS escaneará la aplicación, encontrará esta clase y la registrará automáticamente como un manejador de excepciones.

-----

## 5\. Probar nuestra Validación

¡Es hora de ver el resultado\!

1.  **Construye y Despliega:**

    ```sh
    mvn clean package
    ```

    Copia el `target/project-tracker.war` a la carpeta `autodeploy` de Payara 7.

2.  **Prueba 1: Petición Válida (Debe funcionar como antes)**

    ```sh
    curl -X POST http://localhost:8080/project-tracker/resources/projects \
         -H "Content-Type: application/json" \
         -d '{"name":"Proyecto Validado", "description":"¡Esto funciona!"}'
    ```

    *Resultado: `201 Created`*\
    \
    ![](https://i.imgur.com/OF4YqT4.png)

3.  **Prueba 2: Petición Inválida (Nombre nulo)**

    ```sh
    curl -X POST http://localhost:8080/project-tracker/resources/projects \
         -H "Content-Type: application/json" \
         -d '{"description":"Este proyecto no tiene nombre"}'
    ```

    *Resultado: Un hermoso `400 Bad Request` con este JSON:*

    ```json
    {
      "message": "La petición tiene errores de validación",
      "errors": [
        "name: El nombre no puede ser nulo"
      ]
    }
    ```
    \
    ![](https://i.imgur.com/kIq2YZO.png)

4.  **Prueba 3: Petición Inválida (Nombre corto)**

    ```sh
    curl -X POST http://localhost:8080/project-tracker/resources/projects \
         -H "Content-Type: application/json" \
         -d '{"name":"A"}'
    ```

    *Resultado: `400 Bad Request`*

    ```json
    {
      "message": "La petición tiene errores de validación",
      "errors": [
        "name: El nombre debe tener entre 3 y 100 caracteres"
      ]
    }
    ```
    \
    ![](https://i.imgur.com/NPonArZ.png)

¡Felicidades\! Tu API ahora está protegida contra datos maliciosos o incorrectos, y devuelve mensajes de error claros y profesionales.