package com.beefit.api.service;

import com.beefit.api.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.awt.print.Pageable;

@Service
public class AdminUserService {

    public Page<User> searchUsers(String search, Pageable pageable){



    }
}
