package cn.townhc.homebed.domain.entity;

import cn.townhc.homebed.domain.enums.DomainEnums.FamilyExcuseReason;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

import cn.townhc.homebed.common.Jsons;

/**
 * 家属对“上门未遇”的补充说明。
 * 平台在生成空跑费用/扣次前，给家属一次申辩/解释机会（如临时急诊、手机没电、地址变更）。
 * 卫生院据此审核决定减免、保留或调整下一次巡诊。
 */
@Getter
@Setter
@Entity
@Table(name = "family_excuse")
public class FamilyExcuse extends BaseEntity {
    @Column(nullable = false, unique = true, length = 32)
    private String excuseNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "no_show_id")
    private NoShowRecord noShow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    /** 提交说明的家属账号 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submit_user_id")
    private SysUser submitUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FamilyExcuseReason reason;

    /** 家属书面说明 */
    @Column(nullable = false, length = 1024)
    private String content;

    /** 佐证材料路径 JSON（急诊票据/通话记录截图等） */
    @JsonIgnore
    @Column(length = 2048)
    private String evidencePathsJson;

    private LocalDateTime submittedAt;

    /** 家属期望的处置（供卫生院参考）：减免/改约/承担空跑费 */
    @Column(length = 256)
    private String expectedHandling;

    @Transient
    public List<String> getEvidencePaths() {
        return Jsons.readStringList(evidencePathsJson);
    }
}
