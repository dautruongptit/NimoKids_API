package com.nimokids.config;

import com.nimokids.exception.AnonymousPlayerRequiredException;
import com.nimokids.util.RequestContext;
import com.nimokids.validation.AnonymousId;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class AnonymousIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AnonymousId.class) && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader(RequestContext.ANONYMOUS_ID_HEADER);
        if (header == null || header.isBlank()) {
            throw new AnonymousPlayerRequiredException();
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException ex) {
            throw new AnonymousPlayerRequiredException();
        }
    }
}
