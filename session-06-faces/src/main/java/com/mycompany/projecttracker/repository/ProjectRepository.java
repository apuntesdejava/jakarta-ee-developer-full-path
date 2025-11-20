package com.mycompany.projecttracker.repository;

import com.mycompany.projecttracker.entity.Project;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import java.util.List;

/**
 * Repositorio de Jakarta Data 1.0.
 *
 * @Repository: Indica a CDI que debe crear una implementación de esta interfaz.
 * extends BasicRepository<Project, Long>: Nos da métodos CRUD gratis
 * (save, findById, deleteById, findAll, etc.).
 */
@Repository
public interface ProjectRepository extends BasicRepository<Project, Long> {

    /**
     * ¡Método de Consulta Personalizado!
     * * Jakarta Data analiza el nombre del método: "findByStatus".
     * Sabe que debe buscar proyectos donde el campo 'status' coincida con el parámetro.
     * No hace falta escribir JPQL ni SQL.
     */
    List<Project> findByStatus(String status);

    // Ejemplo adicional: Buscar por nombre
    // Optional<Project> findByName(String name);
}