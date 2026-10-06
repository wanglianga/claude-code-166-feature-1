package cn.townhc.homebed.repository;

import cn.townhc.homebed.domain.entity.NoShowRecord;
import cn.townhc.homebed.domain.enums.DomainEnums.NoShowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoShowRecordRepository extends JpaRepository<NoShowRecord, Long> {
    List<NoShowRecord> findByVisitId(Long visitId);
    List<NoShowRecord> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<NoShowRecord> findByStatusOrderByCreatedAtDesc(NoShowStatus status);
    List<NoShowRecord> findByBilledFalse();
    List<NoShowRecord> findByPatientIdAndBilledFalse(Long patientId);
}
