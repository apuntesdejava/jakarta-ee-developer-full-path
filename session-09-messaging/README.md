# Sesión 9: Mensajería Asíncrona (JMS y MDBs)

Imagina que cuando un usuario crea una tarea, el sistema debe enviar un correo electrónico de notificación. Si usamos el hilo principal para enviar el correo (conectarse al servidor SMTP, enviar, esperar confirmación), el usuario verá el navegador congelado por 2 o 3 segundos.

**Solución:** Usar **Mensajería (Messaging)**.

1.  El usuario crea la tarea.
2.  El servicio guarda la tarea y pone un mensaje rápido en una "cola" (Queue): *"Oye, notifica sobre la tarea ID 10"*.
3.  El servicio responde al usuario inmediatamente ("¡Listo\!").
4.  En segundo plano, otro componente recoge ese mensaje y envía el correo con calma.

**Especificaciones de Jakarta EE 11 a cubrir:**

* [**Jakarta Messaging 3.1 (JMS):**](https://jakarta.ee/specifications/messaging/) La API para enviar y recibir mensajes.
* **Message-Driven Beans (MDB):** Beans especiales que "duermen" hasta que llega un mensaje.

-----

## 1\. Paso 1: Configurar la Infraestructura JMS

Antiguamente, esto requería comandos complejos de consola. En Jakarta EE 11 (y Payara moderno), podemos definir la "Cola" (Queue) y la "Fábrica de Conexiones" (ConnectionFactory) directamente en el código usando anotaciones.

Antes que nada, debemos agregar la siguiente dependencia, ya que no viene incluída en "Jakarta Web Profile" que hemos declarado en la sesión 1.

```xml
<dependency>
    <groupId>jakarta.jms</groupId>
    <artifactId>jakarta.jms-api</artifactId>
    <version>3.1.0</version>
    <scope>provided</scope>
</dependency>
```
Ahora: crea (o edita) [`com.mycompany.projecttracker.config.JmsConfiguration`](src/main/java/com/mycompany/projecttracker/config/JmsConfiguration.java):

```java
package com.mycompany.projecttracker.config;
 
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jms.JMSConnectionFactoryDefinition;
import jakarta.jms.JMSDestinationDefinition;

/**
 * Definición de recursos JMS mediante anotaciones.
 * Payara leerá esto al desplegar y creará la cola automáticamente.
 */
@ApplicationScoped
@JMSConnectionFactoryDefinition(
    name = "java:app/jms/ProjectTrackerFactory", // Nombre para inyectarla
    interfaceName = "jakarta.jms.ConnectionFactory"
)
@JMSDestinationDefinition(
    name = "java:app/jms/TaskQueue", // Nombre de la cola
    interfaceName = "jakarta.jms.Queue",
    destinationName = "TaskQueuePhysical" // Nombre interno en el servidor
)
public class JmsConfiguration {
}
```

-----

## 2\. Paso 2: Crear el DTO de Tarea

Como vamos a crear tareas, necesitamos un DTO para recibir los datos (ya que `ProjectDTO` es solo para proyectos).

Crea [`com.mycompany.projecttracker.model.TaskDTO`](src/main/java/com/mycompany/projecttracker/model/TaskDTO.java):

```java
package com.mycompany.projecttracker.model;

import jakarta.validation.constraints.NotNull;

public record TaskDTO(
    Long id,
    @NotNull String title,
    String status
) {}
```

-----

## 3\. Paso 3: Enviar el Mensaje (El Productor)

Vamos a modificar `ProjectService`. Primero, añadiremos la lógica para crear una `Task` (que nos faltaba) y luego inyectaremos el contexto JMS para enviar el aviso.

Modifica [`com.mycompany.projecttracker.service.ProjectService`](src/main/java/com/mycompany/projecttracker/service/ProjectService.java):

```java
// ... imports existentes ...
import com.mycompany.projecttracker.entity.Task; // Importar
import com.mycompany.projecttracker.model.TaskDTO; // Importar
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext; // Contexto simplificado de JMS 2.0+
import jakarta.jms.Queue;      // La cola
import java.util.logging.Logger; //Logger

@ApplicationScoped
@Transactional
public class ProjectService {

    // ... repository y mapper existentes ...

    // 0. Siempre es bueno usar un Logger
    private static final Logger LOGGER = Logger.getLogger(ProjectService.class.getName());

    // 1. Inyectamos el contexto JMS (Maneja conexiones automáticamente)
    @Inject
    private JMSContext jmsContext;

    // 2. Inyectamos la cola que definimos en el Paso 1
    @Resource(lookup = "java:app/jms/TaskQueue")
    private Queue taskQueue;

    // 3. Para manejar directamente Jakarta Persistence
    @PersistenceContext(unitName = "project-tracker-pu")
    private EntityManager em;

    // ... métodos existentes (create, findAll...) ...

    /**
     * Crea una tarea asociada a un proyecto y notifica por JMS.
     */
    public TaskDTO createTask(Long projectId, TaskDTO taskDto) {
        // A. Buscar el proyecto (usando repository)
        Project project = repository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado: " + projectId));

        // B. Crear la entidad Tarea
        Task newTask = new Task();
        newTask.setTitle(taskDto.title());
        newTask.setStatus("Pendiente");

        // C. Usar el método helper del Proyecto para mantener la coherencia
        project.addTask(newTask);

        // D. Guardar la nueva Tarea directamente por el Jakarta Persistence.
        em.persist(newTask);
        em.flush(); //al hacer flush, obtenemos su ID inmediatamente

        // E. ENVIAR MENSAJE JMS (¡Aquí ocurre la magia!)
        // Enviamos un String simple: "ID_PROYECTO:ID_TAREA"
        String messagePayload = project.getId() + ":" + newTask.getId();

        jmsContext.createProducer().send(taskQueue, messagePayload);

        LOGGER.info("--> JMS: Mensaje enviado a la cola para la tarea " + newTask.getId());

        return new TaskDTO(newTask.getId(), newTask.getTitle(), newTask.getStatus());
    }
}
```

-----

## 4\. Paso 4: Recibir el Mensaje (El Consumidor MDB)

Ahora creamos el componente que escucha. Este componente vive "fuera" del flujo HTTP de la petición.

Crea el paquete `com.mycompany.projecttracker.service.messaging`.
Crea la clase [`NotificationMDB`](src/main/java/com/mycompany/projecttracker/service/messaging/NotificationMDB.java):

```java
package com.mycompany.projecttracker.service.messaging;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import java.util.logging.Logger;

/**
 * Message-Driven Bean (MDB).
 * Escucha asíncronamente la cola 'TaskQueue'.
 */
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:app/jms/TaskQueue"),
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue")
})
public class NotificationMDB implements MessageListener {

    private static final Logger LOGGER = Logger.getLogger(NotificationMDB.class.getName());

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage textMessage) {
                String payload = textMessage.getText();
                LOGGER.info("--> MDB Recibido: Procesando notificación para payload: " + payload);

                // Simular envío de email (espera de 2 segundos)
                // Esto NO bloquea al usuario, porque corre en un hilo separado del pool del MDB.
                Thread.sleep(2000);

                String[] parts = payload.split(":");
                LOGGER.info("--> EMAIL ENVIADO: 'Nueva tarea creada en Proyecto " + parts[0] + " con ID " + parts[1] + "'");
            }
        } catch (JMSException | InterruptedException e) {
            LOGGER.severe("Error procesando mensaje JMS: " + e.getMessage());
        }
    }
}
```

-----

## 5\. Paso 5: Exponer en REST

Necesitamos un endpoint para llamar a `createTask`.

Modifica [`com.mycompany.projecttracker.rest.ProjectResource`](src/main/java/com/mycompany/projecttracker/rest/ProjectResource.java):

```java
// ... imports
import com.mycompany.projecttracker.model.TaskDTO;

// ... dentro de la clase ...

    /**
     * Sub-recurso: Crear una tarea para un proyecto específico.
     * POST /api/projects/{id}/tasks
     */
    @POST
    @Path("/{id}/tasks")
    @RolesAllowed({"ADMIN", "USER"}) // Usuarios logueados pueden crear tareas
    public Response createTask(@PathParam("id") Long projectId, @Valid TaskDTO taskDto) {
        try {
            TaskDTO createdTask = projectService.createTask(projectId, taskDto);
            return Response.ok(createdTask).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
```

-----

## 6\. Probar el Desacoplamiento

El objetivo es ver que la respuesta HTTP es rápida, aunque el "envío de email" tarde 2 segundos.

1.  **Construye y Despliega:**
    `mvn clean package`.

   2.  **Llama al Endpoint:**

       ```sh
       # Crea una tarea en el Proyecto 1
       curl -v -X POST http://localhost:8080/project-tracker/resources/projects/1/tasks \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $TOKEN" \
            -d '{"title":"Implementar JMS"}'
       ```
    
       Aquí tengo un script en PowerShell que permite obtener el token y luego usarlo para hacer la petición al Endpoint que acabamos de crear:
    
       ```powershell
       # Aqui obtenemos el token
       $headers=@{}
       $headers.Add("content-type", "application/json")
       $response = Invoke-RestMethod -Uri 'http://localhost:8080/project-tracker/resources/auth/login' -Method POST -Headers $headers -ContentType 'application/json' -Body '{
         "username": "admin",
         "password": "admin123"
       }'
       
       # Y ahora hacemos post al usando el token
       $headers=@{}
       $headers.Add("content-type", "application/json")
       $headers.Add("Authorization", "Bearer $($response.token)")
       $response = Invoke-RestMethod -Uri 'http://localhost:8080/project-tracker/resources/projects/1/tasks' -Method POST -Headers $headers -ContentType 'application/json' -Body '{
           "title": "Implementar JMS"
       }'
       $response | ConvertTo-Json  # Aqui mostramos el contenido que nos devuelve el endpoint

       ```

3.  **Observa el Comportamiento:**

    * **Consola cURL:** Recibes el JSON de la tarea creada **inmediatamente** (milisegundos). No esperas los 2 segundos.
      ![](https://i.imgur.com/wCBAbYI.png)
    * **Logs de Payara:**
        1.  Verás `--> JMS: Mensaje enviado...` (inmediato).
        2.  Aproximadamente al mismo tiempo, el usuario recibe el 200 OK.
        3.  Verás `--> MDB Recibido...`.
        4.  **2 segundos después**, verás `--> EMAIL ENVIADO...`.
        ![](https://i.imgur.com/5PBGtmh.png)

**¡Éxito\!** Has desacoplado la lógica crítica (guardar tarea) de la lógica secundaria (notificar), haciendo tu aplicación más rápida y resiliente. Si el sistema de correo falla, la tarea ya está guardada y el usuario feliz.