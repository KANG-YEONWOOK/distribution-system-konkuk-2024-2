package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import editor.model.DocumentModel;
import editor.model.DocumentStorage;
import kr.ac.konkuk.ccslab.cm.entity.CMUser;
import kr.ac.konkuk.ccslab.cm.event.*;
import kr.ac.konkuk.ccslab.cm.event.handler.CMAppEventHandler;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMServerStub;
import org.example.dto.EventContent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class CMServerEventHandler implements CMAppEventHandler {
    private CMServerStub m_serverStub;

    private CMServerApp m_serverApp;

    private DocumentStorage m_storage;

    public CMServerEventHandler(CMServerStub serverStub, CMServerApp serverApp, DocumentStorage storage) {
        m_serverStub = serverStub;
        m_serverApp = serverApp;
        m_storage = storage;
    }

    @Override
    public void processEvent(CMEvent cme) {
        switch (cme.getType()) {
            case CMInfo.CM_SESSION_EVENT:
                System.out.println("[EVENT HANDLER] CM_SESSION_EVENT received");
                processSessionEvent(cme);
                break;

            case CMInfo.CM_DUMMY_EVENT:
                processDummyEvent(cme);
                break;

            case CMInfo.CM_USER_EVENT:
                System.out.println("[EVENT HANDLER] CM_USER_EVENT received");
                processUserEvent(cme);
                break;

            default:
                return;
        }
    }

    public void processUserEvent(CMEvent cme) {
        CMUserEvent event = (CMUserEvent) cme;

        long lineId;
        switch (event.getStringID()) {
            // todo 클라로부터 온 이벤트를 처리하고 다른 클라에게 브로드캐스트
            case "NEW_LINE_AFTER_REQUEST":
                handleNewLineAfterRequest(event);
                break;
            case "NEW_SPLIT_LINE_REQUEST":
                handleNewSplitLineRequest(event);
                break;
            case "ON_EDIT_REQUEST":
                handleOnEditRequest(event);
                break;
            case "DELETE_LINE_REQUEST":
                handleDeleteLineRequest(event);
                break;
            case "MERGE_NEXT_LINE_REQUEST":
                handleMergeNextLineRequest(event);
                break;
            case "LOCK_MOVE_REQUEST": {
                System.out.println("[EVENT HANDLER] LOCK_MOVE_REQUEST received");

                String clientID = event.getEventField(CMInfo.CM_STR, "client_id");
                long lineID = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "line_id"));
                long oldLineID = -1L;

                m_serverApp.printMessage("[" + clientID + "] requested to move a lock at line " + lineID + "\n");
                System.out.println("clientID: " + clientID + ", lineID: " + lineID + ", oldLineID: " + oldLineID);

                // lineID == -1 -> release만 요청
                if (clientID.equals("")) {
                    System.out.println("[ERROR] client id is invalid");
                }

                // 1. lock 가지고 있는지 체크
                oldLineID = this.m_serverApp.findLockLineIDByClientID(clientID);
                System.out.println("oldLineID: " + oldLineID);

                // 2. Lock release
                if (oldLineID >= 0L) {
                    boolean releaseResult = this.m_serverApp.releaseServerLock(oldLineID, clientID);

                    if (releaseResult) {
                        m_serverApp.printMessage("[" + clientID + "] released a lock at line " + oldLineID + "\n");
                        System.out.println("[LOG] lock released successfully at line " + oldLineID);
                    } else {
                        m_serverApp.printMessage("[" + clientID + "] failed to release a lock at line " + oldLineID + "\n");
                        System.out.println("[LOG] failed to release lock at line " + oldLineID);
                        // 처리 실패
                        break;
                    }
                }

                // 3. Lock acquire
                boolean acquireResult = true;

                if (lineID >= 0L) {
                    acquireResult = this.m_serverApp.acquireServerLock(lineID, clientID);

                    if (acquireResult) {
                        m_serverApp.printMessage("[" + clientID + "] acquired a lock at line " + lineID + "\n");
                        System.out.println("[LOG] lock acquired successfully at line " + lineID);
                    } else {
                        m_serverApp.printMessage("[" + clientID + "] failed to acquire a lock at line " + lineID + "\n");
                        System.out.println("[LOG] failed to acquire a lock at line " + lineID);
                    }
                }

                // 4. 결과 반환
                // 4.1 lock 얻음
                if (acquireResult) {
                    // oldLineID == -1인 경우 갖고 있던 lock이 없어 새 lock만 획득
                    // oldLineID >= 0인 경우 release 후 lock 획득
                    if (broadcastLockResponse(event, clientID, oldLineID, lineID)) {
                        // lineID == -1 이면 release만 요청
                        if (lineID == -1L && oldLineID >= 0L) {
                            m_serverApp.printMessage("[" + clientID + "] released a lock at " + oldLineID + "\n");
                            System.out.println("[LOG] lock released successfully at line " + oldLineID);
                        }
                        // 이외
                        else {
                            m_serverApp.printMessage("[" + clientID + "] lock moved from " + oldLineID + " to " + lineID + " - broadcast success\n");
                            System.out.println("[SUCCESS - LOCK_MOVE_RESPONSE]  lock moved from " + oldLineID + " to " + lineID);
                        }
                    } else {
                        m_serverApp.printMessage("[" + clientID + "] lock moved from " + oldLineID + " to " + lineID + " - broadcast fail\n");
                        System.out.println("[FAILED - LOCK_MOVE_RESPONSE] lock moved from " + oldLineID + " to " + lineID);
                    }
                }
                // 4.2 lock 얻기 실패
                else {
                    // 4.2.1 release 한 경우 알려줘야 함
                    if (oldLineID >= 0L) {
                        // oldLineID release 성공, 새로 얻은 lock 없음 (-1)
                        if (broadcastLockResponse(event, clientID, oldLineID, -1L)) {
                            m_serverApp.printMessage("[" + clientID + "] release lock at " + oldLineID + " and failed to get lock at " + lineID + " broadcast success\n");
                            System.out.println("[SUCCESS - LOCK_MOVE_RESPONSE]  lock released " + oldLineID + " but failed to acquire at " + lineID);
                        } else {
                            m_serverApp.printMessage("[" + clientID + "] release lock at " + oldLineID + " and failed to get lock at " + lineID + " broadcast fail\n");
                            System.out.println("[FAILED - LOCK_MOVE_RESPONSE]  lock released " + oldLineID + " but failed to acquire at " + lineID);
                        }
                    } else {
                        System.out.println("release failed, acquire failed");
                    }
                }

                m_serverApp.renderRaw();
                break;
            }
            case "REQUEST_SAVE_DOCUMENT": {
                String docId = UUID.randomUUID().toString();
                String title = event.getEventField(CMInfo.CM_STR, "title");
                System.out.println("[SERVER] Saving document: id=" + docId + ", title=" + title);
                
                // 현재 controller의 model을 복사해서 저장
                DocumentModel modelCopy = m_serverApp.getController().getDocumentModel().copy();
                m_storage.saveDocument(docId, title, modelCopy);
                System.out.println("[SERVER] Document saved successfully");
                
                // 저장 후 모든 클라이언트에게 문서 목록 브로드캐스트
                List<DocumentStorage.DocumentMeta> list = m_storage.listDocuments();
                System.out.println("[SERVER] Current document list size: " + list.size());
                
                ObjectMapper om = new ObjectMapper();
                String docsJson = "";
                try {
                    docsJson = om.writeValueAsString(list);
                    System.out.println("[SERVER] Serialized document list: " + docsJson);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    docsJson = "[]";
                }

                CMUserEvent resp = new CMUserEvent();
                resp.setStringID("RESPONSE_LIST_DOCUMENTS");
                resp.setEventField(CMInfo.CM_STR, "documents", docsJson);
                boolean broadcastResult = m_serverStub.broadcast(resp);
                System.out.println("[SERVER] Document list broadcast result: " + broadcastResult);
                break;
            }
            case "REQUEST_LIST_DOCUMENTS": {
                System.out.println("[SERVER] Received document list request");
                // 목록 직렬화해서 클라에 전송
                List<DocumentStorage.DocumentMeta> list = m_storage.listDocuments();
                System.out.println("[SERVER] Sending document list with size: " + list.size());
                
                ObjectMapper om = new ObjectMapper();
                String docsJson = "";
                try {
                    docsJson = om.writeValueAsString(list);
                    System.out.println("[SERVER] Serialized document list: " + docsJson);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    docsJson = "[]";
                }

                CMUserEvent resp = new CMUserEvent();
                resp.setStringID("RESPONSE_LIST_DOCUMENTS");
                resp.setEventField(CMInfo.CM_STR, "documents", docsJson);
                boolean sendResult = m_serverStub.send(resp, event.getHandlerSession());
                System.out.println("[SERVER] Document list send result: " + sendResult);
                break;
            }
            case "REQUEST_LOAD_DOCUMENT": {
                String docId = event.getEventField(CMInfo.CM_STR, "docId");
                DocumentModel model = m_storage.loadDocument(docId);
                // 현재 작업 문서 교체
                m_serverApp.getController().setDocumentModel(model.copy());
                m_serverApp.getController().resetAllLockInfo();
                // 전체 내용 클라이언트에 브로드캐스트(기존 PUSH_DOCUMENT_MODEL)
                broadcastCurrentContents();
                break;
            }
        }
    }

    private void handleMergeNextLineRequest(CMUserEvent event) {
        String clientId = event.getEventField(CMInfo.CM_STR, "clientId");
        long currentLineId = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "currentLineId"));
        long nextLineId = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "nextLineId"));
        String mergedContent = event.getEventField(CMInfo.CM_STR, "mergedContent");

        m_serverApp.printMessage("[" + clientId + "] MERGE_NEXT_LINE_REQUEST received\n");

        // 현재 라인에 대한 락이 있는지 확인
        if (!m_serverApp.hasLock(currentLineId, clientId)) {
            System.out.println("Client " + clientId + " does not have lock on line " + currentLineId);
            return;
        }

        // 서버에서 직접 모델 업데이트 (중복 처리 방지)
        m_serverApp.directMergeLines(currentLineId, nextLineId, mergedContent, clientId);

        // 병합 이벤트를 다른 클라이언트들에게 브로드캐스트
        EventContent eventContent = EventContent.builder()
                .type("merge_next_line")
                .lineId(currentLineId)
                .splitIndex(nextLineId)  // splitIndex를 nextLineId로 재활용
                .content(mergedContent)
                .clientId(clientId)
                .timestamp(LocalDateTime.now())
                .build();

        broadcastEvent(eventContent);
    }

    private void handleDeleteLineRequest(CMUserEvent event) {
        String clientId = event.getEventField(CMInfo.CM_STR, "clientId");
        long lineId = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "lineId"));

        m_serverApp.printMessage("[" + clientId + "] DELETE_LINE_REQUEST received\n");

        if(!m_serverApp.hasLock(lineId, clientId)) return;

        m_serverApp.deleteLine(lineId, clientId);

        EventContent eventContent = EventContent.builder()
                .type("delete_line")
                .lineId(lineId)
                .clientId(clientId)
                .timestamp(LocalDateTime.now())
                .build();

        broadcastEvent(eventContent);
    }

    private void handleOnEditRequest(CMUserEvent event) {
        String clientId = event.getEventField(CMInfo.CM_STR, "clientId");
        String content = event.getEventField(CMInfo.CM_STR, "content");
        long lineId = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "lineId"));

        m_serverApp.printMessage("[" + clientId + "] ON_EDIT_REQUEST received\n");

        // 락이 없으면 요청을 무시
        if(!m_serverApp.hasLock(lineId, clientId)) return;

        // 처리 후에 브로드캐스트
        m_serverApp.editLine(lineId, content, clientId);

        EventContent eventContent = EventContent.builder()
                .type("edit")
                .lineId(lineId)
                .content(content)
                .clientId(clientId)
                .timestamp(LocalDateTime.now())
                .build();
        broadcastEvent(eventContent);
    }

    private void handleNewLineAfterRequest(CMUserEvent event) {
        String clientId = event.getEventField(CMInfo.CM_STR, "clientId");

        m_serverApp.printMessage("[" + clientId + "] NEW_LINE_AFTER_REQUEST received\n");

        long lineId = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "lineId"));

        m_serverApp.insertLineAfter(lineId, "", clientId);

        EventContent eventContent = EventContent.builder()
                .type("new_line_after")
                .lineId(lineId)
                .clientId(clientId)
                .timestamp(LocalDateTime.now())
                .build();

        broadcastEvent(eventContent);
    }

    private void handleNewSplitLineRequest(CMUserEvent event) {
        String clientId = event.getEventField(CMInfo.CM_STR, "clientId");

        m_serverApp.printMessage("[" + clientId + "] NEW_SPLIT_LINE_REQUEST received\n");

        long lineId = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "lineId"));
        long splitIndex = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "splitIndex"));

        m_serverApp.splitLine(lineId, splitIndex, clientId);

        EventContent eventContent = EventContent.builder()
                .type("new_line_split")
                .lineId(lineId)
                .splitIndex(splitIndex)
                .clientId(clientId)
                .timestamp(LocalDateTime.now())
                .build();

        broadcastEvent(eventContent);
    }

    private void broadcastEvent(EventContent eventContent) {
        CMUser myself = m_serverStub.getMyself();

        ObjectMapper objectMapper = new ObjectMapper();
        String dummyEventContent = "";
        try {
            dummyEventContent = objectMapper.writeValueAsString(eventContent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        System.out.println("broadcast message" + dummyEventContent + " from " + myself.getName());

        CMDummyEvent de = new CMDummyEvent();
        de.setHandlerSession(myself.getCurrentSession());
        de.setHandlerGroup(myself.getCurrentGroup());
        de.setDummyInfo(dummyEventContent);
        m_serverStub.broadcast(de);
    }

    // lock 결과 broadcast (송신자 포함)
    private boolean broadcastLockResponse(CMUserEvent receivedEvent, String clientID, long oldLineID, long lineID) {
        CMUserEvent responseEvent = new CMUserEvent();
        responseEvent.setStringID("LOCK_MOVE_RESPONSE");
        responseEvent.setHandlerSession(receivedEvent.getHandlerSession());
        responseEvent.setHandlerGroup(receivedEvent.getHandlerGroup());
        responseEvent.setEventField(CMInfo.CM_STR, "client_id", clientID);
        responseEvent.setEventField(CMInfo.CM_LONG, "old_line_id", String.valueOf(oldLineID));
        responseEvent.setEventField(CMInfo.CM_LONG, "line_id", String.valueOf(lineID));

        return m_serverStub.broadcast(responseEvent);
    }

    private void processSessionEvent(CMEvent cme) {
        CMSessionEvent se = (CMSessionEvent) cme;
        switch (se.getID()) {
            case CMSessionEvent.LOGIN:
                m_serverApp.printMessage("[" + se.getUserName() + "] requests login\n");
                broadcastCurrentContents();
                break;
            case CMSessionEvent.LOGOUT:
                m_serverApp.printMessage("[" + se.getUserName() + "] logs out\n");
                // todo 로그아웃 시 락을 풀고 브로드캐스트
                m_serverApp.releaseServerLockAfterLogout(se.getUserName());
                broadcastLogoutLockResponse(se);
            default:
                return;
        }
    }

    private void broadcastLogoutLockResponse(CMSessionEvent receivedEvent) {
        CMUserEvent responseEvent = new CMUserEvent();
        responseEvent.setStringID("RELEASE_LOGOUT_USER_LOCK");
        responseEvent.setHandlerSession(receivedEvent.getHandlerSession());
        responseEvent.setHandlerGroup(receivedEvent.getHandlerGroup());
        responseEvent.setEventField(CMInfo.CM_STR, "clientId", receivedEvent.getUserName());

        if (m_serverStub.broadcast(responseEvent)){
            System.out.println("문서 내용 브로드캐스트 완료");
            m_serverApp.printMessage("LOGOUT_USER_LOCK_RELEASE succeeded!!\n");
        }
        else {
            System.out.println("문서 내용 브로드캐스트 완료");
        }
    }

    private void broadcastCurrentContents() {
        System.out.println("[broadcasstCurrentContents]");

        // 사용자 정의 이벤트 생성
        CMUserEvent userEvent = new CMUserEvent();
        userEvent.setStringID("PUSH_DOCUMENT_MODEL"); // 이벤트 구분자

        // 내용 직렬화
        String serializedContents = m_serverApp.getSerializedContents();
        long topLineId = m_serverApp.getTopLineId();

        System.out.println(" >> serializedContents starts");
        System.out.println(" " + serializedContents);
        System.out.println(" >> serializedContents ends" );

        userEvent.setEventField(CMInfo.CM_STR, "serializedContents", serializedContents);
        userEvent.setEventField(CMInfo.CM_LONG, "topLineId", String.valueOf(topLineId));

        // 3. 브로드캐스트
        boolean broadcastSuccess = m_serverStub.broadcast(userEvent);
        if(broadcastSuccess){
            System.out.println("문서 내용 브로드캐스트 완료");
            m_serverApp.printMessage("PUSH_CURRENT_CONTENTS succeeded!!\n");
        }
        else{
            System.out.println("문서 내용 브로드캐스트 완료");
        }
    }

    // todo 주석 처리
    private void processDummyEvent(CMEvent cme) {
        CMDummyEvent de = (CMDummyEvent) cme;
        String info = de.getDummyInfo();

        String senderId = de.getSender();
        String myId = m_serverApp.getMyself().getName();
        if (senderId.equals(myId)) {
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        EventContent eventContent = null;
        try {
            eventContent = objectMapper.readValue(info, EventContent.class);
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

        switch (eventContent.getType()) {
//            case "edit":
//                m_serverApp.editLine(eventContent.getLineId(), eventContent.getContent(), eventContent.getClientId());
//                break;
//            case "insert_char":
//                m_serverApp.insertText(eventContent.getLineId(), eventContent.getContent(), eventContent.getPosition());
//                break;
//            case "new_line_after":
//                long newLineId = m_serverApp.insertLineAfter(eventContent.getLineId(), "", eventContent.getClientId());
//                CMUserEvent userEvent = new CMUserEvent();
//                userEvent.setHandlerGroup(cme.getHandlerGroup());
//                userEvent.setHandlerSession(cme.getHandlerSession());
//                broadcastLockResponse(userEvent, eventContent.getClientId(), eventContent.getLineId(), newLineId);
//                break;
//            case "new_line_before":
//                m_serverApp.insertLineBefore(eventContent.getLineId());
//                break;
//            case "delete_line":
//                m_serverApp.deleteLine(eventContent.getLineId());
//                break;
            default:
                System.out.println("unsupported type!!");
        }
    }
}
