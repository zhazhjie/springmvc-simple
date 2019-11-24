package com.zzj.biz.exception;

import com.zzj.core.annotation.ControllerAdvice;
import com.zzj.core.annotation.ExceptionHandler;
import com.zzj.core.annotation.ResponseBody;
import com.zzj.core.exception.WebException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = WebException.class)
    public String WebExceptionHandler(WebException e) {
        e.printStackTrace();
        return "error";
    }

    @ExceptionHandler(value = Exception.class)
    public String ExceptionHandler(Exception e) {
        e.printStackTrace();
        return "error";
    }

}
