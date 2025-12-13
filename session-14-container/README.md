# Sesión 14: Despliegue con Contenedores (Docker)

Hasta ahora, hemos ejecutado Payara instalándolo manualmente en nuestro sistema operativo. Eso está bien para desarrollo, pero en la nube (AWS, Azure, Google Cloud) usamos **Contenedores**.

Un contenedor empaqueta:

1.  El Sistema Operativo (mínimo).
2.  El Runtime de Java (JDK 21).
3.  El Servidor de Aplicaciones (Payara 7).
4.  Nuestra Aplicación (`.war`).
5.  Nuestra Configuración (Pools de conexión, colas, etc.).

**Objetivo:** Crear una imagen Docker de `ProjectTracker` que se configure automáticamente mediante Variables de Entorno.

-----

## 1\. Paso 1: Preparar la Configuración Automática (Post Boot)

En la Sesión 3, ejecutamos comandos `asadmin` manualmente en la terminal para crear el Pool de Base de Datos. En Docker, no podemos entrar a escribir comandos cada vez que arranca el contenedor.

Payara tiene una característica genial: **Scripts de Post-Arranque**.

Crea un archivo en la raíz de tu proyecto llamado [`post-boot-commands.asadmin`](post-boot-commands.asadmin).
Este script se ejecutará automáticamente cuando el contenedor arranque.

```bash
# 1. Crear el Pool de Conexiones
# Fíjate en la magia: ${ENV=...}
# Esto le dice a Payara: "No uses un valor fijo, lee la Variable de Entorno del sistema".
create-jdbc-connection-pool \
    --datasourceclassname org.h2.jdbcx.JdbcDataSource \
    --restype javax.sql.DataSource \
    --property "url=${ENV=DB_URL}:user=${ENV=DB_USER}:password=${ENV=DB_PASSWORD}" \
    ProjectTrackerPool

# 2. Crear el Recurso JNDI (El nombre que usa JPA en persistence.xml)
create-jdbc-resource \
    --connectionpoolid ProjectTrackerPool \
    jdbc/projectTracker

# 3. Configurar la Cola JMS (Sesión 9)
# (Si usaste @JMSDestinationDefinition en Java, esto es opcional, 
# pero hacerlo aquí es más "Infrastructure as Code")
```

**Nota:** Por defecto, Payara sustituirá `${ENV=DB_URL}` con el valor de la variable de entorno `DB_URL`. Si no existe, fallará (o podemos poner un valor por defecto, pero dejémoslo así para forzar la configuración).

-----

## 2\. Paso 2: El [`Dockerfile`](Dockerfile)

Este archivo es la "receta" para construir nuestra imagen.

Crea un archivo llamado [`Dockerfile`](Dockerfile) (sin extensión) en la raíz del proyecto:

```dockerfile
FROM payara/server-full:7.2025.2
# 1. Copiar la App
COPY target/project-tracker.war $DEPLOY_DIR

# 2. Copiar el Script de configuración
COPY post-boot-commands.asadmin $POSTBOOT_COMMANDS

# -----------------------------------------------------------
# 3. DESCARGA AUTOMÁTICA DEL DRIVER JDBC
# -----------------------------------------------------------

# Cambiamos a root para tener permisos de escritura y descarga
USER root

# Definimos la versión que queremos (para cambiarla fácil en el futuro)
ENV PG_VERSION=42.7.8

# Usamos ADD para bajar el JAR directo de Maven Central a la carpeta de librerías
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/${PG_VERSION}/postgresql-${PG_VERSION}.jar \
    /opt/payara/appserver/glassfish/domains/domain1/lib/postgresql.jar

# IMPORTANTE: Al bajarlo con ADD, el archivo queda como propiedad de 'root'.
# Payara corre como usuario 'payara', así que debemos cambiar el dueño
# para asegurarnos de que el servidor pueda leerlo sin problemas.
RUN chown payara:payara /opt/payara/appserver/glassfish/domains/domain1/lib/postgresql.jar

# Volvemos al usuario payara para que el contenedor corra de forma segura
USER payara

# -----------------------------------------------------------

EXPOSE 8080 4848
```

Este archivo  `Dockerfile`, como es una receta, tiene los pasos que deben hacerse para ejecutar la aplicación. El paso 3
es interesante, ya que descargará de Maven el .jar (Driver) respectivo para la base de datos. Mientras que en la sesión 3 lo hicimos manualmente,
aquí se hará de manera automática. 

