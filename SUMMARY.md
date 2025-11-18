# Table of contents

# [Tutorial: Aplicación Full-Stack con Jakarta EE 11 y Payara 7](README.md)


Bienvenido a este tutorial paso a paso para construir una aplicación empresarial moderna desde cero utilizando **Jakarta EE 11**. Usaremos **Payara 7 Community**, ya que es la versión compatible con Jakarta EE 11 (como se indica en las últimas notas de la documentación que proporcionaste, que cubren las versiones 7.x).

**Tema de la Aplicación: `ProjectTracker`**

  * Una aplicación web para gestionar Proyectos.
  * Cada Proyecto puede tener múltiples Tareas.
  * Los usuarios pueden ser asignados a Tareas.

**Pila Tecnológica:**

  * **Jakarta EE 11** (Full Platform)
  * **Java 21** (para aprovechar características modernas como Virtual Threads)
  * **Payara 7 Community** (El servidor de aplicaciones)
  * **Maven** (Gestión de dependencias)

-----

## [Sesión 0: Configuración del Entorno y "Hola Mundo"](session-00-setup/README.md)

**Objetivo:** Configurar el proyecto base y el servidor.
**Especificaciones:** Ninguna (solo configuración).

1.  **Instalar Payara 7:** Descargar e instalar la versión más reciente de **Payara 7 Community Edition**. Esta es la versión necesaria para Jakarta EE 11.
2.  **Usar Payara Starter:** (Mencionado en los blogs de Payara) Iremos a `start.payara.fish`.
3.  **Generar el Proyecto:**
      * Seleccionar `Jakarta EE 11` y el perfil `Web Profile`.
      * Añadir `Java 21`.
      * Generar y descargar el esqueleto del proyecto Maven.
4.  **Hola Mundo (REST):** Crear un endpoint simple para verificar que el servidor funciona.
5.  **README.md:** Crear el `README.md` principal en Github explicando el proyecto.

## [Sesión 1: La Capa API REST (Endpoints)](session-01-jaxrs/README.md)

**Objetivo:** Crear los endpoints HTTP para gestionar Proyectos.
**Especificaciones:**

  * **Jakarta RESTful Web Services 4.0 (JAX-RS):** Para crear los endpoints.
  * **Jakarta JSON Binding 3.1 (JSON-B):** Para la serialización/deserialización automática de Java a JSON.

<!-- end list -->

1.  Crear la clase `ProjectResource.java`.
2.  Mapear las rutas: `GET /api/projects`, `GET /api/projects/{id}`, `POST /api/projects`.
3.  Crear un DTO (Data Transfer Object) simple, por ejemplo, `ProjectDTO`. 

