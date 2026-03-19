package org.cedro.ordersecurityserver.init;

import org.cedro.ordermodel.model.AppUser;
import org.cedro.ordermodel.model.Role;
import org.cedro.ordersecurityserver.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataInitializer implements ApplicationRunner {

    @Autowired
    private AppUserRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent("admin", "admin123", Set.of(Role.ADMIN));
        createIfAbsent("manager1", "manager123", Set.of(Role.MANAGER));
        createIfAbsent("buyer1", "buyer123", Set.of(Role.BUYER));
    }

    private void createIfAbsent(String username, String rawPassword, Set<Role> roles) {
        if (repository.findByUsername(username).isEmpty()) {
            AppUser user = new AppUser();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRoles(roles);
            repository.save(user);
        }
    }
}
