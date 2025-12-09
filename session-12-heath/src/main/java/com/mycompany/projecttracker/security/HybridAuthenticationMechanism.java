package com.mycompany.projecttracker.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AutoApplySession;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;

@ApplicationScoped
@AutoApplySession
public class HybridAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    private TokenService tokenService;

    @Inject
    private IdentityStoreHandler identityStoreHandler;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response, HttpMessageContext context) throws AuthenticationException {

        String path = request.getRequestURI();

        // ------------------------------------------------------------------
        // CASO 1: API REST (Stateless - JWT)
        // ------------------------------------------------------------------
        if (path.contains("/resources/")) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String username = tokenService.validateTokenAndGetUser(token);
                    Set<String> roles = tokenService.getRoles(token);
                    return context.notifyContainerAboutLogin(username, roles);
                } catch (Exception e) {
                    return context.responseUnauthorized();
                }
            }
            // Si no hay token, dejamos pasar como Anónimo.
            // JAX-RS decidirá si acepta (@PermitAll) o rechaza (401).
            return context.doNothing();
        }

        // ------------------------------------------------------------------
        // CASO 2: WEB JSF (Stateful - Sesión/Cookies)
        // ------------------------------------------------------------------

        // A. Procesar intento de Login (cuando el LoginBean envía credenciales)
        Credential credential = context.getAuthParameters().getCredential();
        if (credential != null) {
            CredentialValidationResult result = identityStoreHandler.validate(credential);
            return context.notifyContainerAboutLogin(result);
        }

        // B. Protección de Páginas Web
        // Definimos nuestra regla: Todas las páginas .xhtml son privadas, excepto login.xhtml
        boolean isLoginPage = path.contains("login.xhtml");
        boolean isFacelet = path.endsWith(".xhtml"); // Ojo: verifica también si accedes a /faces/...

        // También verifica la raíz si tu servidor sirve index.xhtml por defecto
        boolean isRoot = path.endsWith("/");

        // Si intentan entrar a una página protegida Y NO hay usuario logueado...
        if ((isFacelet || isRoot) && !isLoginPage && request.getUserPrincipal() == null) {
            try {
                // Redirigir al login
                response.sendRedirect(request.getContextPath() + "/login.xhtml");
                return AuthenticationStatus.SEND_CONTINUE;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Si está logueado o es una página pública (login.xhtml, .css, .js), dejar pasar.
        return context.doNothing();
    }
}