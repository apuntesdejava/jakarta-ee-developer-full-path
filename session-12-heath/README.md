# Sesión 12: Observabilidad (Health & Metrics)

Cuando despliegas `ProjectTracker` en un entorno real, necesitas responder dos preguntas vitales sin mirar los logs:

1.  **¿Estás viva y lista?** (Para que el balanceador de carga te envíe tráfico o te reinicie).
2.  **¿Qué tal vas?** (¿Cuántas peticiones recibes? ¿Estás lenta?).

En esta sesión, implementaremos **MicroProfile Health** y **MicroProfile Metrics**.

**Especificaciones a cubrir:**

* [**MicroProfile Health 4.0+:**](https://microprofile.io/specifications/health/4-0/) Chequeos de estado estandarizados (`/health`).
* [**MicroProfile Metrics 5.0+:**](https://microprofile.io/specifications/metrics/5-1/) Métricas de rendimiento (`/metrics`).

-----

## 1\. Paso 0: Dependencias

Aunque Payara *tiene* las librerías internamente, necesitamos la API para compilar nuestras clases. Añade esto a tu [`pom.xml`](pom.xml) (si no usaste el perfil "MicroProfile" al inicio):

```xml
<dependencies>
    <dependency>
        <groupId>org.eclipse.microprofile</groupId>
        <artifactId>microprofile</artifactId>
        <version>6.1</version> <type>pom</type>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

-----

## 2\. Paso 1: Chequeo de Salud (`DatabaseHealthCheck`)

Payara ya expone `/health` por defecto, pero solo dice "UP" si el servidor está encendido. Queremos ser más específicos: **"¿Estoy conectado a la base de datos?"**.

Usaremos `@Readiness`.

* **Liveness:** "¿Estoy vivo?" (Si no, reiníciame).
* **Readiness:** "¿Estoy listo para trabajar?" (Si no, no me mandes tráfico, pero no me mates todavía, quizás la BBDD está reiniciando).

Crea la clase [`com.mycompany.projecttracker.health.DatabaseHealthCheck`](src/main/java/com/mycompany/projecttracker/health/DatabaseHealthCheck.java):

```java
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
```

-----

## 3\. Paso 2: Añadir Métricas al API REST

Ahora queremos saber qué está pasando con nuestros endpoints. ¿Cuánta gente pide proyectos? ¿Cuánto tarda en responder?

Vamos a instrumentar nuestro `ProjectResource`.

Modifica [`com.mycompany.projecttracker.rest.ProjectResource`](src/main/java/com/mycompany/projecttracker/rest/ProjectResource.java):

```java
// ... imports existentes
import org.eclipse.microprofile.metrics.annotation.Counted; // Importar
import org.eclipse.microprofile.metrics.annotation.Timed;   // Importar

@Path("/projects")
@RequestScoped
public class ProjectResource {

    // ... inyecciones ...

    @GET
    @PermitAll
    // @Counted: Cuenta cuántas veces se llama a este método (monótono incremental)
    @Counted(name = "getAllProjects_total", description = "Total de veces que se listaron los proyectos")
    // @Timed: Mide cuánto tarda la ejecución y estadísticas (media, max, min)
    @Timed(name = "getAllProjects_timer", description = "Tiempo de respuesta de listado", unit = "milliseconds")
    public Response getProjects() {
        // ... (código existente)
    }

    @POST
    @RolesAllowed("ADMIN")
    @Counted(name = "createProject_total", description = "Total de proyectos creados")
    public Response createProject(@Valid ProjectDTO project) {
        // ... (código existente)
    }
    
    // ... resto de métodos
}
```

-----

## 4\. Paso 3: Probar y Verificar

Esta es la parte donde vemos la "magia" de la observabilidad.

1.  **Construye y Despliega:** `mvn clean package`.

2.  **Genera Tráfico:**
    Haz varias llamadas a tu API para generar datos.

    * `curl http://localhost:8080/project-tracker/resources/projects` (Hazlo unas 5 o 6 veces).
    * `curl -X POST ...` (Crea un par de proyectos si tienes el token a mano).

3.  **Verifica la Salud (/health):**
    Abre en tu navegador o usa curl:
    [**http://localhost:8080/health**](http://localhost:8080/health)

    **Deberías ver:**

    ```json
    {
        "status": "UP",
        "checks": [
            {
                "name": "Database Connection",
                "status": "UP",
                "data": {
                    "database": "PostgreSQL at Docket"
                }
            }
        ]
    }
    ```

    ![](https://i.imgur.com/SK7COk8.png)

    *Payara agrega automáticamente otros chequeos (memoria, disco), pero ahí está el tuyo personalizado.*\
    \
    ¿Y cómo luce cuando está caída la base de datos? Bueno, detengamos la base de datos y probemos. Como lo tenemos en un contenedor, bastará con detenerlo. Este sería el resultado del endpoint:

    ![](https://i.imgur.com/6S5hYY8.png)



4.  **Verifica las Métricas (/metrics):**
    Abre: [**http://localhost:8080/metrics**](http://localhost:8080/metrics)

    Por defecto, esto devuelve datos en formato **Prometheus** (texto plano), que es el estándar de la industria para herramientas como Grafana.

    **Busca en el texto algo como:**

    ```text
    # TYPE com_mycompany_projecttracker_rest_ProjectResource_createProject_total counter
    # HELP com_mycompany_projecttracker_rest_ProjectResource_createProject_total Total de proyectos creados
    com_mycompany_projecttracker_rest_ProjectResource_createProject_total{mp_scope="application"} 7.0

    # TYPE com_mycompany_projecttracker_rest_ProjectResource_getAllProjects_timer_seconds summary
    com_mycompany_projecttracker_rest_ProjectResource_getAllProjects_timer_seconds{mp_scope="application",quantile="0.5"} 8926800.0
    com_mycompany_projecttracker_rest_ProjectResource_getAllProjects_timer_seconds{mp_scope="application",quantile="0.75"} 9537300.0
    ...
    ```
 
    ![](https://i.imgur.com/PnbJK6i.png)

-----

### ¿Por qué esto es importante?

En un entorno profesional:

1.  **Kubernetes** llamará a `/health/readiness` cada 10 segundos. Si tu `DatabaseHealthCheck` devuelve `DOWN` (porque la DB se cayó), Kubernetes dejará de enviar usuarios a tu instancia hasta que se recupere.
2.  **Prometheus** llamará a `/metrics` cada minuto, guardando el historial.
3.  **Grafana** leerá esos datos y te permitirá crear un dashboard con un gráfico que diga "Peticiones por segundo" o "Tiempo de respuesta promedio".

¡Has convertido tu aplicación en un ciudadano de primera clase para la nube ("Cloud Native")\!
