package com.danielhw99.common.config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

import com.danielhw99.common.api.ApiFormats;
import com.danielhw99.common.api.ApiId;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ApiWebConfiguration implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, ApiId.class, ApiId::parse);
        registry.addConverter(String.class, LocalDate.class, ApiFormats::parseDate);
        registry.addConverter(String.class, LocalTime.class, ApiFormats::parseTime);
        registry.addConverter(String.class, YearMonth.class, ApiFormats::parseMonth);
    }
}
