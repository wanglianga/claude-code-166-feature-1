package cn.townhc.homebed.domain.entity;

import cn.townhc.homebed.domain.enums.DomainEnums.BedPauseReason;
import cn.townhc.homebed.domain.enums.DomainEnums.BedPauseStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 家庭病床暂停/复评记录。
 * 典型场景：护士按计划上门却得知患者临时送医，平台将建床状态置为“暂停巡诊”，
 * 巡诊计划同步暂停，须由家庭医生重新评估后才能恢复上门。
 */
@Getter
@Setter
@Entity
@Table(name = "bed_pause_record")
public class BedPauseRecord extends BaseEntity {
    @Column(nullable = false, unique = true, length = 32)
    private String pauseNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private BedApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BedPauseReason reason;

    /** 触发暂停的巡诊任务（上门未遇） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_visit_id")
    private Visit sourceVisit;

    /** 触发暂停的未遇记录 */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_no_show_id")
    private NoShowRecord sourceNoShow;

    @Column(length = 1024)
    private String pauseNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paused_by_id")
    private SysUser pausedBy;

    private LocalDateTime pausedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BedPauseStatus status = BedPauseStatus.PAUSED;

    /** 医生重新评估意见（未恢复时为阶段性意见） */
    @Column(length = 1024)
    private String reevaluationNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reevaluated_by_id")
    private SysUser reevaluatedBy;

    private LocalDateTime reevaluatedAt;

    /** 复评恢复说明 */
    @Column(length = 512)
    private String resumeNote;

    private LocalDateTime resumedAt;

    /** 复评后安排的下一次巡诊时间（快照） */
    private LocalDateTime nextVisitAt;
}
