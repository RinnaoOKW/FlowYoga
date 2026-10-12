package com.flowyoga.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator flowYogaRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("flowyoga-user", route -> route.path("/api/user/**").uri("lb://flowyoga-user"))
                .route("flowyoga-course", route -> route.path("/api/course/**").uri("lb://flowyoga-course"))
                .route("flowyoga-member", route -> route.path("/api/member/**").uri("lb://flowyoga-member"))
                .build();
    }
}
