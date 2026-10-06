package cn.townhc.homebed.domain.entity;

import cn.townhc.homebed.domain.enums.DomainEnums.NoShowDecision;
import cn.townhc.homebed.domain.enums.DomainEnums.NoShowFamilyReason;
import cn.townhc.homebed.domain.enums.DomainEnums.NoShowReason;
import cn.townhc.homebed.domain.enums.DomainEnums.NoShowStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 上门未遇工单：护士按计划到患者家中但家属未开门时生成。
 * 固化护士现场证据（到达定位、电话拨打、等待时长、门口照片），
 * 先走“家属补充说明 → 卫生院审核（减免/空跑费/扣除一次服务/调整下一次/暂停病床）”，
 * 疑似患者临时送医时把家庭病床改为暂停，待家庭医生重新评估后再恢复巡诊。
 */
@Getter
@Setter
@Entity
@Table(name = "no_show_case")
public class NoShowCase extends BaseEntity {
    @Column(nullable = false, unique = true, length = 32)
    private String caseNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id")
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    /** 到现场的护士 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id")
    private SysUser nurse;

    // ===== 护士现场证据（上报后不可篡改，补录只能走审核说明） =====

    /** 到达时间 */
    private LocalDateTime arrivedAt;

    /** 护士离开/结束等待时间 */
    private LocalDateTime leftAt;

    /** 到达定位文字快照（地址 + GPS 文本） */
    @Column(length = 256)
    private String arrivalLocationSnapshot;

    private Double arrivalLongitude;
    private Double arrivalLatitude;

    /** 现场初判原因 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NoShowReason reason;

    /**
     * 电话拨打记录 JSON：[{contact,phone,at,durationSeconds,result}]，
     * 留存“联系过谁、几次、是否接通”，作为空跑费争议时的举证。
     */
    @Column(length = 2048)
    private String phoneCallsJson;

    /** 门口等待时长（分钟） */
    private Integer waitMinutes;

    /** 门口照片路径 JSON 数组 */
    @Column(length = 2048)
    private String doorPhotoPathsJson;

    /** 家属是否已提前请假（false 时才可计空跑费/扣除服务） */
    @Column(nullable = false)
    private Boolean advanceLeaveNotified = false;

    @Column(length = 1024)
    private String nurseNote;

    // ===== 工单状态 =====

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NoShowStatus status = NoShowStatus.PENDING_FAMILY;

    // ===== 家属补充说明 =====

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private NoShowFamilyReason familyReason;

    @Column(length = 1024)
    private String familyNote;

    /** 家属补充说明凭证（急诊挂号单/通话记录截图等） */
    @Column(length = 256)
    private String familyEvidencePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_user_id")
    private SysUser familyUser;

    private LocalDateTime familySubmittedAt;

    // ===== 卫生院审核 =====

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private NoShowDecision decision;

    @Column(length = 1024)
    private String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private SysUser reviewer;

    private LocalDateTime reviewedAt;

    /** 审核确认的空跑费金额（出账时按此快照计入统一账单） */
    @Column(precision = 10, scale = 2)
    private BigDecimal emptyRunFee;

    /** 是否扣除一次巡诊服务（已完成次数包/服务次数内扣减） */
    private Boolean serviceDeducted = false;

    /** 空跑费是否已计入统一账单 */
    private Boolean billed = false;

    // ===== 临时送医：病床暂停 → 医生复评 → 恢复巡诊 =====

    private LocalDateTime bedPausedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reassess_doctor_id")
    private SysUser reassessDoctor;

    /** 家庭医生重新评估结论（出院/病情稳定，可恢复巡诊） */
    @Column(length = 1024)
    private String reassessNote;

    private LocalDateTime reassessedAt;

    /** 复评后重新安排/恢复的巡诊任务 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resumed_visit_id")
    private Visit resumedVisit;
}
