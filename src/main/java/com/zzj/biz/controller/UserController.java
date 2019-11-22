package com.zzj.biz.controller;

import com.zzj.biz.entity.User;
import com.zzj.biz.service.UserService;
import com.zzj.core.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/getUserById")
    public User getUserById(Long id, HttpServletRequest request){
        System.out.println("id:"+id);
        User user = userService.getUserById(id);
        return user;
    }

    @PostMapping("/insertUser")
    public User insertUser(@RequestBody User user){
        System.out.println("user:"+user);
        return user;
    }
}
