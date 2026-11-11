package com.danielhw99.common.web;

import java.beans.PropertyEditorSupport;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import com.danielhw99.common.api.ApiEnum;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@ControllerAdvice
public class ApiEnumBinding {

    @InitBinder
    public void bindApiEnums(WebDataBinder binder, HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod method)) {
            return;
        }
        Set<ResolvableType> visited = new HashSet<>();
        for (MethodParameter parameter : method.getMethodParameters()) {
            registerApiEnums(binder, ResolvableType.forMethodParameter(parameter), visited);
        }
    }

    private void registerApiEnums(WebDataBinder binder, ResolvableType source, Set<ResolvableType> visited) {
        Class<?> type = source.resolve();
        if (type == null || !visited.add(source)) {
            return;
        }
        if (type.isEnum() && ApiEnum.class.isAssignableFrom(type)) {
            // A custom editor prevents Spring's enum-name fallback after conversion failure.
            binder.registerCustomEditor(type, new PropertyEditorSupport() {
                @Override
                public void setAsText(String text) {
                    setValue(ApiEnum.fromValue(type.asSubclass(ApiEnum.class), text));
                }
            });
            return;
        }
        for (ResolvableType generic : source.getGenerics()) {
            registerApiEnums(binder, generic, visited);
        }
        if (type.isArray()) {
            registerApiEnums(binder, source.getComponentType(), visited);
        } else if (!type.getPackageName().startsWith("java.")
                && !type.getPackageName().startsWith("jakarta.")
                && !type.getPackageName().startsWith("org.springframework.")) {
            ReflectionUtils.doWithFields(type, field -> {
                if (!Modifier.isStatic(field.getModifiers())) {
                    registerApiEnums(binder, ResolvableType.forField(field, source), visited);
                }
            });
        }
    }
}
