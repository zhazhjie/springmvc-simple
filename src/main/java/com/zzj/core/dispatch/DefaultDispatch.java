package com.zzj.core.dispatch;

import com.zzj.core.annotation.*;
import com.zzj.core.exception.WebException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

public class DefaultDispatch extends HttpServlet {
    private static Map<Object, Object> ioc = new HashMap<>();
    private static Properties properties = new Properties();
    private static Map<String, MethodHandler> methodHandlerMap = new HashMap<>();
    private static List<Annotation> mappingAnnoList = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        initConfig(config.getInitParameter("appProperties"));
        scanPackage(properties.getProperty("package").replaceAll("\\.", "/"));
        doAutowired();
        mappingPathToHandler();

    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException{
        String requestURI = req.getRequestURI();
        MethodHandler methodHandler = methodHandlerMap.get(requestURI);
        if(methodHandler==null){
            resp.setStatus(404);
            PrintWriter writer = resp.getWriter();
            writer.print("404 not fund");
//            throw new WebException("404 not fund");
        }
        Method method = methodHandler.getMethod();
        try {
            Parameter[] parameters = method.getParameters();
            Arrays.stream(parameters).forEach(parameter -> {

            });
            method.invoke(methodHandler.getInstance(),parameters);
        }catch (Exception e){

        }

    }

    /**
     * 初始化配置
     * @param appProperties
     */
    private void initConfig(String appProperties) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(appProperties);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {

        }
    }

    /**
     * 扫描包下所有Bean
     * @param packagePath
     */
    private void scanPackage(String packagePath) {
        URL resource = this.getClass().getClassLoader().getResource(packagePath);
        if (resource == null) return;
        File file = new File(resource.getPath());
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) return;
            for (File subFile : files) {
                this.scanPackage(packagePath + "/" + subFile.getName());
            }
        } else {
            this.instanceBean(packagePath.replace(".class", "").replace("/", "."));
        }

    }

    /**
     * 递归查找目标注解
     * @param source
     * @param annotationClass
     * @param <T>
     * @return
     */
    public static <T extends Annotation> T getAnnotation(Object source, Class<T> annotationClass) {
        T targetAnnotation = null;
        Annotation[] annotations = new Annotation[]{};
        if (source instanceof Class) {
            Class<?> clazz = (Class<?>) source;
            targetAnnotation = clazz.getAnnotation(annotationClass);
            annotations = clazz.getAnnotations();
        } else if (source instanceof Field) {
            Field field = (Field) source;
            targetAnnotation = field.getAnnotation(annotationClass);
            annotations = field.getAnnotations();
        } else if (source instanceof Method) {
            Method method = (Method) source;
            targetAnnotation = method.getAnnotation(annotationClass);
            annotations = method.getAnnotations();
        }
        if (targetAnnotation != null) {
            return targetAnnotation;
        }
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            return getAnnotation(annotationType, annotationClass);
        }
        return null;
    }

    /**
     * 实例化Bean
     * @param className
     */
    private void instanceBean(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.newInstance();
            Component component = getAnnotation(clazz, Component.class);
            if (component != null) {
                ioc.put(clazz, instance);
                Class<?>[] interfaces = clazz.getInterfaces();
                Arrays.stream(interfaces).forEach(item -> {
                    ioc.put(item, instance);
                });
            }

        } catch (Exception e) {

        }
    }

    private String toCamelBak(String name) {
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    /**
     * 执行自动装配
     */
    private void doAutowired() {
        ioc.forEach((key, instance) -> {
            Field[] fields = instance.getClass().getDeclaredFields();
            Arrays.stream(fields).forEach(field -> {
                boolean annotationPresent = field.isAnnotationPresent(Autowired.class);
                if (annotationPresent) {
                    try {
                        field.setAccessible(true);
                        Class<?> type = field.getType();
                        field.set(instance, ioc.get(type));
                    } catch (IllegalAccessException e) {

                    }

                }
            });
        });
    }

    /**
     * 映射请求路径到具体方法
     */
    private void mappingPathToHandler() {
        ioc.forEach((key, instance) -> {
            Class<?> clazz = instance.getClass();
            boolean isController = clazz.isAnnotationPresent(Controller.class);
            if (isController) {
                String basePath = getBasePath(clazz);
                Method[] declaredMethods = clazz.getDeclaredMethods();
                Arrays.stream(declaredMethods).forEach(method -> {
                    setPathMapping(instance, method, basePath);
                });
            }
        });
    }

    private void setPathMapping(Object instance, Method method, String basePath) {
        MethodHandler methodHandler = new MethodHandler();
        RequestMapping requestMapping = getAnnotation(method, RequestMapping.class);
        methodHandler.setRequestMethod(requestMapping.method());
        methodHandler.setInstance(instance);
        methodHandler.setMethod(method);
        methodHandler.setPath(basePath + requestMapping.value());
        methodHandler.setHandleFlag(true);
    }

    private String getBasePath(Class<?> clazz) {
        RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
        requestMapping.annotationType();
        return requestMapping.value();
    }

}
