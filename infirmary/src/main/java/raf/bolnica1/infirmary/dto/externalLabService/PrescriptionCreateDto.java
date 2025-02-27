package raf.bolnica1.infirmary.dto.externalLabService;

import lombok.Getter;
import lombok.Setter;
import raf.bolnica1.infirmary.domain.constants.PrescriptionStatus;
import raf.bolnica1.infirmary.domain.constants.PrescriptionType;

import java.sql.Timestamp;
import java.util.List;

@Getter
@Setter
public class PrescriptionCreateDto {
    private PrescriptionType type;
    private String doctorLbz;
    private Long departmentFromId;
    private Long departmentToId;
    private String lbp;
    private Timestamp creationDateTime;
    private PrescriptionStatus status;
    private String comment;
    private List<PrescriptionAnalysisDto> prescriptionAnalysisDtos;
}
