package com.zzj.core.dispatch;

import com.zzj.core.constant.RequestMethod;
import com.zzj.core.constant.ResponseType;
import lombok.Getter;
import lombok.Setter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Setter
@Getter
public class MethodHandler {
    private RequestMethod[] requestMethod;
    private ResponseType responseType;
    private Object instance;
    private Method method;
    private String path;
}
