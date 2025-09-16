package editor.controller;

import editor.model.DocumentModel;

public class DocumentServerController extends DocumentController {
    public DocumentServerController(DocumentModel model) {
        super(model);
    }

    // 현재 모델을 변경할 수 있도록 setter 추가
    public void setDocumentModel(DocumentModel model) {
        // 부모의 documentModel 필드가 protected이므로 직접 할당 가능
        this.documentModel = model;
    }

    // 현재 모델 반환 (getter)
    public DocumentModel getDocumentModel() {
        return this.documentModel;
    }

    public boolean acquireLock(long lineID, String clientID) {
        return this.documentModel.acquireLock(lineID, clientID);
    }

    public boolean releaseLock(long lineID, String clientID) {
        return this.documentModel.releaseLock(lineID, clientID);
    }

    public long findLockLineIDByClientID(String clientID) {
        return this.documentModel.findLockLineIDByClientID(clientID);
    }

    public void resetAllLockInfo() {
        this.documentModel.resetAllLockInfo();
    }
}
