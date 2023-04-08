package raf.bolnica1.infirmary.services;

import raf.bolnica1.infirmary.dto.externalLabService.PrescriptionCreateDto;
import raf.bolnica1.infirmary.dto.prescription.PrescriptionReceiveDto;

public interface PrescriptionSendService {

    void createPrescription(PrescriptionReceiveDto prescriptionReceiveDto);

    void sendPrescription(PrescriptionCreateDto prescriptionCreateDto);

}