En Docker, existe la instrucción `ADD`, que es capaz de descargar archivos desde una URL y colocarlos directamente en la imagen.

Todo esto se le llama "Infrastructure as Code".

-----

## 3\. Paso 3: Construir la Imagen

Primero, asegúrate de tener el `.war` actualizado:

```sh
mvn clean package
```

Ahora, construye la imagen Docker. Le pondremos el nombre (tag) `project-tracker:v1`.

```sh
docker build -t project-tracker:v1 .
```

*Esto descargará la imagen base de Payara (puede tardar un poco la primera vez) y copiará tu aplicación dentro.*

-----

## 4\. Paso 4: Ejecutar el Contenedor (Conectando las piezas)

Ahora vamos a ejecutar nuestra aplicación. Aquí es donde cumplimos el punto 4: **Conectar mediante variables de entorno.**

Pero antes, debemos recordar que vamos a conectarnos con la base datos.

Recordemos que la base de datos está ejecutándose en Docker usando [`docker-compose.yaml`](../docker/database/docker-compose.yaml).
Así que debemos **acceder a la red de ese contenedor**, y debemos **acceder al host** donde está base de datos (porque ya no será `localhost`).

Para conocer la red de ese contenedor, debemos revisar el nombre de la carpeta donde se encuentra el archivo `docker-compose.yaml`.
Para este ejemplo, lo tengo dentro de la carpeta `database`:

![](https://i.imgur.com/8ItVC9R.png)

Luego, desde una ventana de comandos, ejecutamos lo siguiente:

```shell
docker network ls
```

Y busca el que mismo nombre de la carpeta que tenga sufijo `_default`, en esta caso sería:

![](https://i.imgur.com/GAd87uf.png)

Por tanto, la red se llama: `database_default`.

Ahora, para obtener el host de la base de datos, bastará con revisar el archivo [`docker-compose.yaml`](../docker/database/docker-compose.yaml),
y es el nombre de contenedor:

![](https://i.imgur.com/Xgi0gmC.png)

Es decir: `project_tracker_db`

Con esos valores, ejecuta en tu terminal:

```sh
docker run -d \
  -p 8080:8080 \
  -p 4848:4848 \
  --name project-tracker-container \
  --net database_default \
  -e DB_URL="jdbc\:postgresql\://project_tracker_db/PROJECT_TRACKER" \
  -e DB_USER="PROJECT_TRACKER" \
  -e DB_PASSWORD="PROJECT_TRACKER" \
  project-tracker:v1
```
\
**Powershell:**
```powershell
docker run -d `
  -p "8080:8080" `
  -p "4848:4848" `
  --name project-tracker-container `
  --net database_default `
  -e DB_URL="jdbc\:postgresql\://project_tracker_db/PROJECT_TRACKER" `
  -e DB_USER="PROJECT_TRACKER" `
  -e DB_PASSWORD="PROJECT_TRACKER" `
  "project-tracker:v1"
```

**Desglose del comando:**

* `-d`: Detached (corre en segundo plano).
* `-p 8080:8080`: Conecta el puerto 8080 de tu máquina al 8080 del contenedor.
* `--name`: Le da un nombre fácil para administrarlo.
* `-e VAR=VAL`: **Aquí está la clave.** Pasamos las variables de entorno que el script `post-boot-commands.asadmin` está esperando.

-----

## 5\. Verificar el Despliegue

1.  **Ver logs:**
    Mira cómo arranca Payara y ejecuta tus scripts.

    ```sh
    docker logs -f project-tracker-container
    ```

    *Busca líneas que digan "Executing command: create-jdbc-connection-pool".*
    ![](https://i.imgur.com/VeDdVjn.png)

2.  **Probar la App:**
    Abre tu navegador en `http://localhost:8080/project-tracker/`.
    ¡Deberías ver tu aplicación funcionando\!
    ![](https://i.imgur.com/UO2WcxQ.png)

3.  **Probar la Consola de Administración (Opcional):**
    Abre `https://localhost:4848` (acepta la advertencia de seguridad SSL).
    El usuario por defecto suele ser `admin` (contraseña `admin`). Aquí podrás ver que tu Pool de conexiones `ProjectTrackerPool` fue creado exitosamente.
    ![](https://i.imgur.com/y7OMJac.png)

-----
 
¡Listo\! Has empaquetado tu aplicación Jakarta EE en una unidad inmutable. Ahora puedes llevar esa imagen `project-tracker:v1` a cualquier servidor, Kubernetes o nube, y funcionará exactamente igual.
 