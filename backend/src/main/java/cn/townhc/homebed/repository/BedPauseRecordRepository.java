package cn.townhc.homebed.repository;

import cn.townhc.homebed.domain.entity.BedPauseRecord;
import cn.townhc.homebed.domain.enums.DomainEnums.BedPauseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BedPauseRecordRepository extends JpaRepository<BedPauseRecord, Long> {
    List<BedPauseRecord> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<BedPauseRecord> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
    List<BedPauseRecord> findByStatusOrderByCreatedAtDesc(BedPauseStatus status);
}
