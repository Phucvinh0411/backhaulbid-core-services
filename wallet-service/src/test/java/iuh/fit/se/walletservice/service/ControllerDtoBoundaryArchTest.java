package iuh.fit.se.walletservice.service;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardrail architecture test ensuring no @RestController method in wallet-service
 * returns a JPA @Entity directly or wrapped inside ResponseEntity/Collection/Page.
 */
class ControllerDtoBoundaryArchTest {

    @Test
    void restControllersNeverExposeJpaEntitiesInReturnTypes() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> violations = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("iuh.fit.se.walletservice")) {
            Class<?> controllerClass = Class.forName(candidate.getBeanClassName());
            for (Method method : controllerClass.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    inspectReturnType(controllerClass, method, method.getGenericReturnType(), violations);
                }
            }
        }

        assertThat(violations)
                .as("All @RestController endpoints must return mapped DTOs, never @Entity types")
                .isEmpty();
    }

    private static void inspectReturnType(
            Class<?> controller,
            Method method,
            Type type,
            List<String> violations) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isAnnotationPresent(Entity.class)) {
                violations.add(controller.getSimpleName() + "#" + method.getName() + " exposes @Entity " + clazz.getSimpleName());
            }
            if (clazz.isArray() && clazz.getComponentType().isAnnotationPresent(Entity.class)) {
                violations.add(controller.getSimpleName() + "#" + method.getName() + " exposes @Entity[] " + clazz.getComponentType().getSimpleName());
            }
        } else if (type instanceof ParameterizedType parameterizedType) {
            inspectReturnType(controller, method, parameterizedType.getRawType(), violations);
            for (Type arg : parameterizedType.getActualTypeArguments()) {
                inspectReturnType(controller, method, arg, violations);
            }
        }
    }
}
