# Sesión 15: De Monolito a Microservicio (Payara Micro & Core Profile)

En esta sesión final, adoptaremos la arquitectura **Cloud Native**.

En lugar de tener un servidor instalado donde desplegamos aplicaciones (`.war`), empaquetaremos el servidor **dentro** de nuestra aplicación. El resultado es un archivo `.jar` ejecutable que puedes correr en cualquier lugar que tenga Java, sin instalar nada más.

**Especificaciones a cubrir:**

* [**Jakarta EE Core Profile:**](https://jakarta.ee/specifications/coreprofile/11/) Conceptos de perfiles ligeros.
* [**Payara Micro:**](https://docs.payara.fish/community/docs/Technical%20Documentation/Payara%20Micro%20Documentation/Overview.html) El runtime optimizado para contenedores.

-----

## 1\. Paso 1: Entendiendo los Perfiles (Full vs Web vs Core)

Antes de escribir código, es vital entender qué estamos recortando. Jakarta EE 11 se divide en perfiles para ajustarse a tus necesidades:

| Perfil            | Descripción                         | Ideal para...                                   | Componentes Clave                         |
|:------------------|:------------------------------------|:------------------------------------------------|:------------------------------------------|
| **Full Platform** | La suite completa.                  | Monolitos Legacy, Sistemas Bancarios complejos. | EJB Remotos, SOAP, JMS, Batch, JSF.       |
| **Web Profile**   | Optimizado para la web moderna.     | La mayoría de Apps Web (Como `ProjectTracker`). | JSF, JPA, EJB Lite, JAX-RS, CDI.          |
| **Core Profile**  | **Novedad EE 10/11.** Ultra ligero. | **Microservicios**, Sidecars, Serverless.       | REST, CDI Lite, JSON-B. **(Sin UI/JSF)**. |

**¿Dónde encaja Payara Micro?**
Payara Micro es increíblemente versátil. Aunque está diseñado para microservicios, **soporta Web Profile**. Esto significa que nuestra app `ProjectTracker` (que usa JSF) funcionará perfectamente en Payara Micro, pero consumiendo muchos menos recursos que el servidor completo.

> Pero, hay algo importante que considerar: Ya que la idea de usar MicroProfile es hacer un servicio pequeño, debemos considerar solo lo básico. Así que, todo lo que es JMS se tendrá que descartar de la aplicación:\
  - Borrar los archivos:
    - `src/main/java/com/mycompany/projecttracker/config/JmsConfiguration.java`
    - `src/main/java/com/mycompany/projecttracker/service/messaging/NotificationMDB.java`
  - Borrar la referencia en la clase `com.mycompany.projecttracker.service.ProjectService`:
    ```java
    // @Inject
    // private JMSContext jmsContext;

    // @Resource(lookup = "java:app/jms/TaskQueue")
    // private Queue taskQueue;

    public TaskDTO createTask(Long projectId, TaskDTO taskDto) {
        // ... (lógica de guardado y persistencia) ...
        
        // --- BORRAR ESTO ---
        // String messagePayload = project.getId() + ":" + newTask.getId();
        // jmsContext.createProducer().send(taskQueue, messagePayload);
        // -------------------
        
        return new TaskDTO(...);
    }
    ```


-----

## 2\. Paso 2: Generar el Uber-JAR

Vamos a configurar Maven para que cree un solo archivo `.jar` que contenga:

1.  Nuestra aplicación (`.war`).
2.  El servidor Payara Micro completo.

Abre tu `pom.xml` y añade este plugin en la sección `<build><plugins>`:

```xml
<plugin>
    <groupId>fish.payara.maven.plugins</groupId>
    <artifactId>payara-micro-maven-plugin</artifactId>
    <version>2.4</version>
    <configuration>
        <payaraVersion>7.2025.2</payaraVersion>
        <deployWar>true</deployWar>
        <contextRoot>/</contextRoot>
        <postBootCommandFile>post-boot-commands.asadmin</postBootCommandFile>
    </configuration>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>bundle</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Ejecuta la construcción:

```sh
mvn clean package
```

Ahora mira en la carpeta `target/`. Verás un archivo nuevo (aprox. 80MB): `project-tracker-microbundle.jar`.
**¡Eso es todo tu servidor y tu aplicación en un solo archivo\!**

Necesitamos, además, descargar el .jar del Driver para PostgreSQL. Lo haremos vía Script

**Linux / MacOS**
```bash
curl -o target/postgresql.jar "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar"
```

**PowerShell**
```powershell
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar" -OutFile "target/postgresql.jar"
```

El .jar se ubicará en la carpeta `target`, y desde allí lo incluiremos en la ejecución del .jar creado `project-tracker-microbundle.jar`.

Adicionalmente, debemos establecer las variables de entorno para ejecutar bien nuestra aplicación. Recordemos que vamos a seguir usando el archivo `post-boot-commands.asadmin`, y allí están los comandos para crear la conexión a la base de datos.
En ese mismo archivo se necesita las variables de entorno. Considerando que la base de datos sigue en `localhost`, usaremos la siguiente secuencia de comandos:


**Linux / MacOS**
```bash
export DB_URL="jdbc\:postgresql\://localhost/PROJECT_TRACKER"
export DB_USER="PROJECT_TRACKER"
export DB_PASSWORD="PROJECT_TRACKER"
```

**PowerShell**
```powershell
$env:DB_URL="jdbc\:postgresql\://localhost/PROJECT_TRACKER"
$env:DB_USER="PROJECT_TRACKER"
$env:DB_PASSWORD="PROJECT_TRACKER"
```

-----

## 3\. Paso 3: Ejecutar "Sin Manos" (CLI)

Olvídate de `asadmin start-domain`. Olvídate de instalar Payara en `C:\`.

Abre tu terminal y ejecuta:

**Linux / MacOS**
```bash
java -jar target/project-tracker-microbundle.jar \
    --port 8081 \
    --postbootcommandfile post-boot-commands.asadmin \
    --addLibs target/postgresql.jar
```

**PowerShell**
```powershell
java -jar target/project-tracker-microbundle.jar `
    --port 8081 `
    --postbootcommandfile post-boot-commands.asadmin `
    --addLibs target/postgresql.jar
```

*Nota: Usamos el puerto 8081 para no chocar si tienes el contenedor de la Sesión 14 corriendo.*

Verás que arranca rapidísimo.

* Prueba: `http://localhost:8081/index.xhtml`
* ¡Funciona igual, pero es portátil\! Puedes enviarle este `.jar` a un amigo y le funcionará inmediatamente.

-----

## 4\. Paso 4: Dockerfile Optimizado (Cloud Native)

Para la nube, no usaremos el `Uber-jar` (porque Docker ya es un empaquetado), sino que usaremos la imagen base de **Payara Micro** y le agregaremos nuestro WAR y el driver de Postgres.

Esta imagen es mucho más pequeña y segura que la `server-full` de la Sesión 14.

Crea un archivo [`Dockerfile.micro`](Dockerfile.micro):

```dockerfile
# 1. Usamos la imagen oficial de Payara Micro (JDK 21)
FROM payara/micro:7.2025.2

# 2. Copiamos el WAR
COPY target/project-tracker.war $DEPLOY_DIR

# 3. Descargamos el Driver de Postgres
USER root
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar \
    /opt/payara/libs/postgresql.jar
RUN chown payara:payara /opt/payara/libs/postgresql.jar
USER payara

# 4. CORRECCIÓN: Copiamos el script a una ruta absoluta y conocida
# Usamos /opt/payara/ porque es el HOME del usuario por defecto
COPY post-boot-commands.asadmin /opt/payara/post-boot.asadmin

# 5. Comando de arranque
# Sobreescribimos el comando por defecto para añadir nuestros parámetros.
# Es importante volver a pasar --deploymentDir para que se despliegue la aplicación.
CMD ["--deploymentDir", "/opt/payara/deployments", "--postbootcommandfile", "/opt/payara/post-boot.asadmin", "--addLibs", "/opt/payara/libs/postgresql.jar", "--contextroot", "/"]

```

-----

## 5\. Paso 5: Construir y Desplegar en la Red

Vamos a construir esta versión "Micro" y conectarla a tu base de datos existente (la que tienes en Docker Compose).

1.  **Construir:**

    ```sh
    docker build -t project-tracker-micro -f Dockerfile.micro .
    ```

2.  **Correr (Conectando a tu red `database_default`):**
    *(Recuerda verificar el nombre de tu red con `docker network ls`)*

    **Linux / macOS**
    ```sh
    docker run -d \
      -p 8082:8080 \
      --name project-tracker-micro-app \
      --net database_default \
      -e DB_URL="jdbc\:postgresql\://project_tracker_db/PROJECT_TRACKER" \
      -e DB_USER="PROJECT_TRACKER" \
      -e DB_PASSWORD="PROJECT_TRACKER" \
      project-tracker-micro
    ```

    **Powershell**
    ```powershell
    docker run -d `
      -p 8082:8080 `
      --name project-tracker-micro-app `
      --net database_default `
      -e DB_URL="jdbc\:postgresql\://project_tracker_db/PROJECT_TRACKER" `
      -e DB_USER="PROJECT_TRACKER" `
      -e DB_PASSWORD="PROJECT_TRACKER" `
      project-tracker-micro
    ```

**Resultado:**
Ahora tienes tu aplicación corriendo en el puerto **8082**, consumiendo significativamente menos memoria RAM y espacio en disco que la versión "Server Full", conectada a tu misma base de datos PostgreSQL.

![](https://i.imgur.com/4T4Snxp.png)

-----

## 🎉 ¡FIN DEL CURSO\!

Has completado el camino del desarrollador **Jakarta EE 11 Profesional**.

**¿Qué has logrado?**

1.  **Backend Sólido:** API REST, JPA, Jakarta Data, Validation.
2.  **Frontend Dinámico:** JSF + WebSockets para tiempo real.
3.  **Enterprise Grade:** Seguridad Híbrida (JWT/Web), Mensajería (JMS), Batch Processing, Jobs Programados.
4.  **DevOps Ready:** Observabilidad (Health/Metrics) y Dockerización completa (Server y Micro).

¡Ahora tienes un proyecto de portafolio de nivel senior listo para mostrar al mundo\! Muchas gracias por seguir este tutorial.  🚀