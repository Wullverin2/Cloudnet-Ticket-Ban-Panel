package de.speed.ticketconsolecloudban.appeal;

public interface EvidenceStorage {

  StoredEvidence store(String appealId, AppealMultipartForm.UploadFile file);
}
