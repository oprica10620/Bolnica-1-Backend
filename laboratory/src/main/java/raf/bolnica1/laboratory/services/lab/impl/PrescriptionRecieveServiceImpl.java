package raf.bolnica1.laboratory.services.lab.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import raf.bolnica1.laboratory.domain.constants.OrderStatus;
import raf.bolnica1.laboratory.domain.constants.PrescriptionStatus;
import raf.bolnica1.laboratory.domain.lab.AnalysisParameter;
import raf.bolnica1.laboratory.domain.lab.LabWorkOrder;
import raf.bolnica1.laboratory.domain.lab.ParameterAnalysisResult;
import raf.bolnica1.laboratory.domain.lab.Prescription;
import raf.bolnica1.laboratory.dto.lab.PatientDto;
import raf.bolnica1.laboratory.dto.prescription.*;
import raf.bolnica1.laboratory.mappers.PrescriptionMapper;
import raf.bolnica1.laboratory.mappers.PrescriptionRecieveMapper;
import raf.bolnica1.laboratory.repository.AnalysisParameterRepository;
import raf.bolnica1.laboratory.repository.LabWorkOrderRepository;
import raf.bolnica1.laboratory.repository.ParameterAnalysisResultRepository;
import raf.bolnica1.laboratory.repository.PrescriptionRepository;
import raf.bolnica1.laboratory.services.lab.LabWorkOrdersService;
import raf.bolnica1.laboratory.services.lab.PrescriptionRecieveService;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class PrescriptionRecieveServiceImpl implements PrescriptionRecieveService {

    private final ParameterAnalysisResultRepository parameterAnalysisResultRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AnalysisParameterRepository analysisParameterRepository;
    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionRecieveMapper prescriptionrecieveMapper;

    private final LabWorkOrdersService labWorkOrdersService;
    private final LabWorkOrderRepository labWorkOrderRepository;

    @Override
    public void createPrescription(PrescriptionCreateDto dto) {
        Prescription prescription = prescriptionMapper.toEntity(dto);
        prescription = prescriptionRepository.save(prescription);

        LabWorkOrder labWorkOrder=labWorkOrdersService.createWorkOrder(prescription);

        for(PrescriptionAnalysisDto prescriptionAnalysisDto : dto.getPrescriptionAnalysisDtos()){
            Long analysisId=prescriptionAnalysisDto.getAnalysisId();
            for(Long parameterId:prescriptionAnalysisDto.getParametersIds()){
                AnalysisParameter analysisParameter=analysisParameterRepository.findAnalysisParameterByAnalysisIdAndParameterId(analysisId,parameterId);

                ParameterAnalysisResult parameterAnalysisResult=new ParameterAnalysisResult();
                parameterAnalysisResult.setAnalysisParameter(analysisParameter);
                parameterAnalysisResult.setLabWorkOrder(labWorkOrder);
                parameterAnalysisResultRepository.save(parameterAnalysisResult);
            }
        }
    }

    @Override
    public void updatePrescription(PrescriptionUpdateDto dto) {
        Prescription prescription = prescriptionRepository.findById(dto.getId()).orElse(null);
        if(prescription != null && prescription.getStatus().equals(PrescriptionStatus.NEREALIZOVAN)){
            prescription = prescriptionMapper.toEntityUpdate(dto, prescription);


            LabWorkOrder labWorkOrder = labWorkOrderRepository.findByPrescription(prescription.getId()).orElse(null);
            if(labWorkOrder != null && labWorkOrder.getStatus().equals(OrderStatus.NEOBRADJEN)){
                parameterAnalysisResultRepository.deleteAll(parameterAnalysisResultRepository.findParameterAnalysisResultsByLabWorkOrderId(labWorkOrder.getId()));

                for(PrescriptionAnalysisDto prescriptionAnalysisDto : dto.getPrescriptionAnalysisDtos()) {
                    Long analysisId = prescriptionAnalysisDto.getAnalysisId();
                    for (Long parameterId : prescriptionAnalysisDto.getParametersIds()) {
                        AnalysisParameter analysisParameter = analysisParameterRepository.findAnalysisParameterByAnalysisIdAndParameterId(analysisId, parameterId);

                        ParameterAnalysisResult parameterAnalysisResult = new ParameterAnalysisResult();
                        parameterAnalysisResult.setAnalysisParameter(analysisParameter);
                        parameterAnalysisResult.setLabWorkOrder(labWorkOrder);
                        parameterAnalysisResultRepository.save(parameterAnalysisResult);
                    }
                }
            }
            prescriptionRepository.save(prescription);
        }
    }

    @Override
    public void deletePrescription(Long id, String lbz) {
        Prescription prescription = prescriptionRepository.findById(id).orElse(null);
        if(prescription == null)
            return;

        if(!prescription.getDoctorLbz().equals(lbz)){
            return;
        }

        LabWorkOrder labWorkOrder = labWorkOrderRepository.findByPrescription(prescription.getId()).orElse(null);
        if(labWorkOrder != null) {
            labWorkOrdersService.deleteWorkOrder(labWorkOrder);
        }
        prescriptionRepository.delete(prescription);
    }

    @Override
    public Page<PrescriptionDto> findPrescriptionsForPatient(String lbp, String doctorLbz, int page, int size) {
        List<Prescription> prescriptions = prescriptionRepository.findPrescriptionsByLbpAndDoctorLbz(lbp, doctorLbz, PrescriptionStatus.NEREALIZOVAN);
        List<PrescriptionDto> prescriptionDtos = new ArrayList<>();
        for(Prescription prescription : prescriptions){
            prescriptionDtos.add(prescriptionrecieveMapper.toPrescriptionDto(prescription));
        }
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, prescriptionDtos.size());

        List<PrescriptionDto> sublist = prescriptionDtos.subList(startIndex, endIndex);

        Page<PrescriptionDto> paged = new PageImpl<>(sublist, PageRequest.of(page, size), prescriptionDtos.size());
        return paged;
    }

    @Override
    public PrescriptionDoneDto findPrescription(Long id) {
        Prescription prescription = prescriptionRepository.findPrescriptionById(id);
        LabWorkOrder labWorkOrder = labWorkOrderRepository.findByPrescription(prescription.getId()).orElse(null);

        PrescriptionDoneDto prescriptionDoneDto = new PrescriptionDoneDto();
        prescriptionDoneDto.setId(prescription.getId());
        prescriptionDoneDto.setDate(new Date(prescription.getCreationDateTime().getTime()));
        prescriptionDoneDto.setComment(prescription.getComment());
        prescriptionDoneDto.setDoctorLbz(prescription.getDoctorLbz());
        prescriptionDoneDto.setLbp(prescription.getLbp());
        prescriptionDoneDto.setDepartmentFromId(prescription.getDepartmentFromId());
        prescriptionDoneDto.setDepartmentToId(prescription.getDepartmentToId());
        prescriptionDoneDto.setType("LABORATORIJA");
        prescriptionDoneDto.setPrescriptionStatus(prescription.getStatus());
        List<PrescriptionAnalysisNameDto> list = new ArrayList<>();

        boolean flag = false;
        if(labWorkOrder != null){
            List<ParameterAnalysisResult> parameterAnalysisResults = parameterAnalysisResultRepository.findParameterAnalysisResultsByLabWorkOrderId(labWorkOrder.getId());
            for(ParameterAnalysisResult parameterAnalysisResult : parameterAnalysisResults){
                flag = false;
                for(PrescriptionAnalysisNameDto prescriptionAnalysisNameDto : list){
                    if(prescriptionAnalysisNameDto.getAnalysisName().equals(parameterAnalysisResult.getAnalysisParameter().getLabAnalysis().getAnalysisName())){
                        prescriptionAnalysisNameDto.getParameters().add(new ParameterDto(parameterAnalysisResult.getAnalysisParameter().getParameter().getParameterName(), parameterAnalysisResult.getResult(), parameterAnalysisResult.getAnalysisParameter().getParameter().getLowerLimit(), parameterAnalysisResult.getAnalysisParameter().getParameter().getUpperLimit()));
                        flag = true;
                        break;
                    }
                }
                if(!flag){
                    PrescriptionAnalysisNameDto prescriptionAnalysisNameDto = new PrescriptionAnalysisNameDto();
                    prescriptionAnalysisNameDto.setAnalysisName(parameterAnalysisResult.getAnalysisParameter().getLabAnalysis().getAnalysisName());
                    prescriptionAnalysisNameDto.setParameters(new ArrayList<>());
                    prescriptionAnalysisNameDto.getParameters().add(new ParameterDto(parameterAnalysisResult.getAnalysisParameter().getParameter().getParameterName(), parameterAnalysisResult.getResult(), parameterAnalysisResult.getAnalysisParameter().getParameter().getLowerLimit(), parameterAnalysisResult.getAnalysisParameter().getParameter().getUpperLimit()));
                    list.add(prescriptionAnalysisNameDto);
                }
            }
        }
        prescriptionDoneDto.setParameters(list);
        return prescriptionDoneDto;
    }

    @Override
    public ArrayList<PrescriptionDto> findPrescriptionsForPatientRest(String lbp, String doctorLbz) {
        List<Prescription> prescriptions = prescriptionRepository.findPrescriptionsByLbpAndDoctorLbz(lbp, doctorLbz, PrescriptionStatus.NEREALIZOVAN);
        List<PrescriptionDto> prescriptionDtos = new ArrayList<>();
        for(Prescription prescription : prescriptions){
            prescriptionDtos.add(prescriptionrecieveMapper.toPrescriptionDto(prescription));
        }
        return (ArrayList<PrescriptionDto>) prescriptionDtos;
    }

    @Override
    public Page<PrescriptionDto> findPrescriptionsForPatientNotRealized(String lbp, Integer page, Integer size) {
        List<Prescription> prescriptions = prescriptionRepository.findPrescriptionsByLbpNotRealized(lbp, PrescriptionStatus.NEREALIZOVAN);
        List<PrescriptionDto> prescriptionDtos = new ArrayList<>();
        for(Prescription prescription : prescriptions){
            prescriptionDtos.add(prescriptionrecieveMapper.toPrescriptionDto(prescription));
        }
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, prescriptionDtos.size());

        List<PrescriptionDto> sublist = prescriptionDtos.subList(startIndex, endIndex);

        Page<PrescriptionDto> paged = new PageImpl<>(sublist, PageRequest.of(page, size), prescriptionDtos.size());
        return paged;
    }

    @Override
    public Page<PatientDto> findPatients(int page, int size) {
        List<Prescription> prescriptions = prescriptionRepository.findPrescriptionsNotRealized(PrescriptionStatus.NEREALIZOVAN);
        List<PatientDto> patientDtos = new ArrayList<>();
        for(Prescription prescription : prescriptions){
            patientDtos.add(new PatientDto(prescription.getLbp(), prescription.getId()));
        }
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, patientDtos.size());

        List<PatientDto> sublist = patientDtos.subList(startIndex, endIndex);

        Page<PatientDto> paged = new PageImpl<>(sublist, PageRequest.of(page, size), patientDtos.size());
        return paged;
    }
}
