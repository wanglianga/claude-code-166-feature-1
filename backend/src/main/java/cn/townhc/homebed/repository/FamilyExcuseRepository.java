package cn.townhc.homebed.repository;

import cn.townhc.homebed.domain.entity.FamilyExcuse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FamilyExcuseRepository extends JpaRepository<FamilyExcuse, Long> {
    List<FamilyExcuse> findByNoShowIdOrderByCreatedAtDesc(Long noShowId);
}
