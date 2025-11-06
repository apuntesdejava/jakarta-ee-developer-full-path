# Sesión 0: Configuración del Entorno y "Hola Mundo"

¡Bienvenido al tutorial de ProjectTracker! En esta primera sesión, no escribiremos mucha lógica de negocio, pero haremos el trabajo más importante: configurar nuestro entorno de desarrollo y asegurarnos de que podemos desplegar una aplicación Jakarta EE 11 con éxito.

Objetivo de esta sesión:

* Configurar las herramientas necesarias (Java 21, Payara 7). 
* Generar el esqueleto del proyecto con Payara Starter. 
* Crear un endpoint "Hola Mundo" para verificar la instalación.

---

## 1.Prerrequisitos

Antes de empezar, asegúrate de tener instalado lo siguiente:

* Java 21 (o superior): Jakarta EE 11 funciona con Java 17+, pero usaremos Java 21 para aprovechar características modernas, como los Virtual Threads, más adelante.
* Apache Maven: Para gestionar las dependencias y la construcción del proyecto.
* Un IDE: Se recomienda [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community o Ultimate) o [VS Code](https://code.visualstudio.com/) con el "Extension Pack for Java".

## 2. Instalación de Payara 7 (Soporte para Jakarta EE 11)