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