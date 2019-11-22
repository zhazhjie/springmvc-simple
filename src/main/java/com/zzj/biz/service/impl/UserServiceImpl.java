package com.zzj.biz.service.impl;

import com.zzj.biz.entity.User;
import com.zzj.core.annotation.Service;
import com.zzj.biz.service.UserService;

@Service
public class UserServiceImpl implements UserService {
    @Override
    public User getUserById(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("jac");
        return user;
    }
}
