package com.mycompany.resource;

import com.mycompany.projecttracker.service.GreetingService;
import com.mycompany.projecttracker.service.qualifier.GreetingType;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("hello")
public class HelloWorldResource {

    @Inject
    @GreetingType("default")
    GreetingService greetingService;

    @Inject
    @GreetingType("mock")
    GreetingService greetingMockService;

    @GET
    public Response hello(@QueryParam("name") @DefaultValue("world") String name) {
        return Response
            .ok(greetingService.greet(name))
            .build();
    }

    @GET
    @Path("mock")
    public Response helloMock(@QueryParam("name") @DefaultValue("world") String name) {
        return Response
            .ok(greetingMockService.greet(name))
            .build();
    }


}