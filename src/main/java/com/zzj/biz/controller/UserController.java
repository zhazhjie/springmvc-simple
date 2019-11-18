package com.zzj.biz.controller;

import com.zzj.core.annotation.Autowired;
import com.zzj.core.annotation.Controller;
import com.zzj.core.annotation.GetMapping;
import com.zzj.core.annotation.RequestMapping;
import com.zzj.biz.service.UserService;

@Controller
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/getUser")
    public void getUser(){

    }
}
