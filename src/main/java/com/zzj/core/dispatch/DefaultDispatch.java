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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DefaultDispatch extends HttpServlet {
    private String packagePath = "";
    private String viewPath = "";
    private Properties properties = new Properties();
    private Map<Class, Object> ioc = new HashMap<>();
    private Map<String, MethodHandler> methodHandlers = new HashMap<>();
    private Object controllerAdviceInstance = null;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
        controllerExceptionHandler(req, resp, () -> {
            String requestURI = req.getRequestURI();
            MethodHandler methodHandler = methodHandlers.get(requestURI);
            Method method = methodHandler.getMethod();
//            try {
            //填充参数
            Object[] args = fillParams(method.getParameters(), req, resp);
            //调用对应controller
            Object invoke = method.invoke(methodHandler.getInstance(), args);
            //响应请求
            sendResponse(req, resp, methodHandler.getResponseType(), invoke);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
        });

    }

    /**
     * controller层异常拦截
     *
     * @param req
     * @param resp
     * @param controllerExecutor
     */
    private void controllerExceptionHandler(HttpServletRequest req, HttpServletResponse resp, ControllerExecutor controllerExecutor) {
        try {
            controllerExecutor.run();
        } catch (InvocationTargetException e) {
            //InvocationTargetException 捕获反射invoke抛出的异常
            if (controllerAdviceInstance == null) {
                e.printStackTrace();
            } else {
                Class<?> clazz = controllerAdviceInstance.getClass();
                Method[] declaredMethods = clazz.getDeclaredMethods();
                Method defaultHandler = null;
                boolean handleFlag = false;
                for (Method declaredMethod : declaredMethods) {
                    ExceptionHandler exceptionHandler = declaredMethod.getAnnotation(ExceptionHandler.class);
                    //获取真正的异常类
                    Throwable targetException = e.getTargetException();
                    //执行对应的ExceptionHandler
                    if (exceptionHandler != null && exceptionHandler.value() == targetException.getClass()) {
                        try {
                            Object invoke = declaredMethod.invoke(controllerAdviceInstance, targetException);
                            ResponseType responseType = declaredMethod.isAnnotationPresent(ResponseBody.class) ? ResponseType.MODEL : ResponseType.VIEW;
                            sendResponse(req, resp, responseType, invoke);
                            handleFlag = true;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    } else if (exceptionHandler != null && exceptionHandler.value() == Exception.class) {
                        defaultHandler = declaredMethod;
                    }
                }
                //默认异常处理
                if (!handleFlag && defaultHandler != null) {
                    try {
                        Object invoke = defaultHandler.invoke(controllerAdviceInstance, e.getTargetException());
                        ResponseType responseType = defaultHandler.isAnnotationPresent(ResponseBody.class) ? ResponseType.MODEL : ResponseType.VIEW;
                        sendResponse(req, resp, responseType, invoke);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 响应请求
     *
     * @param req
     * @param resp
     * @param responseType
     * @param invoke
     * @throws ServletException
     * @throws IOException
     */
    private void sendResponse(HttpServletRequest req, HttpServletResponse resp, ResponseType responseType, Object invoke) throws ServletException, IOException {
        if (responseType == ResponseType.VIEW) {
            req.getRequestDispatcher(viewPath + invoke.toString() + ".jsp").forward(req, resp);
        } else {
            PrintWriter writer = resp.getWriter();
            writer.print(JSON.toJSONString(invoke));
            writer.close();
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
                String paramName = requestParam != null && !requestParam.value().equals("") ? requestParam.value() : parameter.getName();
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
        MethodHandler methodHandler = methodHandlers.get(requestURI);
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
            properties.load(resourceAsStream);
            packagePath = properties.getProperty("packageName").replaceAll("\\.", "/");
            viewPath = properties.getProperty("viewPath");
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
    private <T extends Annotation> T getAnnotation(Object source, Class<T> annotationClazz) {
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
            //跳过元注解
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
            ControllerAdvice controllerAdvice = clazz.getAnnotation(ControllerAdvice.class);
            //包含@Component注解的类实例添加到ico容器
            if (component != null) {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                //按类型存
                ioc.put(clazz, instance);
                Class<?>[] interfaces = clazz.getInterfaces();
                //把对应的接口类型也存进去
                Arrays.stream(interfaces).forEach(interfaceClazz -> {
                    ioc.put(interfaceClazz, instance);
                });
            } else if (controllerAdvice != null) {
                controllerAdviceInstance = clazz.getDeclaredConstructor().newInstance();
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
                Autowired autowired = field.getAnnotation(Autowired.class);
                if (autowired != null) {
                    try {
                        field.setAccessible(true);
                        Class<?> type = field.getType();
                        //按类型装配
                        Object bean = ioc.get(type);
                        if (autowired.required() && bean == null) {
                            throw new WebException("Can not found the bean with type " + type);
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
     * 映射请求路径到具体handler
     */
    private void mappingPathToHandler() {
        ioc.forEach((key, instance) -> {
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
        methodHandlers.put(methodHandler.getPath(), methodHandler);
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
        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        if (putMapping != null) return putMapping.value();
        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null) return deleteMapping.value();
        return "";
    }

}
