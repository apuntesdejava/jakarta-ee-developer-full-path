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