[//]: # (4.  **Novedad EE 11:** Mostrar cómo usar la inyección de CDI &#40;`@Inject`&#41; en lugar del antiguo `@Context` &#40;ahora obsoleto&#41; para inyectar información de la solicitud.)

## [Sesión 2: El Corazón de la Aplicación (Inyección de Dependencias)](session-02-cdi/README.md)

**Objetivo:** Conectar la capa REST con la lógica de negocio usando CDI.
**Especificaciones:**

  * **Jakarta Contexts and Dependency Injection 4.1 (CDI):** El pilar de la plataforma.

<!-- end list -->

1.  Crear una clase de servicio: `ProjectService.java`.
2.  Anotarla con `@ApplicationScoped`.
3.  Inyectar `ProjectService` en `ProjectResource` usando `@Inject`.
4.  Mover la lógica de "negocio" (por ahora simulada) al servicio.
5.  **Novedad EE 11:** Explicar el soporte mejorado de CDI, como `@Priority` en los productores.

## [Sesión 3: La Capa de Persistencia (Base de Datos)](session-03-jpa/README.md)

**Objetivo:** Definir el modelo de datos y guardarlo en la base de datos.
**Especificaciones:**

  * **Jakarta Persistence 3.2 (JPA):** El estándar para el mapeo Objeto-Relacional (ORM).

<!-- end list -->

1.  Configurar el `persistence.xml` y un Datasource en Payara (ej. H2 o PostgreSQL).
2.  Crear las Entidades: `Project.java`, `Task.java`.
3.  Definir las relaciones (`@OneToMany`, `@ManyToOne`).
4.  **Novedad EE 11:**
      * Usar tipos de `java.time` (como `Instant` o `Year`) que ahora son soportados nativamente.
      * Mostrar cómo usar un `Record` de Java como `@Embeddable` (ej. un `AuditInfo(createdBy, createdAt)`).

## [Sesión 4: Validación de Datos](session-04-validation/README.md)

**Objetivo:** Asegurar que los datos que entran a la API sean válidos.
**Especificaciones:**

  * **Jakarta Validation 3.1:** Para la validación basada en anotaciones.

<!-- end list -->

1.  Añadir anotaciones (`@NotNull`, `@Size`, `@Email`) a las Entidades y DTOs.
2.  Activar la validación en los endpoints REST (usando `@Valid`).
3.  Manejar las `ConstraintViolationException` para devolver errores 400 (Bad Request) claros.
4.  **Novedad EE 11:** Mostrar cómo aplicar reglas de validación a `Records` de Java.

## Sesión 5: Simplificando el Acceso a Datos (¡La gran novedad\!)

**Objetivo:** Reducir drásticamente el código boilerplate de la base de datos.
**Especificaciones:**

  * **Jakarta Data 1.0 (¡NUEVA\!):** La nueva especificación de Jakarta EE 11.

<!-- end list -->

1.  Eliminar el código manual de `EntityManager` del `ProjectService`.
2.  Crear una interfaz: `ProjectRepository`.
3.  Hacer que extienda `CrudRepository<Project, Long>`.
4.  Inyectar `ProjectRepository` en el servicio y usarla directamente (`repository.save(project)`, `repository.findById(id)`).
5.  Mostrar cómo crear un método de consulta personalizado (ej. `List<Project> findByStatus(ProjectStatus status);`).

## Sesión 6: La Interfaz de Usuario (UI)

**Objetivo:** Construir una interfaz web simple para interactuar con la aplicación.
**Especificaciones:**

  * **Jakarta Faces 4.1 (JSF):** Para la UI renderizada en el servidor.
  * **Jakarta Expression Language 6.0 (EL):** Para conectar la vista al backend.

<!-- end list -->

1.  Crear una página XHTML (`index.xhtml`).
2.  Crear un Bean de CDI (`@Named @RequestScoped`) llamado `ProjectBean.java`.
3.  Inyectar el `ProjectService` en el Bean.
4.  Usar JSF (ej. `<h:dataTable>`, `<h:commandButton>`) para mostrar y crear proyectos.

## Sesión 7: Seguridad de la Aplicación

**Objetivo:** Proteger los endpoints y la UI.
**Especificaciones:**

  * **Jakarta Security 4.0:** API unificada de seguridad.
  * **Jakarta Authentication 3.1:** Para los mecanismos de autenticación (ej. JWT, Basic Auth).

<!-- end list -->

1.  Asegurar los endpoints REST usando `@RolesAllowed`.
2.  Configurar un Identity Store (ej. Básico en BBDD o LDAP).
3.  **Novedad EE 11:** Mostrar el `InMemoryIdentityStore` para pruebas rápidas.
4.  Implementar un `HttpAuthenticationMechanism` (ej. JWT) para la API REST.
5.  Configurar la seguridad de JSF (basada en formulario).

## Sesión 8: Concurrencia Moderna (Virtual Threads)

**Objetivo:** Manejar tareas asíncronas de larga duración sin bloquear hilos.
**Especificaciones:**

  * **Jakarta Concurrency 3.1:** Para gestionar hilos de forma controlada por el servidor.

<!-- end list -->

1.  **Requisito:** Asegurarse de correr sobre **Java 21**.
2.  Crear un servicio asíncrono (ej. `ReportGeneratorService`).
3.  **Novedad EE 11:** Definir un `ManagedExecutorService` (usando `@ManagedExecutorDefinition`) y configurarlo para que use **Virtual Threads**.
4.  Ejecutar una tarea asíncrona (ej. `CompletableFuture.runAsync(..., executor)`).

## Sesión 9: Mensajería y Tareas Desacopladas

**Objetivo:** Usar mensajería para desacoplar partes de la aplicación.
**Especificaciones:**

  * **Jakarta Messaging 3.1 (JMS):** Para la comunicación asíncrona.
  * **Message-Driven Beans (EJB Lite):** Para consumir mensajes.

<!-- end list -->

1.  Configurar una Queue JMS en Payara.
2.  Cuando se crea una Tarea nueva, el `ProjectService` envía un mensaje a la Queue.
3.  Crear un Message-Driven Bean (MDB) que escuche esa Queue (ej. `NotificationMDB`) y simule el envío de un email.

## Sesión 10: Tareas Programadas (Jobs)

**Objetivo:** Ejecutar tareas en segundo plano de forma programada.
**Especificaciones:**

  * **Jakarta Enterprise Beans 4.0 Lite (EJB):** Específicamente el `@Schedule`.

<!-- end list -->

1.  Crear un `TaskCleanupService`.
2.  Crear un método con la anotación `@Schedule(hour = "0", minute = "0")`.
3.  Este método buscará y archivará tareas antiguas (ej. completadas hace más de 90 días).
4.  (Nota: Jakarta Concurrency 3.1 también introduce `@Scheduled`, se pueden mostrar ambas formas).

## Sesión 11: Interacción en Tiempo Real (Web Sockets)

**Objetivo:** Notificar a la UI en tiempo real cuando los datos cambien.
**Especificaciones:**

  * **Jakarta WebSocket 2.2:** Para comunicación bidireccional.

<!-- end list -->

1.  Crear un `ProjectDashboardEndpoint.java` anotado con `@ServerEndpoint`.
2.  Cuando la UI de JSF se conecte, se registra el cliente.
3.  Usar un Evento de CDI (`@Observes`) para que cuando el `ProjectService` cree un proyecto, dispare un evento.
4.  El `ProjectDashboardEndpoint` recibe el evento y envía el nuevo proyecto (como JSON) a todos los clientes WebSocket conectados.

## Sesión 12: Observabilidad y Salud (MicroProfile)

**Objetivo:** Exponer el estado de la aplicación para monitoreo.
**Especificaciones:**

  * **Eclipse MicroProfile (Integrado en Payara):** Payara incluye MicroProfile, que complementa a Jakarta EE.
  * **MicroProfile Health:** Para chequeos de salud.
  * **MicroProfile Metrics:** Para métricas de la aplicación.

<!-- end list -->

1.  Crear una clase `DatabaseHealthCheck` que implemente `HealthCheck` y verifique la conexión a la BBDD.
2.  Anotarla con `@Liveness` o `@Readiness`.
3.  Acceder al endpoint `/health` de Payara.
4.  Añadir métricas (ej. `@Counted` o `@Timed`) al `ProjectResource` para ver cuántas veces se llama a la API.
5.  Acceder al endpoint `/metrics`.

## Sesión 13: El Perfil "Core" (Microservicios)

**Objetivo:** Entender cómo construir un microservicio ligero.
**Especificaciones:**

  * **Jakarta EE 11 Core Profile:** El subconjunto de especificaciones para runtimes ligeros.

<!-- end list -->

1.  Explicar qué es el Core Profile (CDI, JAX-RS, JSON-B/P, Concurrency, etc.).
2.  Crear un nuevo módulo Maven (`pom.xml`) que *solo* use las dependencias del Core Profile.
3.  Mostrar cómo nuestra API REST (Sesión 1) y Servicios (Sesión 2) pueden funcionar en este perfil mínimo, ideal para contenedores.
4.  Empaquetarlo con **Payara Micro** en lugar del servidor completo.
