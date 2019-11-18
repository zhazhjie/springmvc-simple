package com.zzj.core.annotation;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Controller {
    @AliasFor(annotation = Component.class)
    String value() default "";
}
