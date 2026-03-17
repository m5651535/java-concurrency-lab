package com.lab.nio.reactive.flux.config;

import io.micrometer.observation.ObservationRegistry;
import io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.proxy.observation.ObservationProxyExecutionListener;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class R2dbcConfig {

    @Bean
    public static BeanPostProcessor r2dbcProxyPostProcessor(
            ObjectProvider<ObservationRegistry> observationRegistryProvider,
            ObjectProvider<R2dbcProperties> r2dbcPropertiesProvider) {

        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ConnectionFactory) {

                    ObservationRegistry registry = observationRegistryProvider
                            .getIfAvailable(ObservationRegistry::create);

                    R2dbcProperties props = r2dbcPropertiesProvider.getIfAvailable();
                    String url = (props != null) ? props.getUrl() : "r2dbc:postgresql://db:5432/lab_db";

                    ObservationProxyExecutionListener listener =
                            new ObservationProxyExecutionListener(registry, (ConnectionFactory) bean, url);

                    return ProxyConnectionFactory.builder((ConnectionFactory) bean)
                            .listener(listener)
                            .build();
                }
                return bean;
            }
        };
    }
}