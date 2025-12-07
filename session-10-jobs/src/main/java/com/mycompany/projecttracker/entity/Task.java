package com.mycompany.projecttracker.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "TASK")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROJECT_ID", nullable = false) // Define la columna de la clave foránea
    private Project project;

    // --- NUEVO: Auditoría para saber cuándo se creó ---
    @Embedded
    private AuditInfo auditInfo;

    // Constructor, Getters y Setters...

    public Task() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    // --- Getter y Setter ---
    public AuditInfo getAuditInfo() { return auditInfo; }
    public void setAuditInfo(AuditInfo auditInfo) { this.auditInfo = auditInfo; }
}