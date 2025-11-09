package com.mycompany.projecttracker.rest;

import com.mycompany.projecttracker.model.ProjectDTO;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Endpoint REST para gestionar Proyectos.
 * Especificaciones: JAX-RS 4.0 y JSON-B 3.1.
 */
@Path("/projects")
@Produces(MediaType.APPLICATION_JSON) // Por defecto, todos los métodos aquí devuelven JSON
@Consumes(MediaType.APPLICATION_JSON) // Por defecto, todos los métodos aquí esperan recibir JSON
public class ProjectResource {

    // --- Simulación de Base de Datos ---
    // (Esto desaparecerá en futuras sesiones cuando usemos CDI y JPA)
    private static final Map<Long, ProjectDTO> projectDatabase = new ConcurrentHashMap<>();
    private static final AtomicLong idCounter = new AtomicLong(0);

    // Bloque estático para poblar con datos de prueba
    static {
        long id1 = idCounter.incrementAndGet();
        projectDatabase.put(id1,
            new ProjectDTO(id1, "Sitio Web Corporativo", "Desarrollo del nuevo sitio web v2", "Activo"));
        long id2 = idCounter.incrementAndGet();
        projectDatabase.put(id2,
            new ProjectDTO(id2, "App Móvil (ProjectTracker)", "Lanzamiento de la app nativa", "Planificado"));
    }
    // --- Fin de la simulación ---


    /**
     * Inyecta información sobre la URI de la petición actual.
     * Nota EE 11: Usamos @Inject (CDI) en lugar del antiguo @Context (JAX-RS).
     * Esto muestra la profunda integración de CDI en toda la plataforma.
     */
    @Context
    private UriInfo uriInfo;

    /**
     * Método para OBTENER todos los proyectos.
     * Responde a: GET /api/projects
     */
    @GET
    public Response getAllProjects() {
        List<ProjectDTO> projects = new ArrayList<>(projectDatabase.values());
        // JAX-RS y JSON-B se encargan de convertir la Lista a un array JSON.
        return Response.ok(projects).build();
    }

    /**
     * Método para OBTENER un proyecto por su ID.
     * Responde a: GET /api/projects/{id}
     */
    @GET
    @Path("/{id}")
    public Response getProjectById(@PathParam("id") Long id) {
        ProjectDTO project = projectDatabase.get(id);

        if (project == null) {
            // Devuelve un error 404 Not Found si no existe
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(project).build();
    }

    /**
     * Método para CREAR un nuevo proyecto.
     * Responde a: POST /api/projects
     */
    @POST
    public Response createProject(ProjectDTO projectRequest) {
        // Valida la entrada (simple por ahora)
        if (projectRequest == null || projectRequest.name() == null || projectRequest.name().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("El nombre del proyecto es obligatorio.")
                .build();
        }

        // Genera un nuevo ID y crea el DTO
        long newId = idCounter.incrementAndGet();
        ProjectDTO newProject = new ProjectDTO(
            newId,
            projectRequest.name(),
            projectRequest.description(),
            "Nuevo" // Estado por defecto
        );

        projectDatabase.put(newId, newProject);

        // Buena práctica REST: Devolver 201 Created con la URL del nuevo recurso
        URI location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(newId)).build();
        return Response.created(location).entity(newProject).build();
    }
}