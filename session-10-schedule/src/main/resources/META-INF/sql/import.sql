-- Inserta Proyectos
-- (No especificamos ID, dejamos que IDENTITY lo genere)
INSERT INTO PROJECT (NAME, DESCRIPTION, STATUS, DEADLINE, CREATEDBY, CREATEDAT) VALUES ('Sitio Web Corporativo', 'Desarrollo del nuevo sitio web v2', 'Activo', '2025-12-31', 'import_user', '2025-01-01');

INSERT INTO PROJECT (NAME, DESCRIPTION, STATUS, DEADLINE, CREATEDBY, CREATEDAT) VALUES ('App Móvil (ProjectTracker)', 'Lanzamiento de la app nativa', 'Planificado', '2026-03-15', 'import_user', '2025-01-10');

-- Inserta Tareas y las vincula a los proyectos
-- Asumimos que los proyectos anteriores tendrán ID 1 y 2
INSERT INTO TASK (TITLE, STATUS, PROJECT_ID) VALUES ('Diseñar Homepage', 'Completada', 1), ('Desarrollar formulario de contacto', 'En Progreso', 1), ('Definir API de Tareas', 'Completada', 2), ('Testear login de usuario', 'Pendiente', 2);

-- Insertar una tarea que ya está completada, con fecha antigua (simulada)
-- Asumiendo que el Proyecto 1 existe.
INSERT INTO TASK (TITLE, STATUS, PROJECT_ID, CREATEDBY, CREATEDAT) VALUES ('Tarea Vieja de Prueba', 'Completada', 1, 'admin', '2020-01-01');