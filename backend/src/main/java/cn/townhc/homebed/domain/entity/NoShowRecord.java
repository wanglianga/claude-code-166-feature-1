package cn.townhc.homebed.domain.entity;

import cn.townhc.homebed.domain.enums.DomainEnums.NoShowDecision;
import cn.townhc.homebed.domain.enums.DomainEnums.NoShowReason;
import cn.townhc.homebed.domain.enums.DomainEnums.NoShowStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import cn.townhc.homebed.common.Jsons;

/**
 * 上门未遇（空跑）记录。
 * 护士按计划到达患者家中但家属未开门时，必须留存：
 * 到达定位、电话拨打记录、等待时长、门口照片，作为后续改约与费用处理的证据。
 * 家属可在卫生院核定费用前提交补充说明；卫生院审核后决定减免、保留空跑费、
 * 扣除一次服务或调整下一次巡诊。患者临时送医时联动家庭病床暂停与医生复评。
 */
@Getter
@Setter
@Entity
@Table(name = "no_show_record")
public class NoShowRecord extends BaseEntity {
    @Column(nullable = false, unique = true, length = 32)
    private String noShowNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id")
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nurse_id")
    private SysUser nurse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NoShowReason reason;

    // ===== 护士到场证据 =====
    /** 到达时间 */
    private LocalDateTime arrivedAt;

    /** 到达定位快照（地址 + 坐标文本） */
    @Column(length = 512)
    private String arrivalLocation;

    private Double arrivalLongitude;
    private Double arrivalLatitude;

    /** 门口照片路径 JSON 数组 */
    @JsonIgnore
    @Column(length = 2048)
    private String doorPhotoPathsJson;

    // ===== 电话拨打 =====
    /** 主要拨打号码（家属/紧急联系人） */
    @Column(length = 20)
    private String dialedPhone;

    /** 拨打次数 */
    private Integer callCount = 0;

    /** 接通次数 */
    private Integer connectedCallCount = 0;

    /** 通话/拨打明细 JSON：[{phone,time,connected,durationSec}] */
    @JsonIgnore
    @Column(length = 2048)
    private String callLogsJson;

    // ===== 等待 =====
    /** 等待时长（分钟） */
    private Integer waitMinutes;

    private LocalDateTime waitStartedAt;
    private LocalDateTime waitEndedAt;

    /** 护士现场情况描述（敲门、询问邻居等） */
    @Column(length = 1024)
    private String nurseNote;

    /** 家属是否已提前请假 */
    private Boolean earlyLeaveApproved = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NoShowStatus status = NoShowStatus.REPORTED;

    // ===== 卫生院审核 =====
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private NoShowDecision decision;

    /** 核定空跑费金额（保留/部分减免时使用） */
    @Column(precision = 10, scale = 2)
    private BigDecimal approvedFee;

    /** 扣除服务次数 */
    private Integer deductServiceCount = 0;

    /** 调整后的下一次巡诊时间 */
    private LocalDateTime adjustedNextVisitAt;

    /** 被调整的下一次巡诊任务 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjusted_visit_id")
    private Visit adjustedVisit;

    @Column(length = 1024)
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private SysUser reviewer;

    private LocalDateTime reviewedAt;

    /** 是否已计入账单（防止重复收空跑费） */
    private Boolean billed = false;

    /** 患者临时送医联动生成的病床暂停单 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_pause_id")
    private BedPauseRecord bedPause;

    /** 家属补充说明提交截止时间（在此之前不生成费用） */
    private LocalDateTime excuseDeadline;

    @Transient
    public List<String> getDoorPhotoPaths() {
        return Jsons.readStringList(doorPhotoPathsJson);
    }

    @Transient
    public List<java.util.Map<String, Object>> getCallLogs() {
        return Jsons.readObjectList(callLogsJson);
    }
}
