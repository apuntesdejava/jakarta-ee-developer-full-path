package com.mycompany.projecttracker.rest;

import com.mycompany.projecttracker.security.TokenService;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/auth")
public class AuthResource {

    @Inject
    private IdentityStoreHandler identityStoreHandler; // Valida contra InMemoryIdentityStore

    @Inject
    private TokenService tokenService;

    public record LoginRequest(@NotNull String username, @NotNull String password) {}

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(@Valid LoginRequest request) {
        // 1. Validar credenciales contra el Identity Store
        CredentialValidationResult result = identityStoreHandler.validate(
            new UsernamePasswordCredential(request.username, request.password)
        );

        if (result.getStatus() == CredentialValidationResult.Status.VALID) {
            // 2. Si es válido, generar JWT
            String token = tokenService.generateToken(result.getCallerPrincipal().getName(), result.getCallerGroups());
            return Response.ok(Map.of("token", token)).build();
        } else {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }
}