package com.beefit.api.utils.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Essa interface gera uma nova annotation para funções dos services que devem gerar logs
// @interface é utilizado para criar novas annotations
// Tive que instalar o Spring AOP (dependência de programação orientada à apectos)

@Target(ElementType.METHOD) // Usada em métodos
@Retention(RetentionPolicy.RUNTIME) // Conseguimos combinar com reflections
public @interface LogAudit {
    String action();
    String description() default "";
}
