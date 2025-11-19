# Sesión 0: Configuración del Entorno y "Hola Mundo"

¡Bienvenido al tutorial de ProjectTracker! En esta primera sesión, no escribiremos mucha lógica de negocio, pero haremos el trabajo más importante: configurar nuestro entorno de desarrollo y asegurarnos de que podemos desplegar una aplicación Jakarta EE 11 con éxito.

Objetivo de esta sesión:

* Configurar las herramientas necesarias (Java 21, Payara 7). 
* Generar el esqueleto del proyecto con Payara Starter. 
* Crear un endpoint "Hola Mundo" para verificar la instalación.

---

## 1.Prerrequisitos

Antes de empezar, asegúrate de tener instalado lo siguiente:

* Java 21 (o superior): Jakarta EE 11 funciona con Java 17+, pero usaremos Java 21 para aprovechar características modernas, como los "Virtual Threads", más adelante.
* Apache Maven: Para gestionar las dependencias y la construcción del proyecto.
* Un IDE: Se recomienda [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community o Ultimate) o [VS Code](https://code.visualstudio.com/) con la "Extension Pack for Java".

## 2. Instalación de Payara 7 (Soporte para Jakarta EE 11)

Usaremos Payara 7 Community Edition, que es la implementación compatible con Jakarta EE 11.

1. **Descarga**: Ve a la [página de descargas de Payara](https://payara.fish/downloads/payara-platform-community-edition/).
2. **Selecciona**: Elige la versión más reciente de Payara 7 Community.
3. **Descomprime**: Descomprime el archivo ZIP en una ubicación de tu elección (ej. `C:\payara7` o `~/payara7`).

## 3. Generar el Proyecto con Payara Starter

Para evitar escribir el `pom.xml` de configuración desde cero, usaremos la herramienta oficial: **Payara Starter**.

1. Ve a [start.payara.fish](https://start.payara.fish/).
2. Rellena el formulario con las siguientes opciones:
   - Project Description:
     - Build: Maven
     - Group ID: `com.mycompany` (o el tuyo personal)
     - Artifact ID: `project-tracker`
     - Version: `0.1-SNAPSHOT`
   - Jakarta EE    
     - Jakarta EE Version: `Jakarta EE 11`
     - Profile: `Web Profile` (Es un buen punto de partida. Añadiremos más dependencias después).
   - Payara Platform
     - Payara Server
     - Payara Platform version: `7.2025.1`
   - Project Configuration
     - Package: (el mismo de su Group ID)
     - No incluir Test
     - Java Version: `Java 21`
   - Microprofile
     - No seleccionar nada 
3. Haz clic en Generate y descarga el archivo ZIP.
4. Descomprímelo y ábrelo en tu IDE.

**Análisis del `pom.xml`**

Lo más importante que ha generado esta herramienta está en tu [`pom.xml`](pom.xml):

```xml

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>21</maven.compiler.release>
        <jakartaee-api.version>11.0.0</jakartaee-api.version>
        <payara.version>7.2025.1</payara.version>
        <payara.home>payara6</payara.home>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>fish.payara.api</groupId>
                <artifactId>payara-bom</artifactId>
                <version>${payara.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>jakarta.platform</groupId>
            <artifactId>jakarta.jakartaee-web-api</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.eclipse.microprofile</groupId>
            <artifactId>microprofile</artifactId>
            <scope>provided</scope>
            <type>pom</type>
        </dependency>
        <dependency>
            <groupId>fish.payara.api</groupId>
            <artifactId>payara-api</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
```

**Nota clave**: La dependencia `jakarta.jakartaee-web-api` tiene `<scope>provided</scope>`. Esto significa que Maven espera que el servidor de aplicaciones (Payara) ya "provea" estas librerías. No las incluimos en nuestro archivo `.war`, lo que lo hace extremadamente ligero.

## 4. El "Hola Mundo" (API REST)

El generador de Payara ya crea las clases necesarias para comenzar un ejemplo API REST. Examinaremos las siguientes

### Activador de JAX-RS

La primera clase le dice a Jakarta EE "¡Quiero activar la API REST en esta aplicación!". Esta clase es [`RestConfiguration.java`](src/main/java/com/mycompany/resource/RestConfiguration.java)

```java
package fish.payara.resource;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Configures RESTful Web Services for the application.
 */
@ApplicationPath("resources")
public class RestConfiguration extends Application {
    
}

```

- `@ApplicationPath("/resources")`: Esto establece que todos nuestros endpoints REST vivirán bajo la URL `.../resources/....`

### El Endpoint "Hello"

La otra clase es [`HelloWorldResource.java`](src/main/java/com/mycompany/resource/HelloWorldResource.java) que responderá a las peticiones.

```java
package fish.payara.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("hello")
public class HelloWorldResource {


    @GET
    public Response hello(@QueryParam("name") String name) {
        if ((name == null) || name.trim().isEmpty()) {
            name = "world";
        }
        return Response
                .ok(name)
                .build();
    }
    

}
```

* `@Path("/hello")`: Esta clase manejará las peticiones a `.../resources/hello`.
* `@GET`: Este método manejará las peticiones HTTP GET.

## 5. Desplegar y Probar

¡Es hora de ver el resultado!

1. Construir el Proyecto: Abre una terminal en la raíz de tu proyecto y ejecuta: \
   
   **Windows**:
   ```powershell
   .\mvnw clean package
   ```
   
   **Linux/macOS**:
   ```shell
   ./mvnw clean package
   ```
   
   Esto generará el archivo `target/project-tracker-1.0-SNAPSHOT.war`. Si deseamos que tenga un nombre más corto, como `project-tracker`, creemos la siguiente línea en la sección `<build>` del archivo `pom.xml`.
   
   ```xml
   <finalName>project-tracker</finalName>
   ``` 
   
   Ejecutemos nuevamente el comando `mvnw clean package` y tendremos el archivo `target/project-tracker.war`

2. Iniciar Payara Server: Ve a la carpeta donde descomprimiste Payara 7 y ejecuta:

    ```shell
    # Para Windows
    cd C:\payara7\bin
    asadmin start-domain
    
    # Para macOS/Linux
    cd ~/payara7/bin
    ./asadmin start-domain
   ```

3. **Desplegar la Aplicación (La forma fácil)**: Busca la carpeta `autodeploy` de Payara.\
   Usualmente está en: `[PAYARA_HOME]/glassfish/domains/domain1/autodeploy/` \
   Copia tu archivo `target/project-tracker.war` dentro de esa carpeta `autodeploy`.\
   Payara detectará automáticamente el archivo y lo desplegará.\
   \
   Una manera de saber si desplegó correctamente o no, es mirando el log que se encuentra aquí: `[PAYARA_HOME]/glassfish/domains/domain1/logs/server.log` 
   
   \
   El contenido sería como este:
   ![](https://i.imgur.com/UZVIRie.png) 

4. **Verificar**: Abre tu navegador web o un cliente API (como Postman, cURL) y visita: \
   \
   http://localhost:8080/project-tracker/resources/hello

   \
   _Analicemos esa URL:_
   * `localhost:8080`: Tu servidor Payara.
   * `/project-tracker`: El nombre de tu archivo `.war` (definido en el `pom.xml` como `finalName`).
   * `/resources`: El prefijo que está en [`RestConfiguration.java`](src/main/java/com/mycompany/resource/RestConfiguration.java).
   * `/hello`: La ruta que está en [`HelloWorldResource.java`](src/main/java/com/mycompany/resource/HelloWorldResource.java).

**Resultado esperado**

Deberías ver algo así:

En un navegador:\
![](https://i.imgur.com/58qrdMl.png)


-----

¡Felicidades! Has configurado con éxito un servidor Payara 7 y has desplegado tu primera aplicación Jakarta EE 11.

En la próxima sesión, reemplazaremos este endpoint de prueba por uno real para gestionar nuestros "Proyectos", introduciendo Jakarta REST (JAX-RS) y Jakarta JSON Binding (JSON-B).