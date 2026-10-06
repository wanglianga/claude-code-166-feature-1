package cn.townhc.homebed.repository;

import cn.townhc.homebed.domain.entity.NoShowCase;
import cn.townhc.homebed.domain.enums.DomainEnums.NoShowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoShowCaseRepository extends JpaRepository<NoShowCase, Long> {
    List<NoShowCase> findByVisitId(Long visitId);
    List<NoShowCase> findByStatusOrderByCreatedAtDesc(NoShowStatus status);
    List<NoShowCase> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<NoShowCase> findAllByOrderByCreatedAtDesc();
    long countByStatus(NoShowStatus status);
}
