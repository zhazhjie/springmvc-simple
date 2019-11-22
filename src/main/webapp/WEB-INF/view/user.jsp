<%@ page import="com.zzj.biz.entity.User" %><%--
  Created by IntelliJ IDEA.
  User: Administrator
  Date: 2019/11/22
  Time: 9:47
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Title</title>
</head>
<body>
    <div>id:<%= request.getAttribute("id") %></div>
    <div>name:<%= request.getAttribute("name") %></div>
</body>
</html>
