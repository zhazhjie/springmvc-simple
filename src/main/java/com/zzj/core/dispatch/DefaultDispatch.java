package com.zzj.core.dispatch;

import com.zzj.core.annotation.Component;
import com.zzj.core.annotation.Controller;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class DefaultDispatch extends HttpServlet {
    private static Map<String,Object> ico=new HashMap<>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
//        this.scanPackage("com.zzj.biz");
        this.instanceComponents("com.zzj.biz.controller.UserController");
    }
    private void initConfig(){

    }
    private void scanPackage(String packageName){
        URL resource = this.getClass().getClassLoader().getResource(packageName.replaceAll("\\.","/"));
        if(resource==null) return;
        File file = new File(resource.getPath());
        if(!file.exists()) return;
        if(file.isDirectory()){
            File[] files = file.listFiles();
            for (File file1 : files) {
                this.scanPackage(packageName+"."+file1.getName());
            }
        }else{
            this.instanceComponents(file.getName().replace(".class",""));
        }

    }
    private void instanceComponents(String className){
        try {
            Class<?> clazz = Class.forName(className);
            Object o = clazz.newInstance();
            Component annotation = clazz.getAnnotation(Component.class);
        }catch (Exception e){

        }
    }
    private void setAutowired(){

    }
    private void mappingPathToHandler(){

    }

}
