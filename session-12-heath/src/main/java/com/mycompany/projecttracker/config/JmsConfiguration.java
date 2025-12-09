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