package com.zzj.core.dispatch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zzj.core.annotation.*;
import com.zzj.core.constant.RequestMethod;
import com.zzj.core.constant.ResponseType;
import com.zzj.core.exception.WebException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DefaultDispatch extends HttpServlet {
    private static String PACKAGE_PATH = "";
    private static String VIEW_PATH = "";
    private static Properties PROPERTIES = new Properties();
    private static Map<Class, Object> IOC = new HashMap<>();
    private static Map<String, MethodHandler> METHOD_HANDLER_MAP = new HashMap<>();

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
        scanPackage(PACKAGE_PATH);
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
        MethodHandler methodHandler = METHOD_HANDLER_MAP.get(requestURI);
        Method method = methodHandler.getMethod();
        try {
            //填充参数
            Object[] args = fillParams(method.getParameters(), req, resp);
            //调用对应controller
            Object invoke = method.invoke(methodHandler.getInstance(), args);
            if (methodHandler.getResponseType() == ResponseType.VIEW) {
                req.getRequestDispatcher(VIEW_PATH + invoke.toString() + ".jsp").forward(req, resp);
            } else {
                PrintWriter writer = resp.getWriter();
                writer.print(JSON.toJSONString(invoke));
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 填充参数
     *
     * @param parameters
     * @param req
     * @param resp
     * @return
     * @throws IOException
     */
    private Object[] fillParams(Parameter[] parameters, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JSONObject params = new JSONObject();
        JSONObject data = new JSONObject();
        Map<String, String[]> parameterMap = req.getParameterMap();
        parameterMap.forEach((key, value) -> {
            if (value != null && value.length == 1) {
                params.put(key, value[0]);
            } else {
                params.put(key, value);
            }
        });
        //获取json数据
        if (req.getContentType() != null && req.getContentType().equals("application/json")) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(req.getInputStream()));
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            data = JSON.parseObject(stringBuilder.toString(), JSONObject.class);
        }
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> paramClazz = parameter.getType();
            RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
            //有@RequestBody注解尝试用json数据填充
            if (requestBody != null) {
                try {
                    Object instance = paramClazz.newInstance();
                    Field[] declaredFields = paramClazz.getDeclaredFields();
                    for (Field field : declaredFields) {
                        field.setAccessible(true);
                        Object param = data.getObject(field.getName(), field.getType());
                        field.set(instance, param);
                    }
                    args[i] = instance;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                String paramName = requestParam != null ? requestParam.value() : parameter.getName();
                if (paramClazz == HttpServletRequest.class) {
                    args[i] = req;
                } else if (paramClazz == HttpServletResponse.class) {
                    args[i] = resp;
                } else {
                    args[i] = params.getObject(paramName, parameter.getType());
                }
            }
        }
        return args;
    }

    /**
     * 请求前置拦截
     *
     * @param req
     * @param resp
     * @return
     * @throws IOException
     */
    private boolean requestPreCheck(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String requestURI = req.getRequestURI();
        MethodHandler methodHandler = METHOD_HANDLER_MAP.get(requestURI);
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
        try {
            InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(appProperties);
            PROPERTIES.load(resourceAsStream);
            PACKAGE_PATH = PROPERTIES.getProperty("packageName").replaceAll("\\.", "/");
            VIEW_PATH = PROPERTIES.getProperty("viewPath");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描包下所有类
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
            if (packagePath.endsWith(".class"))
                this.instanceBean(packagePath.replace(".class", "").replace("/", "."));
        }

    }

    /**
     * 递归查找目标注解
     *
     * @param source
     * @param annotationClazz
     * @param <T>
     * @return
     */
    public static <T extends Annotation> T getAnnotation(Object source, Class<T> annotationClazz) {
        T targetAnnotation = null;
        Annotation[] annotations = new Annotation[]{};
        if (source instanceof Class) {
            Class<?> clazz = (Class<?>) source;
            targetAnnotation = clazz.getAnnotation(annotationClazz);
            annotations = clazz.getAnnotations();
        } else if (source instanceof Field) {
            Field field = (Field) source;
            targetAnnotation = field.getAnnotation(annotationClazz);
            annotations = field.getAnnotations();
        } else if (source instanceof Method) {
            Method method = (Method) source;
            targetAnnotation = method.getAnnotation(annotationClazz);
            annotations = method.getAnnotations();
        }
        if (targetAnnotation != null) {
            return targetAnnotation;
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof Documented || annotation instanceof Target || annotation instanceof Retention) {
                continue;
            }
            Class<? extends Annotation> annotationType = annotation.annotationType();
            return getAnnotation(annotationType, annotationClazz);
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
            //包含@Component注解的类实例添加到ico容器
            if (component != null) {
                Object instance = clazz.newInstance();
                //按类型存
                IOC.put(clazz, instance);
                Class<?>[] interfaces = clazz.getInterfaces();
                Arrays.stream(interfaces).forEach(interfaceClazz -> {
                    IOC.put(interfaceClazz, instance);
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
        IOC.forEach((key, instance) -> {
            Field[] fields = instance.getClass().getDeclaredFields();
            Arrays.stream(fields).forEach(field -> {
                Autowired autowired = field.getAnnotation(Autowired.class);
                if (autowired != null) {
                    try {
                        field.setAccessible(true);
                        Class<?> type = field.getType();
                        //按类型装配
                        Object bean = IOC.get(type);
                        if (autowired.required() && bean == null) {
                            throw new WebException("Can not found the bean with type "+type);
                        }
                        field.set(instance, bean);
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
        IOC.forEach((key, instance) -> {
            Class<?> clazz = instance.getClass();
            Controller controller = getAnnotation(clazz, Controller.class);
            if (controller != null) {
                String basePath = getBasePath(clazz);
                Method[] declaredMethods = clazz.getDeclaredMethods();
                Arrays.stream(declaredMethods).forEach(method -> {
                    buildMethodHandler(clazz, instance, method, basePath);
                });
            }
        });
    }

    /**
     * 生成请求路径对应的handler
     *
     * @param instance
     * @param method
     * @param basePath
     */
    private void buildMethodHandler(Class clazz, Object instance, Method method, String basePath) {
        RequestMapping requestMapping = getAnnotation(method, RequestMapping.class);
        if (requestMapping == null) return;
        MethodHandler methodHandler = new MethodHandler();
        methodHandler.setRequestMethod(requestMapping.method());
        methodHandler.setInstance(instance);
        methodHandler.setMethod(method);
        methodHandler.setPath(basePath + getMappingPath(method));
        methodHandler.setResponseType(getResponseType(clazz, method));
        METHOD_HANDLER_MAP.put(methodHandler.getPath(), methodHandler);
    }

    /**
     * 请求的返回类型
     *
     * @param clazz
     * @param method
     * @return
     */
    private ResponseType getResponseType(Class clazz, Method method) {
        return getAnnotation(clazz, ResponseBody.class) != null || getAnnotation(method, ResponseBody.class) != null ? ResponseType.MODEL : ResponseType.VIEW;
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
