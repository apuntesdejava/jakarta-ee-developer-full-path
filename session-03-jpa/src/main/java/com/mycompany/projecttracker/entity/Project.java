package com.mycompany.projecttracker.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Entidad JPA que representa la tabla 'Project' en la base de datos.
 * Especificación: Jakarta Persistence 3.2.
 */
@Entity
@Table(name = "PROJECT") // Opcional, pero buena práctica
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob // Large Object, para textos largos (mapea a CLOB o TEXT)
    private String description;

    @Column(length = 20)
    private String status;

    /**
     * Novedad JPA 3.2 (EE 11): Soporte nativo mejorado para java.time.
     * No necesitamos @Convert ni nada extra.
     */
    private LocalDate deadline;

    // --- Constructores ---

    // JPA requiere un constructor sin argumentos
    public Project() {
    }

    // --- Getters y Setters (necesarios para que JPA funcione) ---

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

    // (Opcional: puedes añadir equals() y hashCode() si lo deseas)
}