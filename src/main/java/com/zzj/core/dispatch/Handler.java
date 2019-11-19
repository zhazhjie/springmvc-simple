package com.zzj.core.dispatch;

import lombok.Getter;
import lombok.Setter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Setter
@Getter
public class Handler {
    private String methodName;
    private Annotation annotation;
    private Object instance;
    private Method method;
    private boolean handleFlag;
}
