package com.zzj.core.dispatch;

import com.zzj.core.constant.RequestMethod;
import com.zzj.core.constant.ResponseType;

import java.lang.reflect.Method;


public class MethodHandler {
    private RequestMethod[] requestMethod;
    private ResponseType responseType;
    private Object instance;
    private Method method;
    private String path;

    public RequestMethod[] getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(RequestMethod[] requestMethod) {
        this.requestMethod = requestMethod;
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public void setResponseType(ResponseType responseType) {
        this.responseType = responseType;
    }

    public Object getInstance() {
        return instance;
    }

    public void setInstance(Object instance) {
        this.instance = instance;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
