package com.promoit.otp.service;

import com.promoit.otp.dao.UserDao;
import com.promoit.otp.model.Role;
import com.promoit.otp.model.User;
import com.promoit.otp.util.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserDao userDao;
    private final TokenService tokenService;

    public UserService(UserDao userDao, TokenService tokenService) {
        this.userDao = userDao;
        this.tokenService = tokenService;
    }

    public User register(String login, String password, Role role) {
        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(400, "login and password are required");
        }
        if (userDao.findByLogin(login).isPresent()) {
            throw new ApiException(409, "User with this login already exists");
        }
        if (role == Role.ADMIN && userDao.existsByRole(Role.ADMIN)) {
            throw new ApiException(409, "An administrator already exists; a second one cannot be registered");
        }
        User user = userDao.create(login, PasswordHasher.hash(password), role);
        log.info("Registered user '{}' with role {}", login, role);
        return user;
    }

    public String login(String login, String password) {
        User user = userDao.findByLogin(login)
                .orElseThrow(() -> new ApiException(401, "Invalid credentials"));
        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new ApiException(401, "Invalid credentials");
        }
        log.info("User '{}' logged in", login);
        return tokenService.issue(user.getId(), user.getLogin(), user.getRole());
    }

    public List<User> listNonAdminUsers() {
        return userDao.findAllNonAdmin();
    }

    public void deleteUser(long id) {
        User user = userDao.findById(id)
                .orElseThrow(() -> new ApiException(404, "User not found"));
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(403, "Administrators cannot be deleted");
        }
        userDao.delete(id);
        log.info("Deleted user id={} (login '{}') and cascaded OTP codes", id, user.getLogin());
    }
}
