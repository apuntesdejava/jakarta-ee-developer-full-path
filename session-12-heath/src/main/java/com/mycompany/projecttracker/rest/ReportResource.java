package com.mycompany.projecttracker.rest;

import com.mycompany.projecttracker.service.ReportService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/reports")
public class ReportResource {

    @Inject
    private ReportService reportService;

    @POST
    @Path("/{projectId}")
    @RolesAllowed({"ADMIN", "USER"}) // Ambos roles pueden pedir reportes
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestReport(@PathParam("projectId") Long projectId, @Context SecurityContext securityContext) {

        String username = securityContext.getUserPrincipal().getName();

        // Llamamos al servicio asíncrono.
        // NO esperamos a que termine (no usamos .get() ni .join()).
        reportService.generateReportAsync(projectId, username);

        // Respondemos inmediatamente al usuario, en formato JSON
        var response = Json.createObjectBuilder()
            .add("status","Reporte solicitado. Procesando en segundo plano.")
            .build();
        return Response.accepted()
            .entity(response)
            .build();
    }
}