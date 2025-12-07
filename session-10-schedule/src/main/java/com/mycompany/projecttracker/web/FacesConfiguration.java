package com.mycompany.projecttracker.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;

/**
 * Configuración "Sin XML" para Jakarta Faces.
 * * @FacesConfig(version = FacesConfig.Version.JSF_2_3):
 * Esta anotación activa el soporte CDI completo para los beans de JSF.
 * Aunque estemos en Jakarta EE 11 (Faces 4.1), esta anotación sigue siendo
 * la señal estándar para decirle al servidor "¡Úsalo todo!".
 */
@FacesConfig
@ApplicationScoped
public class FacesConfiguration {
    // No se necesita código dentro. La anotación hace todo el trabajo.
}