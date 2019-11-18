package com.zzj.core.annotation;

public @interface Autowired {
    boolean required() default true;
}
