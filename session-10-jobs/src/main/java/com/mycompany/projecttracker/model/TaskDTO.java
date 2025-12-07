package com.mycompany.projecttracker.model;

import jakarta.validation.constraints.NotNull;

public record TaskDTO(
    Long id,
    @NotNull String title,
    String status
) {}