package com.mycompany.projecttracker.rest;

import com.mycompany.projecttracker.model.ProjectDTO;
import com.mycompany.projecttracker.model.TaskDTO;
import com.mycompany.projecttracker.service.ProjectService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import java.net.URI;
import java.util.List;
import java.util.Properties;

/**
 * Endpoint REST para gestionar Proyectos.
 * ¡Ahora delegando la lógica a un servicio CDI!
 */
@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    // --- ¡Toda la lógica de la "base de datos" ha desaparecido! ---

    @Inject // 3. ¡La magia de CDI!
    private ProjectService projectService; // CDI inyectará el singleton aquí

    @Context
    private UriInfo uriInfo;

    /**
     * Método para OBTENER proyectos, opcionalmente filtrando por estado.
     * Responde a: GET /resources/projects?status=Activo
     */
    @GET
    @PermitAll // Público (o usa @RolesAllowed("USER") si quieres cerrarlo)
    // @Counted: Cuenta cuántas veces se llama a este método (monótono incremental)
    @Counted(name = "getAllProjects_total", description = "Total de veces que se listaron los proyectos")
    // @Timed: Mide cuánto tarda la ejecución y estadísticas (media, max, min)
    @Timed(name = "getAllProjects_timer", description = "Tiempo de respuesta de listado", unit = "milliseconds")
    public Response getProjects(@QueryParam("status") String status) {
        List<ProjectDTO> projects;

        if (status != null && !status.isBlank()) {
            // Usamos nuestro nuevo método de Jakarta Data
            projects = projectService.findByStatus(status);
        } else {
            projects = projectService.findAll();
        }

        return Response.ok(projects).build();
    }

    /**
     * Método para OBTENER un proyecto por su ID.
     * Responde a: GET /resources/projects/{id}
     */
    @GET
    @Path("/{id}")
    @PermitAll
    public Response getProjectById(@PathParam("id") Long id) {
        return projectService.findById(id)
            .map(project -> Response.ok(project).build()) // 200 OK si se encuentra
            .orElse(Response.status(Response.Status.NOT_FOUND).build()); // 404 si no
    }

    /**
     * Método para CREAR un nuevo proyecto.
     * Responde a: POST /resources/projects
     */
    @POST
    @RolesAllowed("ADMIN")
    @Counted(name = "createProject_total", description = "Total de proyectos creados")
    public Response createProject(@Valid ProjectDTO projectRequest) {

        ProjectDTO newProject = projectService.create(projectRequest);
        URI location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(newProject.id())).build();
        return Response.created(location).entity(newProject).build();
    }

    /**
     * Sub-recurso: Crear una tarea para un proyecto específico.
     * POST /api/projects/{id}/tasks
     */
    @POST
    @Path("/{id}/tasks")
    @RolesAllowed({"ADMIN", "USER"}) // Usuarios logueados pueden crear tareas
    public Response createTask(@PathParam("id") Long projectId, @Valid TaskDTO taskDto) {
        try {
            TaskDTO createdTask = projectService.createTask(projectId, taskDto);
            return Response.ok(createdTask).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @POST
    @Path("/import")
    @RolesAllowed("ADMIN")
    public Response runImport() {
        // 1. Obtener el operador de Batch del contenedor
        JobOperator jobOperator = BatchRuntime.getJobOperator();

        // 2. Iniciar el trabajo por su nombre XML (sin la extensión .xml)
        long executionId = jobOperator.start("taskImportJob", new Properties());

        return Response.accepted()
            .entity("Job de importación iniciado con Execution ID: " + executionId)
            .build();
    }
}