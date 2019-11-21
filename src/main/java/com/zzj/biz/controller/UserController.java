package com.zzj.biz.controller;

import com.zzj.core.annotation.*;
import com.zzj.biz.service.UserService;

@Controller
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/getUser")
    public String getUser(@RequestParam("name") String name,Integer id){
        System.out.println(name+":"+id);
        return name;
    }
}
