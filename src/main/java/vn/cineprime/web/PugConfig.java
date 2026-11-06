package vn.cineprime.web;

import de.neuland.pug4j.PugEngine;
import de.neuland.pug4j.spring.boot.PugAutoConfiguration;
import de.neuland.pug4j.spring.boot.PugProperties;
import de.neuland.pug4j.spring.view.PugViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PugConfig {
    // Boot's existing bean named viewResolver causes spring-pug4j's name condition to back off.
    // Explicit registration keeps the official loader/engine and all spring.pug4j properties.
    @Bean public PugViewResolver cineprimePugResolver(PugEngine engine,PugProperties properties){
        PugViewResolver resolver=new PugAutoConfiguration().pugViewResolver(engine,properties);
        resolver.setOrder(0);return resolver;
    }
}
