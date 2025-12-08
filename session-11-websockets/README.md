# Sesión 11: Interacción en Tiempo Real (WebSockets + CDI Events)

Vamos a conectar tres mundos:

1.  **Lógica de Negocio (Service):** Algo sucede (se crea un proyecto).
2.  **Eventos (CDI):** El servicio grita "¡Pasó algo\!" sin saber a quién le importa.
3.  **WebSockets (Endpoint):** Escucha ese grito y se lo cuenta inmediatamente a todos los navegadores conectados.

**Especificaciones de Jakarta EE 11 a cubrir:**

* [**Jakarta WebSocket 2.2:**](https://jakarta.ee/specifications/websocket/2.2/) Comunicación bidireccional.
* [**Jakarta Contexts and Dependency Injection (CDI):**](https://jakarta.ee/specifications/cdi/4.1/) Eventos (`Event<T>` y `@Observes`).

-----

## 1\. Paso 1: Definir el Evento

Primero, necesitamos un objeto simple que represente "lo que pasó". Usaremos un POJO (o Record) simple que envuelva nuestro DTO.

Crea el paquete `com.mycompany.projecttracker.event`.
Crea el record `ProjectCreatedEvent.java`:

```java
package com.mycompany.projecttracker.event;

import com.mycompany.projecttracker.model.ProjectDTO;

/**
 * Evento CDI que se dispara cuando un proyecto es creado exitosamente.
 * Es un simple contenedor de datos.
 */
public record ProjectCreatedEvent(ProjectDTO project) {
}
```

-----

## 2\. Paso 2: Disparar el Evento desde el Servicio

Ahora, modifiquemos `ProjectService` para que, en lugar de guardar silencio tras crear un proyecto, dispare este evento.

Abre [`com.mycompany.projecttracker.service.ProjectService.java`](src/main/java/com/mycompany/projecttracker/service/ProjectService.java):

```java
// ... imports
import jakarta.enterprise.event.Event; // <-- Importante: Jakarta CDI Event
import com.mycompany.projecttracker.event.ProjectCreatedEvent;

@ApplicationScoped
@Transactional
public class ProjectService {

    // ... inyecciones previas (repository, mapper, jmsContext...)

    // 1. Inyectamos el disparador de eventos
    @Inject
    private Event<ProjectCreatedEvent> projectEvent;
    
    // los otros métodos

    public ProjectDTO create(ProjectDTO projectRequest) {
        // ... (lógica de mapeo y guardado en repository) ...
        Project newEntity = mapper.toEntity(projectRequest);
        // ... set audit info ...
        newEntity = repository.save(newEntity);
        
        ProjectDTO createdDto = mapper.toDTO(newEntity);

        // 2. ¡DISPARAMOS EL EVENTO!
        // Esto notificará a cualquier @Observes en la aplicación de forma síncrona
        // (o asíncrona si usamos fireAsync, pero usaremos fire() por simplicidad).
        projectEvent.fire(new ProjectCreatedEvent(createdDto));

        LOGGER.info("--> Evento CDI disparado para Proyecto ID: " + createdDto.id());

        return createdDto;
    }

    // ... resto de métodos
}
```

-----

## 3\. Paso 3: Gestionar Sesiones y Difusión (El Broadcaster)

Aquí está el truco arquitectónico. Los WebSockets (`@ServerEndpoint`) se crean **uno por cada conexión de cliente**. No son Singletons.

Para poder enviar un mensaje a **todos**, necesitamos un componente central (`@ApplicationScoped`) que guarde la lista de sesiones abiertas y escuche el evento CDI.

Crea el paquete `com.mycompany.projecttracker.websocket`.
Crea la clase [`DashboardSessionManager.java`](src/main/java/com/mycompany/projecttracker/websocket/DashboardSessionManager.java):

```java
package com.mycompany.projecttracker.websocket;

import com.mycompany.projecttracker.event.ProjectCreatedEvent;
import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.websocket.Session;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class DashboardSessionManager {

    private static final Logger LOGGER = Logger.getLogger(DashboardSessionManager.class.getName());
    
    // Colección thread-safe para guardar las sesiones de los navegadores conectados
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    // Instancia de JSON-B para convertir objetos a texto JSON manualmente
    private final Jsonb jsonb = JsonbBuilder.create();

    public void addSession(Session session) {
        sessions.add(session);
    }

    public void removeSession(Session session) {
        sessions.remove(session);
    }

    /**
     * Este método escucha el Evento CDI disparado por ProjectService.
     * Se ejecuta automáticamente cuando alguien llama a projectEvent.fire().
     */
    public void onProjectCreated(@Observes ProjectCreatedEvent event) {
        ProjectDTO newProject = event.project();
        LOGGER.info("--> [WebSocket] Recibido evento de nuevo proyecto: " + newProject.name());
        
        // Convertimos el objeto Java a JSON String
        String jsonMessage = jsonb.toJson(newProject);
        
        // Enviamos el JSON a todos los navegadores conectados
        sendToAll(jsonMessage);
    }

    private void sendToAll(String message) {
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    // Envío asíncrono para no bloquear el hilo si un cliente es lento
                    session.getAsyncRemote().sendText(message);
                } catch (Exception e) {
                    LOGGER.warning("Error enviando websocket: " + e.getMessage());
                }
            }
        });
    }
}
```

-----

## 4\. Paso 4: El Endpoint WebSocket

Ahora creamos la "puerta" por donde se conectan los navegadores. Gracias a la integración de Jakarta EE, podemos inyectar nuestro `DashboardSessionManager` directamente en el Endpoint.

Crea en el mismo paquete [`ProjectDashboardEndpoint.java`](src/main/java/com/mycompany/projecttracker/websocket/ProjectDashboardEndpoint.java):

```java
package com.mycompany.projecttracker.websocket;

import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.util.logging.Logger;

/**
 * Endpoint WebSocket.
 * URL: ws://localhost:8080/project-tracker/ws/dashboard
 */
@ServerEndpoint("/ws/dashboard")
public class ProjectDashboardEndpoint {

    private static final Logger LOGGER = Logger.getLogger(ProjectDashboardEndpoint.class.getName());

    @Inject
    private DashboardSessionManager sessionManager;

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.info("--> Nuevo cliente WebSocket conectado: " + session.getId());
        sessionManager.addSession(session);
    }

    @OnClose
    public void onClose(Session session) {
        sessionManager.removeSession(session);
    }

    // No necesitamos @OnMessage porque el flujo es unidireccional (Servidor -> Cliente)
    // para este caso de uso (Dashboard de notificaciones).
}
```

-----

## 5\. Paso 5: El Frontend (JavaScript)

Finalmente, necesitamos que `index.xhtml` se conecte a este socket y actualice la tabla cuando llegue un mensaje.

Modifica [`src/main/webapp/index.xhtml`](src/main/webapp/index.xhtml).
Añade este script justo antes de cerrar el `</h:body>`:

```html
    <script type="text/javascript">
        // 1. Calcular la URL del WebSocket dinámicamente
        var wsUrl = "ws://" + document.location.host + "#{request.contextPath}/ws/dashboard";
        console.log("Conectando a: " + wsUrl);

        // 2. Abrir conexión
        var socket = new WebSocket(wsUrl);

        socket.onopen = function(event) {
            console.log("WebSocket conectado!");
        };

        // 3. Manejar mensajes entrantes
        socket.onmessage = function(event) {
            console.log("Mensaje recibido: " + event.data);
            
            var project = JSON.parse(event.data);
            
            // 4. Actualizar la tabla visualmente (Manipulación simple del DOM)
            // Buscamos la tabla generada por JSF. 
            // Nota: JSF genera IDs complejos, pero la clase 'table' nos ayuda.
            var table = document.querySelector(".table tbody");
            
            if (table) {
                var newRow = table.insertRow(-1); // Insertar al final
                // Un poco de animación o color para resaltar
                newRow.style.backgroundColor = "#fff3cd"; 
                setTimeout(() => newRow.style.backgroundColor = "transparent", 2000);

                // Insertar celdas (coincidiendo con las columnas de tu h:dataTable)
                var cellId = newRow.insertCell(0);
                var cellName = newRow.insertCell(1);
                var cellDesc = newRow.insertCell(2);
                var cellStatus = newRow.insertCell(3);

                cellId.textContent = project.id;
                cellName.textContent = project.name;
                cellDesc.textContent = project.description;
                
                // Renderizar el badge de estado
                cellStatus.innerHTML = '<span style="padding: 4px 8px; background-color: #e1f5fe; border-radius: 4px;">' + project.status + '</span>';
            }
        };
    </script>
</h:body>
</html>
```

-----

## 6\. Probar el Tiempo Real

¡El momento de la verdad\! Vamos a ver cómo dos usuarios ven los cambios sin refrescar.

1.  **Reconstruye y Despliega:** `mvn clean package`.
2.  **Abre dos ventanas del navegador:**
    * Ventana A: `http://localhost:8080/project-tracker/` (Login como admin/admin123 si te lo pide).
    * Ventana B: `http://localhost:8080/project-tracker/` (En modo incógnito o en otro navegador, logueado como admin también).
3.  **En la Ventana A:**
    * Rellena el formulario de "Nuevo Proyecto".
    * Ponle de nombre: "Proyecto Tiempo Real".
    * Dale a "Guardar".
4.  **Observa la Ventana B:**
    * **¡Sin tocar nada\!**, deberías ver aparecer mágicamente el "Proyecto Tiempo Real" al final de la tabla, quizás con el color de fondo amarillo momentáneo que programamos.

### Resumen de lo que pasó:

1.  **Browser A** envió POST (JSF Form).
2.  **ProjectService** guardó en BBDD y disparó `projectEvent.fire()`.
3.  **DashboardSessionManager** capturó el evento (`@Observes`).
4.  Serializó el DTO a JSON.
5.  Iteró sobre la sesión del **Browser B** (y A).
6.  Envió el mensaje por el WebSocket.
7.  El JavaScript del **Browser B** recibió el JSON y pintó la fila.

¡Has creado una aplicación reactiva moderna usando estándares puros de Jakarta EE 11\!
