package com.nimokids.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Safety net for the admin area. The URL rule only checks that the caller is a logged-in admin; WHICH admin may do
 * WHAT is declared on every handler with @PreAuthorize. A handler without one would be open to every admin role,
 * so this test fails the build when somebody forgets it.
 */
class AdminControllersSecurityTest {

    @Test
    void everyHandlerOfAnAdminControllerDeclaresAPreAuthorizeRule() throws Exception {
        List<String> unprotected = new ArrayList<>();
        int checkedHandlers = 0;

        for (Class<?> controller : restControllers()) {
            if (!mapsToAdminArea(controller)) {
                continue;
            }
            boolean classLevelRule = hasRule(controller);
            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                checkedHandlers++;
                if (!classLevelRule && !hasRule(method)) {
                    unprotected.add(controller.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(checkedHandlers).as("at least the sample AdminTopicController must be found").isPositive();
        assertThat(unprotected).as("admin handlers without @PreAuthorize").isEmpty();
    }

    @Test
    void controllersUnderTheAdminUrlLiveInTheAdminPackage() throws Exception {
        List<String> misplaced = new ArrayList<>();
        for (Class<?> controller : restControllers()) {
            if (mapsToAdminArea(controller) && !controller.getPackageName().endsWith(".controller.admin")) {
                misplaced.add(controller.getName());
            }
        }
        assertThat(misplaced).isEmpty();
    }

    @Test
    void playerControllersNeverUseRoleRules() throws Exception {
        List<String> mixedUp = new ArrayList<>();
        for (Class<?> controller : restControllers()) {
            if (!mapsToAdminArea(controller) && (hasRule(controller) || Arrays.stream(controller.getDeclaredMethods()).anyMatch(this::hasRule))) {
                mixedUp.add(controller.getSimpleName());
            }
        }
        assertThat(mixedUp).as("public player controllers must stay anonymous").isEmpty();
    }

    private List<Class<?>> restControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.nimokids.controller")) {
            controllers.add(Class.forName(definition.getBeanClassName()));
        }
        return controllers;
    }

    private boolean mapsToAdminArea(Class<?> controller) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        if (mapping != null && Arrays.stream(mapping.path().length > 0 ? mapping.path() : mapping.value())
                .anyMatch(path -> path.startsWith(SecurityPaths.ADMIN_BASE))) {
            return true;
        }
        return Arrays.stream(controller.getDeclaredMethods())
                .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
                .anyMatch(methodMapping -> methodMapping != null
                        && Arrays.stream(methodMapping.path().length > 0 ? methodMapping.path() : methodMapping.value())
                        .anyMatch(path -> path.startsWith(SecurityPaths.ADMIN_BASE)));
    }

    private boolean hasRule(java.lang.reflect.AnnotatedElement element) {
        return AnnotatedElementUtils.hasAnnotation(element, PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(element, PostAuthorize.class);
    }
}
