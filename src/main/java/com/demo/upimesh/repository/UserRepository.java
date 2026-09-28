package com.demo.upimesh.repository;

import com.demo.upimesh.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByVpa(String vpa);
}
