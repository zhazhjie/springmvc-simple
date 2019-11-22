package com.zzj.core.annotation;

import com.zzj.core.constant.RequestMethod;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@RequestMapping(method = RequestMethod.DELETE)
public @interface DeleteMapping {
    String value();
}
