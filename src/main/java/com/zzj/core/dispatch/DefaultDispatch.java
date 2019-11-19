package com.zzj.core.dispatch;

import com.zzj.core.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class DefaultDispatch extends HttpServlet {
    private static Map<Object, Object> ioc = new HashMap<>();
    private static Properties properties=new Properties();
    private static Map<String,Object> pathMapping=new HashMap<>();
    private static List<Annotation> mappingAnnoList=new ArrayList<>();

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
        this.initConfig(config.getInitParameter("appProperties"));
        this.scanPackage(properties.getProperty("package").replaceAll("\\.","/"));
        this.doAutowired();
    }

    private void initConfig(String appProperties) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(appProperties);
        try {
            properties.load(resourceAsStream);
        }catch (IOException e){

        }
    }

    private void scanPackage(String packagePath) {
        URL resource = this.getClass().getClassLoader().getResource(packagePath);
        if (resource == null) return;
        File file = new File(resource.getPath());
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if(files==null) return;
            for (File subFile : files) {
                this.scanPackage(packagePath + "/"+ subFile.getName());
            }
        } else {
            this.instanceBean(packagePath.replace(".class","").replace("/", "."));
        }

    }
    private boolean checkAnnotation(Class<?> clazz){
        Component component = clazz.getAnnotation(Component.class);
        if(component!=null){
            return true;
        }
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation:annotations){
            Class<? extends Annotation> annotationType = annotation.annotationType();
            return this.checkAnnotation(annotationType);
        }
        return false;
    }
    private void instanceBean(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.newInstance();
            boolean isComponent = this.checkAnnotation(clazz);
            if(isComponent){
                String beanName=toCamelBak(clazz.getSimpleName());
                ioc.put(clazz,instance);
                Class<?>[] interfaces = clazz.getInterfaces();
                Arrays.stream(interfaces).forEach(item->{
                    ioc.put(item,instance);
                });
            }

        } catch (Exception e) {

        }
    }
    private String toCamelBak(String name){
        return name.substring(0,1).toLowerCase()+name.substring(1);
    }
    private void doAutowired() {

        ioc.forEach((key,instance)->{
            Field[] fields = instance.getClass().getDeclaredFields();
            Arrays.stream(fields).forEach(field -> {
                boolean annotationPresent = field.isAnnotationPresent(Autowired.class);
                if(annotationPresent){
                    try {
                        field.setAccessible(true);
                        Class<?> type = field.getType();
                        field.set(instance,ioc.get(type));
                    }catch (IllegalAccessException e){

                    }

                }
            });
        });
    }

    private void mappingPathToHandler() {
        ioc.forEach((key,value)->{
            Class<?> clazz = value.getClass();
            boolean isController = clazz.isAnnotationPresent(Controller.class);
            if(isController){
                String basePath=getBasePath(clazz);
                Method[] declaredMethods = clazz.getDeclaredMethods();
                Arrays.stream(declaredMethods).forEach(method -> {
                    setPathMapping(method,basePath);
                });
            }
        });
    }
    private void setPathMapping(Method method,String basePath){

    }

    private String getBasePath(Class<?> clazz){
        RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
        return requestMapping.value();
    }



}
