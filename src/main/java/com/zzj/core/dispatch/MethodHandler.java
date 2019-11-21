package com.zzj.core.dispatch;

import com.zzj.core.constant.RequestMethod;
import lombok.Getter;
import lombok.Setter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Setter
@Getter
public class MethodHandler {
    private RequestMethod[] requestMethod;
    private Annotation annotation;
    private Object instance;
    private Method method;
    private String path;
}
