package com.demo.upimesh.repository;

import com.demo.upimesh.model.MeshConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeshConnectionRepository extends JpaRepository<MeshConnection, Long> {
    List<MeshConnection> findAllByOrderByIdAsc();
    List<MeshConnection> findByStatus(MeshConnection.Status status);
    Optional<MeshConnection> findBySourceDevice_IdAndTargetDevice_Id(Long sourceId, Long targetId);
    long countByStatus(MeshConnection.Status status);
}
