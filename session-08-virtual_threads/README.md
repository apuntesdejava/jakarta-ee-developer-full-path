# Sesión 8: Concurrencia Moderna (Virtual Threads)

En una aplicación web tradicional, si tienes una tarea pesada (como generar un reporte PDF complejo o enviar 1000 correos), no quieres bloquear el hilo principal que atiende la petición HTTP. Si lo haces, el usuario verá el navegador cargando infinitamente y podrías colapsar el servidor.

La solución es procesarlo en **segundo plano (asíncronamente)**.

En esta sesión, crearemos un sistema de generación de reportes que:

1.  Recibe la petición del usuario.
2.  Responde inmediatamente "Recibido, trabajando en ello" (HTTP 202).
3.  Ejecuta la tarea pesada en un **Hilo Virtual (Virtual Thread)** gestionado por Payara.

**Especificaciones de Jakarta EE 11 a cubrir:**

* **[Jakarta Concurrency 3.1](https://jakarta.ee/specifications/concurrency/3.1/):** Específicamente la definición de ejecutores (`ManagedExecutorService`) y el soporte para hilos virtuales.

-----

## 1\. Paso 1: Definir el Ejecutor (Virtual)

Antes, para tener un "Pool de Hilos", tenías que entrar a la consola de administración de Payara y configurarlo manualmente.

En Jakarta EE 11, podemos definir la infraestructura **desde el código**. Y lo mejor: podemos decirle que use **Virtual Threads** con una simple propiedad.

Crea un nuevo paquete `com.mycompany.projecttracker.config` (o úsalo si ya existe).
Crea la clase [`ConcurrencyConfig.java`](src/main/java/com/mycompany/projecttracker/config/ConcurrencyConfig.java):

```java
package com.mycompany.projecttracker.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.concurrent.ManagedExecutorDefinition;
import jakarta.enterprise.concurrent.ContextServiceDefinition;

/**
 * Definición de recursos de Concurrencia.
 * Jakarta EE 11 permite definir esto por anotaciones.
 */
@ApplicationScoped
@ManagedExecutorDefinition(
    name = "java:app/concurrent/VirtualExecutor", // El nombre JNDI para inyectarlo luego
    virtual = true, // <--- ¡LA NOVEDAD DE EE 11! Habilita Virtual Threads
    maxAsync = 10,  // Cuántas tareas pueden correr simultáneamente (opcional)
    context = "java:app/concurrent/MyContext" // Propagación de contexto (seguridad, etc.)
)
@ContextServiceDefinition(
    name = "java:app/concurrent/MyContext",
    propagated = {ContextServiceDefinition.SECURITY, ContextServiceDefinition.APPLICATION}
)
public class ConcurrencyConfig {
    // Clase vacía, solo sirve para portar las anotaciones.
}
```

**Análisis:**

* `virtual = true`: Esta es la joya de Jakarta EE 11. Le dice al servidor: "No uses un pool de hilos de sistema operativo limitados. Crea un nuevo hilo virtual ligero para cada tarea que envíe aquí".
* `propagated = SECURITY`: Esto es vital. Significa que si el usuario "admin" inicia la tarea, el hilo virtual sabrá que es "admin" (útil si llamamos a métodos `@RolesAllowed` dentro del hilo).

-----

## 2\. Paso 2: Crear el Servicio de Reportes

Este servicio simulará una tarea pesada.

Crea el paquete `com.mycompany.projecttracker.service`.
Crea la clase [`ReportService.java`](src/main/java/com/mycompany/projecttracker/service/ReportService.java):

```java
package com.mycompany.projecttracker.service;

import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ApplicationScoped
public class ReportService {

    private static final Logger logger = Logger.getLogger(ReportService.class.getName());

    // Inyectamos el ejecutor que definimos en el Paso 1
    @Resource(lookup = "java:app/concurrent/VirtualExecutor")
    private ManagedExecutorService executor;

    /**
     * Inicia la generación del reporte de forma asíncrona.
     * Retorna un CompletableFuture para que quien llame pueda saber cuándo terminó (si quisiera).
     */
    public CompletableFuture<Void> generateReportAsync(Long projectId, String userInitiator) {
        
        // CompletableFuture.runAsync envía la tarea al ejecutor
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. Simular trabajo pesado (5 segundos)
                logger.info("--> Iniciando reporte para Proyecto ID: " + projectId + " solicitado por: " + userInitiator);
                
                // Imprimimos el nombre del hilo para verificar que es VIRTUAL
                logger.info("--> Corriendo en Hilo: " + Thread.currentThread());
                
                Thread.sleep(5000); 

                // 2. Finalizar
                logger.info("--> Reporte finalizado para Proyecto ID: " + projectId);
                
                // (Aquí podrías guardar un registro en BBDD, enviar un email, etc.)
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.severe("--> Reporte interrumpido");
            }
        }, executor); // <--- ¡Importante pasarle nuestro executor virtual!
    }
}
```

-----

## 3\. Paso 3: Endpoint REST Asíncrono

Vamos a exponer esta funcionalidad. Este endpoint **no** devolverá el reporte. Devolverá un código **202 Accepted**, que significa: "Entendido, lo haré, pero no esperes aquí sentado".

Crea [`com.mycompany.projecttracker.rest.ReportResource.java`](src/main/java/com/mycompany/projecttracker/rest/ReportResource.java):

```java
package com.mycompany.projecttracker.rest;

import com.mycompany.projecttracker.service.ReportService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/reports")
public class ReportResource {

    @Inject
    private ReportService reportService;

    @POST
    @Path("/{projectId}")
    @RolesAllowed({"ADMIN", "USER"}) // Ambos roles pueden pedir reportes
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestReport(@PathParam("projectId") Long projectId, @Context SecurityContext securityContext) {

        String username = securityContext.getUserPrincipal().getName();

        // Llamamos al servicio asíncrono.
        // NO esperamos a que termine (no usamos .get() ni .join()).
        reportService.generateReportAsync(projectId, username);

        // Respondemos inmediatamente al usuario, en formato JSON
        var response = Json.createObjectBuilder()
            .add("status","Reporte solicitado. Procesando en segundo plano.")
            .build();
        return Response.accepted()
            .entity(response)
            .build();
    }
}
```

-----

## 4\. Probar y Verificar (La prueba de fuego)

Para que esta sesión sea un éxito, debemos demostrar que **realmente** estamos usando Virtual Threads.

1.  **Construye y Despliega:**
    `mvn clean package` y copiar a `autodeploy`.

2.  **Solicita el Reporte (vía cURL):**
    Necesitas el Token JWT:\
    **Linux:**
    ```sh
    # 1. Login
    # (Obtén el token como en la sesión 7)
    TOKEN="eyJhbGci..."

    # 2. Pedir reporte
    curl -v -X POST http://localhost:8080/project-tracker/resources/reports/1 \
         -H "Authorization: Bearer $TOKEN"
    ```
    \
    **PowerShell**
    ```powershell
    $headers=@{}
    $headers.Add("Content-Type", "application/json")
    $response = Invoke-RestMethod -Uri 'http://localhost:8080/project-tracker/resources/auth/login' -Method POST -Headers $headers -ContentType 'application/json' -Body '{
        "username": "admin",
        "password": "admin123"
    }'

    $headers=@{}
    $headers.Add("Authorization", "Bearer $($response.token)")
    $response = Invoke-RestMethod -Uri 'http://localhost:8080/project-tracker/resources/reports/1' -Method POST -Headers $headers
    $response | ConvertTo-Json
    ```


3.  **Observa la Respuesta Inmediata:**
    El comando `curl` debería responder **instantáneamente** (en milisegundos) con `HTTP/1.1 202 Accepted`. No debería quedarse esperando los 5 segundos.\
    \
    ![](https://i.imgur.com/lOmmvVr.png)

4.  **Observa los Logs de Payara:**
    Ve a la consola donde corre Payara o abre el archivo `server.log`. Deberías ver algo similar a esto 5 segundos después:

    ```text
    INFO: --> Iniciando reporte para Proyecto ID: 1 solicitado por: admin
    INFO: --> Corriendo en Hilo: VirtualThread[#234]/runnable@ForkJoinPool-1-worker-1
    ... (5 segundos después) ...
    INFO: --> Reporte finalizado para Proyecto ID: 1
    ```
    ![](https://i.imgur.com/fjg4vZY.png)

    **¡Fíjate en el nombre del hilo\!**

    * Si fuera un hilo normal, diría algo como: `Thread[concurrent/__defaultManagedExecutorService-managed-thread...]`
    * Como es un hilo virtual, dice: **`VirtualThread[#...]`**.

-----

### ¿Por qué esto es importante?

Si usaras hilos normales y llegaran 10,000 peticiones de reportes simultáneas, tu servidor colapsaría o rechazaría conexiones (OutOfMemoryError o ThreadStarvation).

Con **Virtual Threads**, el servidor simplemente creará 10,000 objetos Java ligeros. La JVM los gestionará eficientemente montándolos y desmontándolos de los hilos del procesador solo cuando realmente necesiten CPU, permitiendo una escalabilidad masiva con el mismo hardware.

¡Felicidades\! Has implementado concurrencia de última generación en tu aplicación empresarial.