# Sesión 7: Seguridad Híbrida (JWT + JSF)

En esta sesión convertiremos `ProjectTracker` en una aplicación segura. Implementaremos una arquitectura profesional donde:

1.  La **API REST** estará protegida por **Tokens JWT** (JSON Web Tokens).
2.  La **Web JSF** estará protegida por **Login de Formulario** (Cookies de Sesión).
3.  Los **Usuarios** vendrán de un almacén en memoria (Novedad EE 11).

**Especificaciones de Jakarta EE 11 a cubrir:**

* **Jakarta Security 4.0:** Orquestación de la seguridad.
* **Jakarta Authentication 3.1:** Implementación del mecanismo JWT personalizado.

-----

## 1\. Paso 0: Dependencias (JWT)

Jakarta EE tiene soporte para *verificar* JWT (vía MicroProfile), pero para *generarlos* y manejarlos a bajo nivel en un mecanismo personalizado, necesitamos una pequeña librería de ayuda. Usaremos `jjwt` (Java JWT), que es el estándar de industria.

Añade esto a tu `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.13.0</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.13.0</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.13.0</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

-----

## 2\. Paso 1: El Almacén de Identidad (Novedad EE 11)

Aquí cumplimos el punto de la **Novedad EE 11**. En lugar de configurar una base de datos compleja o un LDAP, usamos la nueva anotación `@InMemoryIdentityStoreDefinition`.

Crea la clase [`com.mycompany.projecttracker.web.SecurityConfig`](src/main/java/com/mycompany/projecttracker/web/SecurityConfig.java):

```java
package com.mycompany.projecttracker.web;

import jakarta.annotation.security.DeclareRoles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;
import jakarta.security.enterprise.identitystore.InMemoryIdentityStoreDefinition;

@FacesConfig
@ApplicationScoped
@DeclareRoles({"ADMIN", "USER"})

//  NOVEDAD EE 11: Almacén de usuarios en memoria para prototipado rápido
@InMemoryIdentityStoreDefinition({
    @InMemoryIdentityStoreDefinition.Credentials(
        callerName = "admin", password = "admin123", groups = {"ADMIN", "USER"}
    ),
    @InMemoryIdentityStoreDefinition.Credentials(
        callerName = "pepe", password = "pepe123", groups = {"USER"}
    )
})
 
public class SecurityConfig {
}
```

-----

## 3\. Paso 2: Servicio de Tokens (JWT)

Necesitamos una clase auxiliar para **Generar** (cuando el usuario hace login) y **Validar** (cuando el usuario pide datos) los tokens.

Crea [`com.mycompany.projecttracker.security.TokenService`](src/main/java/com/mycompany/projecttracker/security/TokenService.java):

```java
package com.mycompany.projecttracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@ApplicationScoped
public class TokenService {

    // En producción, esta clave debe estar en una variable de entorno o vault
    private static final String SECRET_KEY = "MiSuperSecretoParaFirmarTokensJWT_DebeSerLargo";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    /**
     * Genera un JWT firmado con roles y expiración (1 hora).
     */
    public String generateToken(String username, Set<String> roles) {
        return Jwts.builder()
            .subject(username)
            .claim("groups", roles)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hora
            .signWith(key)
            .compact();
    }

    /**
     * Valida el token y extrae el usuario (Subject).
     * Lanza excepción si el token es inválido o expiró.
     */
    public String validateTokenAndGetUser(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.getSubject();
    }

