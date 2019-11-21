package com.zzj.core.dispatch;

import com.zzj.core.annotation.*;
import com.zzj.core.constant.RequestMethod;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DefaultDispatch extends HttpServlet {
    private static String packagePath = "";
    private static Properties properties = new Properties();
    private static Map<Class, Object> ioc = new HashMap<>();
    private static Map<String, MethodHandler> methodHandlerMap = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        initConfig(config.getInitParameter("appProperties"));
        scanPackage(packagePath);
        doAutowired();
        mappingPathToHandler();
    }

    /**
     * 处理请求
     *
     * @param req
     * @param resp
     * @throws IOException
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!requestPreCheck(req, resp)) return;
        String requestURI = req.getRequestURI();
        MethodHandler methodHandler = methodHandlerMap.get(requestURI);
        Method method = methodHandler.getMethod();
        try {
            Map<String, String[]> parameterMap = req.getParameterMap();
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            //填充参数
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                Class<?> paramClazz = parameter.getType();
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                String paramName = requestParam != null ? requestParam.value() : parameter.getName();
                String[] values = parameterMap.get(paramName);
                args[i] = convertValue(paramClazz, values, req, resp);
            }
            //调用对应controller
            Object invoke = method.invoke(methodHandler.getInstance(), args);
            PrintWriter writer = resp.getWriter();
            writer.print(invoke);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Object convertValue(Class clazz, String[] values, HttpServletRequest req, HttpServletResponse resp) {
        if (clazz == HttpServletRequest.class) {
            return req;
        }
        if (clazz == HttpServletResponse.class) {
            return resp;
        }
        String value;
        if (values != null && values.length == 1) {
            value = values[0];
        } else {
            if (clazz == Boolean.TYPE) {
                return false;
            }
            if (clazz == Byte.TYPE || clazz == Short.TYPE || clazz == Integer.TYPE || clazz == Long.TYPE || clazz == Float.TYPE || clazz == Double.TYPE) {
                return 0;
            }
            return null;
        }
        if (clazz == Boolean.TYPE || clazz == Boolean.class) {
            return Boolean.valueOf(value);
        }
        if (clazz == Byte.TYPE || clazz == Byte.class) {
            return Byte.valueOf(value);
        }
        if (clazz == Short.TYPE || clazz == Short.class) {
            return Short.valueOf(value);
        }
        if (clazz == Integer.TYPE || clazz == Integer.class) {
            return Integer.valueOf(value);
        }
        if (clazz == Long.TYPE || clazz == Long.class) {
            return Long.valueOf(value);
        }
        if (clazz == Float.TYPE || clazz == Float.class) {
            return Float.valueOf(value);
        }
        if (clazz == Double.TYPE || clazz == Double.class) {
            return Double.valueOf(value);
        }
        if (clazz == String.class) {
            return String.valueOf(value);
        }
        return null;
    }

    /**
     * 请求前置检查
     *
     * @param req
     * @param resp
     * @return
     * @throws IOException
     */
    private boolean requestPreCheck(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String requestURI = req.getRequestURI();
        MethodHandler methodHandler = methodHandlerMap.get(requestURI);
        //判断请求路径是否有对应的handler
        if (methodHandler == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "404 NOT FOUND");
            return false;
        }
        //判断请求方法是否支持
        String curMethod = req.getMethod().toUpperCase();
        RequestMethod[] requestMethods = methodHandler.getRequestMethod();
        boolean matchMethod = Arrays.stream(requestMethods).anyMatch(requestMethod -> requestMethod.toString().equals(curMethod));
        if (!matchMethod) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, curMethod + " METHOD NOT ALLOWED");
            return false;
        }
        return true;
    }

    /**
     * 初始化配置
     *
     * @param appProperties
     */
    private void initConfig(String appProperties) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(appProperties);
        try {
            properties.load(resourceAsStream);
            packagePath = properties.getProperty("package").replaceAll("\\.", "/");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描包下所有Bean
     *
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
     *
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
     *
     * @param className
     */
    private void instanceBean(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Component component = getAnnotation(clazz, Component.class);
            //包含@Component注解的添加到ico容器
            if (component != null) {
                Object instance = clazz.newInstance();
                //按类型存
                ioc.put(clazz, instance);
                Class<?>[] interfaces = clazz.getInterfaces();
                Arrays.stream(interfaces).forEach(interfaceClazz -> {
                    ioc.put(interfaceClazz, instance);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 执行自动装配
     */
    private void doAutowired() {
        ioc.forEach((key, instance) -> {
            Field[] fields = instance.getClass().getDeclaredFields();
            Arrays.stream(fields).forEach(field -> {
                boolean needAutowired = field.isAnnotationPresent(Autowired.class);
                if (needAutowired) {
                    try {
                        field.setAccessible(true);
                        Class<?> type = field.getType();
                        //按类型装配
                        field.set(instance, ioc.get(type));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
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
                    buildMethodHandler(instance, method, basePath);
                });
            }
        });
    }

    /**
     * 生成具体路径对应的handler
     *
     * @param instance
     * @param method
     * @param basePath
     */
    private void buildMethodHandler(Object instance, Method method, String basePath) {
        RequestMapping requestMapping = getAnnotation(method, RequestMapping.class);
        if (requestMapping == null) return;
        MethodHandler methodHandler = new MethodHandler();
        methodHandler.setRequestMethod(requestMapping.method());
        methodHandler.setInstance(instance);
        methodHandler.setMethod(method);
        methodHandler.setPath(basePath + requestMapping.value() + getMappingPath(method));
        methodHandlerMap.put(methodHandler.getPath(), methodHandler);
    }

    /**
     * 获取基准路径
     *
     * @param clazz
     * @return
     */
    private String getBasePath(Class<?> clazz) {
        RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
        return requestMapping.value();
    }

    /**
     * 获取具体路径
     *
     * @param method
     * @return
     */
    private String getMappingPath(Method method) {
        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null) return requestMapping.value();
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null) return getMapping.value();
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null) return postMapping.value();
        return "";
    }

}
