package com.danielhw99.common.web;

import java.beans.PropertyEditorSupport;

import com.danielhw99.common.api.ApiEnum;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
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
        for (MethodParameter parameter : method.getMethodParameters()) {
            Class<?> type = parameter.getParameterType();
            if (type.isEnum() && ApiEnum.class.isAssignableFrom(type)) {
                // A custom editor prevents Spring's enum-name fallback after conversion failure.
                binder.registerCustomEditor(type, new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        setValue(ApiEnum.fromValue(type.asSubclass(ApiEnum.class), text));
                    }
                });
            }
        }
    }
}
