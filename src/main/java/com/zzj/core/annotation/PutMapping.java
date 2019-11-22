package com.zzj.core.annotation;

import com.zzj.core.constant.RequestMethod;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@RequestMapping(method = RequestMethod.PUT)
public @interface PutMapping {
    String value();
}
