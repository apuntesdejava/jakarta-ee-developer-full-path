package com.mycompany.projecttracker.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa la tabla 'Project' en la base de datos.
 * Especificación: Jakarta Persistence 3.2.
 */
@Entity
@Table(name = "PROJECT")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob
    private String description;

    @Column(length = 20)
    private String status;

    // Soporte nativo de java.time (JPA 3.2)
    private LocalDate deadline;

    /**
     * ¡Aquí usamos nuestro Record @Embeddable!
     * JPA creará las columnas 'CREATEDBY' y 'CREATEDAT' en la tabla 'PROJECT'.
     */
    @Embedded
    private AuditInfo auditInfo;

    /**
     * Esta es la relación "Inversa" (OneToMany).
     * Un Proyecto (One) tiene Muchas Tareas (Many).
     *
     * mappedBy = "project": Le dice a JPA "Esta relación ya está
     * definida en la clase Task, en el campo 'project'".
     *
     * cascade = CascadeType.ALL: Si guardo un Proyecto, también
     * guarda sus tareas. Si borro un Proyecto, borra sus tareas.
     *
     * orphanRemoval = true: Si quito una tarea de esta lista,
     * bórrala de la BBDD.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

    // Constructor, Getters y Setters...

    public Project() {
    }

    // --- Métodos de ayuda para la relación (buena práctica) ---
    public void addTask(Task task) {
        tasks.add(task);
        task.setProject(this);
    }
    public void removeTask(Task task) {
        tasks.remove(task);
        task.setProject(null);
    }

    // --- Getters y Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public AuditInfo getAuditInfo() { return auditInfo; }
    public void setAuditInfo(AuditInfo auditInfo) { this.auditInfo = auditInfo; }
    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) { this.tasks = tasks; }
}