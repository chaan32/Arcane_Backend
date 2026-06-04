package com.arcane.Arcane.Web.User.repository;

import com.arcane.Arcane.Web.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginId(String loginId);
    Optional<User> findByNickName(String nickName);
    Optional<User> findByEmail(String email);
}
