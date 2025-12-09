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