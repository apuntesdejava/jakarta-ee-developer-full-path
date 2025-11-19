package com.mycompany.projecttracker.rest;

import com.mycompany.projecttracker.model.ProjectDTO;
import com.mycompany.projecttracker.service.ProjectService;
import jakarta.inject.Inject;
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
import java.util.List;

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
     * Método para OBTENER todos los proyectos.
     * Responde a: GET /resources/projects
     */
    @GET
    public Response getAllProjects() {
        // La capa REST solo coordina. La lógica está en el servicio.
        List<ProjectDTO> projects = projectService.findAll();
        return Response.ok(projects).build();
    }

    /**
     * Método para OBTENER un proyecto por su ID.
     * Responde a: GET /resources/projects/{id}
     */
    @GET
    @Path("/{id}")
    public Response getProjectById(@PathParam("id") Long id) {
        // Usamos Optional para un manejo de "no encontrado" más limpio
        return projectService.findById(id)
            .map(project -> Response.ok(project).build()) // 200 OK si se encuentra
            .orElse(Response.status(Response.Status.NOT_FOUND).build()); // 404 si no
    }

    /**
     * Método para CREAR un nuevo proyecto.
     * Responde a: POST /resources/projects
     */
    @POST
    public Response createProject(ProjectDTO projectRequest) {
        // Validación (aún simple, la mejoraremos en la Sesión 4)
        if (projectRequest == null || projectRequest.name() == null || projectRequest.name().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("El nombre del proyecto es obligatorio.")
                .build();
        }

        ProjectDTO newProject = projectService.create(projectRequest);

        // Construir la URL del nuevo recurso
        URI location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(newProject.id())).build();
        return Response.created(location).entity(newProject).build();
    }
}