    /**
     * Extrae los roles del token.
     */
    public Set<String> getRoles(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        // Asumimos que viene como una lista de strings
        return Set.copyOf(claims.get("groups", java.util.List.class));
    }
}
```

-----

## 4\. Paso 3: El Mecanismo de Autenticación Híbrido

Este es el corazón de **Jakarta Authentication**. Crearemos una clase que intercepte **todas** las peticiones HTTP.

Su lógica será:

1.  ¿Es una petición a la API (`/resources/...`)? -\> Busca un Token JWT.
2.  ¿Es una petición Web? -\> Deja que JSF maneje la sesión (Form Auth).

Crea [`com.mycompany.projecttracker.security.HybridAuthenticationMechanism`](src/main/java/com/mycompany/projecttracker/security/HybridAuthenticationMechanism.java):

```java
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
```

-----

## 5\. Paso 4: Endpoint de Login para REST

Para obtener el JWT, necesitamos un endpoint público donde enviar usuario/contraseña.

Crea [`com.mycompany.projecttracker.rest.AuthResource`](src/main/java/com/mycompany/projecttracker/rest/AuthResource.java):

```java
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
```

-----

## 6\. Paso 5: Proteger los Endpoints (`@RolesAllowed`)

Ahora aplicamos la seguridad a nuestro `ProjectResource`.

Modifica [`com.mycompany.projecttracker.rest.ProjectResource`](src/main/java/com/mycompany/projecttracker/rest/ProjectResource.java):

```java
// ... imports
import jakarta.annotation.security.RolesAllowed;
import jakarta.annotation.security.PermitAll;

@Path("/projects")
@RequestScoped
public class ProjectResource {

    @Inject
    private ProjectService projectService;

    @GET
    @PermitAll // Público (o usa @RolesAllowed("USER") si quieres cerrarlo)
    public Response getProjects(@QueryParam("status") String status) { 
        // ... 
    }

