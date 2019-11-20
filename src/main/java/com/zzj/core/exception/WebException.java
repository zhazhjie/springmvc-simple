package com.zzj.core.exception;

public class WebException extends RuntimeException{
    private static final long serialVersionUID = 1;

    private String msg;
    private int code = 500;

    public WebException(String msg) {
        super(msg);
        this.msg = msg;
    }

    public WebException(String msg, Throwable e) {
        super(msg, e);
        this.msg = msg;
    }

    public WebException(int code, String msg) {
        super(msg);
        this.msg = msg;
        this.code = code;
    }

    public WebException(int code, String msg, Throwable e) {
        super(msg, e);
        this.msg = msg;
        this.code = code;
    }
}
