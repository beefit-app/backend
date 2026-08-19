package com.beefit.api.infra.database;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseUrlSanitizerPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSourceProperties properties) {
            String url = properties.getUrl();
            if (url != null && !url.startsWith("jdbc:")) {
                if (url.startsWith("postgres://")) {
                    properties.setUrl("jdbc:postgresql://" + url.substring("postgres://".length()));
                } else if (url.startsWith("postgresql://")) {
                    properties.setUrl("jdbc:postgresql://" + url.substring("postgresql://".length()));
                } else {
                    properties.setUrl("jdbc:" + url);
                }
            }
        }
        return bean;
    }
}
