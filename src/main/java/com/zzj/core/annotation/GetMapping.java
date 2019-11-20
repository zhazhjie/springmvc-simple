package com.zzj.core.annotation;

import com.zzj.core.constant.RequestMethod;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@RequestMapping(method = RequestMethod.GET)
public @interface GetMapping {
    @AliasFor(annotation = RequestMapping.class)
    String value();
}
