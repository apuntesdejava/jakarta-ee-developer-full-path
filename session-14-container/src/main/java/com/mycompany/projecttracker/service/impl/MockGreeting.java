package com.mycompany.projecttracker.service.impl;

import com.mycompany.projecttracker.service.GreetingService;
import com.mycompany.projecttracker.service.qualifier.GreetingType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@GreetingType("mock")
public class MockGreeting implements GreetingService {
    @Override
    public String greet(String name) {
        return "Modo de prueba: Hola " + name;
    }
}