    @POST
    @RolesAllowed("ADMIN") // Solo JWT con rol ADMIN pasa aquí
    public Response createProject(@Valid ProjectDTO project) {
        return Response.status(Response.Status.CREATED)
                .entity(projectService.create(project))
                .build();
    }
}
```

-----

## 7\. Paso 6: Seguridad JSF (Login Form)

Finalmente, creamos la página de login para la web.

Crea [`src/main/webapp/login.xhtml`](src/main/webapp/login.xhtml):

```xml
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:h="jakarta.faces.html">
<h:head>
    <title>Login</title>
    <style>
        .login-box { width: 300px; margin: 100px auto; padding: 20px; border: 1px solid #ccc; border-radius: 8px; }
        .form-group { margin-bottom: 15px; }
        label { display: block; }
        .error { color: red; }
    </style>
</h:head>
<h:body>
    <div class="login-box">
        <h3>Iniciar Sesión</h3>
        
        <h:panelGroup rendered="#{param['error'] != null}">
            <p class="error">Usuario o contraseña incorrectos</p>
        </h:panelGroup>

        <h:form>
            <div class="form-group">
                <label>Usuario:</label>
                <h:inputText value="#{loginBean.username}" />
            </div>
            <div class="form-group">
                <label>Contraseña:</label>
                <h:inputSecret value="#{loginBean.password}" />
            </div>
            <h:commandButton value="Entrar" action="#{loginBean.login}" />
        </h:form>
    </div>
</h:body>
</html>
```

Y el Bean de Login [`com.mycompany.projecttracker.web.LoginBean`](src/main/java/com/mycompany/projecttracker/web/LoginBean.java):

```java
package com.mycompany.projecttracker.web;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Named
@RequestScoped
public class LoginBean {

    @Inject
    private SecurityContext securityContext;

    @Inject
    private FacesContext facesContext;

    private String username;
    private String password;

    public String login() {
        ExternalContext externalContext = facesContext.getExternalContext();

        // Invoca al mecanismo de seguridad (Form Auth)
        AuthenticationStatus status = securityContext.authenticate(
            (HttpServletRequest) externalContext.getRequest(),
            (HttpServletResponse) externalContext.getResponse(),
            AuthenticationParameters.withParams()
                .credential(new UsernamePasswordCredential(username, password))
        );

        if (status == AuthenticationStatus.SUCCESS) {
            return "index.xhtml?faces-redirect=true";
        } else {
            facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Login fallido", null));
            return null;
        }
    }

    // Getters y Setters para username y password...
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
```

-----

## 8\. Flujo de Seguridad

1.  **Flujo REST:**

    * GET `/project-tracker/resources/projects` sin autenticación, y obtiene todos los registros:\
    **cURL:** 
    ```shell
    curl --request GET \
         --url http://localhost:8080/project-tracker/resources/projects
    ``` 
    **Powershell:**
    ```powershell
    $response = Invoke-RestMethod -Uri 'http://localhost:8080/project-tracker/resources/projects' -Method GET 
    $response | ConvertTo-Json
    ``` 
    \
    ![](https://i.imgur.com/3pBHOoo.png)
    * POST a `/resources/auth/login` con `{username:"admin", password:"admin123"}`.
    * Respuesta: `{"token": "eyJhbGciOi..."}`.\
    **cURL:**
    ```shell
    curl --request POST \
         --url http://localhost:8080/project-tracker/resources/auth/login \
         --header 'Content-Type: application/json' \
         --data '{
           "username": "admin",
           "password": "admin123"
          }'
    ``` 
    **Powershell:**
    ```powershell
    $headers=@{}
    $headers.Add("Content-Type", "application/json")
    $response = Invoke-RestMethod -Uri 'http://localhost:8080/project-tracker/resources/auth/login' -Method POST -Headers $headers -ContentType 'application/json' -Body '{
        "username": "admin",
        "password": "admin123"
    }'
    $response | ConvertTo-Json
    ```
    \
    ![](https://i.imgur.com/3xDx821.png) 
    * POST a `/resources/projects` con Header `Authorization: Bearer eyJhbGciOi...`.
    * `HybridAuthenticationMechanism` valida el token, extrae roles y permite acceso.
      **cURL:**
    ```shell
        curl --request POST \
             --url http://localhost:8080/project-tracker/resources/projects \
             --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImdyb3VwcyI6WyJBRE1JTiIsIlVTRVIiXSwiaWF0IjoxNzY0MTY4NjExLCJleHAiOjE3NjQxNzIyMTF9.qJIUfzXHVIGA2pGPnCYouDJRC_Ql8FDtTkD6xvJij5g' \
             --header 'Content-Type: application/json' \
             --data '{
                "name":"New project with auth"
               }'
    ``` 
    **Powershell:**
    ```powershell
    $headers=@{}
    $headers.Add("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImdyb3VwcyI6WyJBRE1JTiIsIlVTRVIiXSwiaWF0IjoxNzY0MTY4NjExLCJleHAiOjE3NjQxNzIyMTF9.qJIUfzXHVIGA2pGPnCYouDJRC_Ql8FDtTkD6xvJij5g")
    $headers.Add("Content-Type", "application/json")
    $response = Invoke-RestMethod -Uri 'http://localhost:8080/project-tracker/resources/projects' -Method POST -Headers $headers -ContentType 'application/json' -Body '{
        "name":"New project with auth"
        }'
    $response | ConvertTo-Json
    ```
    >📌 Naturalmente, la cadena "Bearer" que se usa en estos comandos es el generado en el paso anterior, y es solo de ejemplo. Para tu aplicación, usa el generado en el paso anterior.

2.  **Flujo Web JSF:**

    * Usuario entra a `/index.xhtml`.
    * `SecurityConfig` detecta que requiere login -\> Redirige a `/login.xhtml`.\
    \
    ![](https://i.imgur.com/ZM2fEJk.png)
    \ 
    * Usuario rellena formulario -\> `LoginBean` autentica -\> Redirige a `/index.xhtml`.\
    \
    ![](https://i.imgur.com/g15GsZv.png)

¡Has implementado exitosamente una seguridad de nivel empresarial en Jakarta EE 11, cubriendo tanto APIs modernas como aplicaciones Web tradicionales\!