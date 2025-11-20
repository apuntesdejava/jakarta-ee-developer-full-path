# Sesión 6: Interfaz de Usuario con Jakarta Faces (JSF)

En esta sesión, dejaremos de lado por un momento las herramientas de API (como Postman o cURL) y construiremos una página web real que cualquier usuario pueda utilizar en su navegador.

Usaremos **Jakarta Faces 4.1 (anteriormente JavaServer Faces o JSF)**. Es un framework MVC (Modelo-Vista-Controlador) basado en componentes que se ejecuta en el servidor.

**¿Cómo funciona?**

1.  **Vista (.xhtml):** Escribimos etiquetas similares a HTML (`<h:inputText>`).
2.  **Controlador (Bean):** Escribimos una clase Java (`ProjectBean`) que conecta la vista con nuestros servicios.
3.  **Binding (EL 6.0):** Usamos `#{...}` para conectar ambos mundos mágicamente.

-----

## 1\. Paso 1: Crear el Controlador (Backing Bean)

Necesitamos una clase Java que sirva de intermediario entre la página web y nuestro `ProjectService`. En el mundo de Jakarta Faces, esto se llama un "Backing Bean".

Crea un nuevo paquete: `com.mycompany.projecttracker.web`.
Dentro, crea la clase [`ProjectBean.java`](src/main/java/com/mycompany/projecttracker/web/ProjectBean.java):

```java
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
```

**Nota sobre Java Records y JSF:**
Los Records son inmutables (no tienen métodos `set...`). JSF, por diseño, espera JavaBeans mutables (con getters y setters) para rellenar los formularios.

* *Solución Rápida (usada arriba):* Usar campos `String` normales (`formName`, `formDescription`) en el Bean para capturar la entrada del usuario, y luego construir el `Record` manualmente al guardar.

### Paso 1.5: Activador de Jakarta Faces (Configuración Java)

En lugar de un `web.xml`, creamos una clase vacía con una anotación. Esto le dice al servidor: "Esta es una aplicación Jakarta Faces moderna, activa todo el soporte de CDI".

Crea una clase en el paquete `com.mycompany.projecttracker.web` llamada [`FacesConfiguration.java`](src/main/java/com/mycompany/projecttracker/web/FacesConfiguration.java):


```java
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
```

-----

## 2\. Paso 2: Crear la Vista XHTML

Ahora creamos la página web. JSF usa archivos `.xhtml` (XML HTML).

Crea el archivo [`src/main/webapp/index.xhtml`](src/main/webapp/index.xhtml). (Si la carpeta `webapp` no existe, créala dentro de `src/main/`).

```xml
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:h="jakarta.faces.html"
      xmlns:f="jakarta.faces.core">
    
    <h:head>
        <title>Project Tracker - Jakarta EE 11</title>
        <!-- Un poco de CSS simple para que no se vea tan mal -->
        <style>
            body { font-family: sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }
            h1 { color: #2c3e50; }
            .table { width: 100%; border-collapse: collapse; margin-top: 20px; }
            .table th, .table td { border: 1px solid #ddd; padding: 8px; text-align: left; }
            .table th { background-color: #f2f2f2; }
            .form-group { margin-bottom: 15px; }
            label { display: block; margin-bottom: 5px; font-weight: bold; }
            input[type="text"], textarea { width: 100%; padding: 8px; box-sizing: border-box; }
            .btn { background-color: #e67e22; color: white; padding: 10px 15px; border: none; cursor: pointer; }
            .btn:hover { background-color: #d35400; }
            .error { color: red; }
        </style>
    </h:head>

    <h:body>
        <h1>📋 Project Tracker</h1>

        <!-- SECCIÓN 1: Formulario de Creación -->
        <div style="background-color: #fafafa; padding: 20px; border-radius: 8px;">
            <h3>Nuevo Proyecto</h3>
            
            <h:form id="projectForm">
                <!-- Mensajes de error globales o de validación -->
                <h:messages globalOnly="true" styleClass="error" />

                <div class="form-group">
                    <label>Nombre:</label>
                    <!-- value conecta con projectBean.formName -->
                    <h:inputText id="name" value="#{projectBean.formName}" required="true" requiredMessage="El nombre es obligatorio"/>
                    <h:message for="name" styleClass="error" />
                </div>

                <div class="form-group">
                    <label>Descripción:</label>
                    <h:inputTextarea id="desc" value="#{projectBean.formDescription}" rows="3" />
                </div>

                <!-- action llama al método del bean -->
                <h:commandButton value="Guardar Proyecto" action="#{projectBean.createProjectFromForm}" styleClass="btn" />
            </h:form>
        </div>

        <!-- SECCIÓN 2: Lista de Proyectos -->
        <h3>Proyectos Existentes</h3>
        
        <!-- h:dataTable es un bucle automático sobre la lista -->
        <h:dataTable value="#{projectBean.projects}" var="p" styleClass="table">
            
            <h:column>
                <f:facet name="header">ID</f:facet>
                #{p.id()}
            </h:column>

            <h:column>
                <f:facet name="header">Nombre</f:facet>
                #{p.name()}
            </h:column>

            <h:column>
                <f:facet name="header">Descripción</f:facet>
                #{p.description()}
            </h:column>

            <h:column>
                <f:facet name="header">Estado</f:facet>
                <span style="padding: 4px 8px; background-color: #e1f5fe; border-radius: 4px;">
                    #{p.status()}
                </span>
            </h:column>

        </h:dataTable>

    </h:body>
</html>
```

### Puntos Clave de Jakarta Faces 4.1

1.  **Namespaces:** Fíjate en `xmlns:h="jakarta.faces.html"`. En versiones antiguas (Java EE) era `xmlns:h="http://java.sun.com/jsf/html"`. ¡Asegúrate de usar el namespace de Jakarta\!
2.  **Jakarta Expression Language (EL 6.0):** La sintaxis `#{projectBean.formName}` es EL. Permite leer (`getFormName`) al renderizar y escribir (`setFormName`) al enviar el formulario.
3.  **Integración:** Al guardar, JSF llama al Bean, el Bean llama al Service (CDI), el Service llama al Repository (Data), y el Repository guarda en H2 (JPA). ¡Todo el stack de Jakarta EE en acción\!

-----

## 3\. Desplegar y Probar

1.  **Reconstruir:**

    ```sh
    mvn clean package
    ```

    Copia el `.war` a `autodeploy`.

2.  **Acceder a la Web:**
    Abre tu navegador y ve a:
    [**http://localhost:8080/project-tracker/index.xhtml**](http://localhost:8080/project-tracker/index.xhtml) (o simplemente `/project-tracker/` si el servidor sirve index por defecto).\
    \
    ![](https://i.imgur.com/P0KxIZZ.png)

3.  **Prueba la Interacción:**

    * Verás la lista de proyectos (los creados por `import.sql`).
    * Rellena el formulario con un nuevo nombre y descripción.
    * Haz clic en "Guardar Proyecto".
    * La página se recargará y verás tu nuevo proyecto en la tabla inferior.\
    ![](https://i.imgur.com/yhMGcC3.png)

4.  **Prueba la Validación:**

    * Intenta guardar un proyecto dejando el nombre vacío.
    * Verás el mensaje de error en rojo (`El nombre es obligatorio`) generado automáticamente por JSF gracias al atributo `required="true"`.
    ![](https://i.imgur.com/CxXLj2D.png) 

-----

¡Felicidades\! Has completado el círculo "Full Stack". Tienes una aplicación con Backend (API REST, JPA, Jakarta Data) y Frontend (Jakarta Faces), todo corriendo sobre la plataforma unificada de Jakarta EE 11.