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