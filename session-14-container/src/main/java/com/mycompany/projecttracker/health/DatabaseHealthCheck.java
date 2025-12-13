package com.mycompany.projecttracker.health;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness; // Ojo: import de MicroProfile
import javax.sql.DataSource;
import java.sql.Connection;

@Readiness // Indica que este chequeo determina si la app está "Lista"
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    // Inyectamos el mismo DataSource que configuramos en la Sesión 3
    @Resource(lookup = "jdbc/projectTracker")
    private DataSource ds;

    @Override
    public HealthCheckResponse call() {

        try (Connection conn = ds.getConnection()) {
            if (conn.isValid(2)) { // Timeout de 2 segundos
                return HealthCheckResponse.named("Database Connection")
                    .up()
                    .withData("database", "PostgreSQL at Docket")
                    .build();
            } else {
                return HealthCheckResponse.named("Database Connection")
                    .down()
                    .withData("error", "Conexión inválida")
                    .build();
            }
        } catch (Exception e) {
            return HealthCheckResponse.named("Database Connection")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}