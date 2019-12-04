package com.zzj.core.annotation;

import com.zzj.core.constant.RequestMethod;

import java.lang.annotation.*;

@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestMapping {
    String value() default "";

    RequestMethod[] method() default {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.PUT};
}
