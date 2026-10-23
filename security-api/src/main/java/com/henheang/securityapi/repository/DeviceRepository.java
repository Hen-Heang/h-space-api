package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.Device;
import com.henheang.securityapi.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {
    List<Device> findAllByUser(User user);

    Optional<Device> findByUserAndFingerprint(User user, String fingerprint);
}
