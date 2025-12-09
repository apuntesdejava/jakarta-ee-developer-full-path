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