package com.mycompany.projecttracker.web;

import com.mycompany.projecttracker.model.ProjectDTO;
import com.mycompany.projecttracker.service.ProjectService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;

/**
 * Controlador para la vista JSF.
 * * @Named: Hace que esta clase sea visible en el archivo .xhtml como "projectBean".
 * @RequestScoped: El bean vive solo durante una petición HTTP. Se crea al pedir la página y muere al responder.
 */
@Named
@RequestScoped
public class ProjectBean {

    @Inject
    private ProjectService projectService;

    // Datos para la vista
    private List<ProjectDTO> projects;

    // Objeto para capturar los datos del formulario de "Nuevo Proyecto"
    // Inicializamos un record vacío (o con valores nulos)
    private ProjectDTO newProject = new ProjectDTO(null, null, null, null);

    /**
     * Se ejecuta inmediatamente después de que el Bean se crea e inyecta.
     * Cargamos la lista de proyectos aquí.
     */
    @PostConstruct
    public void init() {
        loadProjects();
    }

    private void loadProjects() {
        this.projects = projectService.findAll();
    }

    /**
     * Acción ejecutada por el botón "Guardar".
     */
    public String createProject() {
        // 1. Llamamos al servicio para guardar
        projectService.create(newProject);

        // 2. Limpiamos el formulario
        this.newProject = new ProjectDTO(null, null, null, null);

        // 3. Recargamos la lista para que aparezca el nuevo
        loadProjects();

        // 4. Navegación: devolver null o una cadena vacía significa "quédate en la misma página"
        return "";
    }

    // --- Getters y Setters (Necesarios para que JSF lea/escriba los datos) ---

    public List<ProjectDTO> getProjects() {
        return projects;
    }

    public ProjectDTO getNewProject() {
        return newProject;
    }

    // Importante: Aunque ProjectDTO es inmutable (record), JSF necesita poder "setear"
    // el objeto completo o sus propiedades. Como es un record, usaremos un truco en la vista
    // o, para este tutorial simple, aceptaremos que JSF sobreescriba la referencia 'newProject'.
    public void setNewProject(ProjectDTO newProject) {
        this.newProject = newProject;
    }

    /**
     * Un pequeño truco para JSF y Records:
     * JSF intenta llamar a `setNombre()` en el objeto, pero los Records no tienen setters.
     * * ESTRATEGIA: Para no complicar este tutorial con conversores avanzados,
     * vamos a agregar propiedades "mutables" temporales aquí en el Bean
     * solo para el formulario, y luego construir el Record.
     */
    private String formName;
    private String formDescription;

    public String getFormName() { return formName; }
    public void setFormName(String formName) { this.formName = formName; }
    public String getFormDescription() { return formDescription; }
    public void setFormDescription(String formDescription) { this.formDescription = formDescription; }

    // Versión actualizada del método createProject usando las variables temporales
    public String createProjectFromForm() {
        ProjectDTO dto = new ProjectDTO(null, formName, formDescription, null);
        projectService.create(dto);

        // Limpiar formulario
        formName = "";
        formDescription = "";

        loadProjects();
        return "";
    }
}