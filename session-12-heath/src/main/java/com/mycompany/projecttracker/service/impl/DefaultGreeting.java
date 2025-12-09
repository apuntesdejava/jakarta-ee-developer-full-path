package com.mycompany.projecttracker.service.impl;

import com.mycompany.projecttracker.service.GreetingService;
import com.mycompany.projecttracker.service.qualifier.GreetingType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@GreetingType("default")
public class DefaultGreeting implements GreetingService {
    @Override
    public String greet(String name) {
        return "Hello %s !".formatted(name);
    }
}
