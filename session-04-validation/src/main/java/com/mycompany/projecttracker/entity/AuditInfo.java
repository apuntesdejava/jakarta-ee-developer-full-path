package com.mycompany.projecttracker.entity;

import jakarta.persistence.Embeddable;
import java.time.LocalDate;

/**
 * Novedad de Jakarta EE 11 / JPA 3.2:
 * Podemos usar un Java Record (inmutable, conciso)
 * como un componente "incrustable" en nuestras entidades.
 */
@Embeddable
public record AuditInfo(
    String createdBy,
    LocalDate createdAt
) {
    // Constructor sin argumentos requerido por JPA
    // para un record @Embeddable (un poco peculiar, pero necesario)
    public AuditInfo() {
        this(null, null);
    }
}