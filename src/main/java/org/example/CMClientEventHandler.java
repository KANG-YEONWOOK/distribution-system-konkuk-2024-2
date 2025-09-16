package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import editor.model.DocumentStorage;
import editor.model.LockStatus;
import editor.model.TextLine;
import kr.ac.konkuk.ccslab.cm.event.CMDummyEvent;
import kr.ac.konkuk.ccslab.cm.event.CMEvent;
import kr.ac.konkuk.ccslab.cm.event.CMSessionEvent;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.event.handler.CMAppEventHandler;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;
import org.example.dto.EventContent;
import org.example.util.CustomGsonUtils;

import javax.swing.*;
import java.util.List;

public class CMClientEventHandler implements CMAppEventHandler {
    private CMClientStub m_clientStub;

    private CMClientApp m_clientApp;

    public CMClientEventHandler(CMClientStub m_clientStub, CMClientApp m_clientApp) {
        this.m_clientStub = m_clientStub;
        this.m_clientApp = m_clientApp;
    }

    @Override
    public void processEvent(CMEvent cme) {
        switch (cme.getType()) {
            case CMInfo.CM_SESSION_EVENT:
                processSessionEvent(cme);
                break;

            case CMInfo.CM_DUMMY_EVENT:
                processDummyEvent(cme);
                break;

            case CMInfo.CM_USER_EVENT:
                processUserEvent(cme);
                break;

            default:
                break;
        }
    }

    private void processUserEvent(CMEvent cme) {
        CMUserEvent event = (CMUserEvent) cme;

        switch (event.getStringID()) {
            case "PUSH_DOCUMENT_MODEL":
                pushDocumentModel(event);
                break;
            case "RELEASE_LOGOUT_USER_LOCK":
                releaseLogoutUserLock(event);
                break;
            case "LOCK_MOVE_RESPONSE": {
                String clientID = event.getEventField(CMInfo.CM_STR, "client_id");
                long oldLineID = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "old_line_id"));
                long lineID = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "line_id"));

                // lock 요청한 클라이언트가 자신이라면 controller.lockAcquiredLineID 업데이트
                if (clientID.equals(m_clientApp.getClientID())) {
                    m_clientApp.setLockAcquiredLineID(lineID);
                }

                // case 1. lock acquire 성공 lineID >= 0
                if (lineID >= 0L) {
                    // case 1.1 lock release 없음 (갖고 있던 lock 없음) oldLineID == -1 -> 별도 처리 X

                    // case 1.2 lock release 성공 oldLineID >= 0
                    if (oldLineID >= 0L) {
                        // lock release
                        m_clientApp.setLockInfoByServer(oldLineID, LockStatus.RELEASE, clientID);
                    }

                    // lock acquire
                    m_clientApp.setLockInfoByServer(lineID, LockStatus.ACQUIRE, clientID);
                }
                // case 2. lock release 성공, acquire 실패 oldLineID >= 0, lineID == -1
                else if (oldLineID >= 0L) {
                    // oldLineID - lock release
                    m_clientApp.setLockInfoByServer(oldLineID, LockStatus.RELEASE, clientID);
                }

                // selectedLineID가 lock 실패한 상태인데 lock이 풀렸다면 acquire 재요청
                if (!clientID.equals(m_clientApp.getClientID()) && oldLineID == m_clientApp.getSelectedLineID() && m_clientApp.getLockAcquiredLineID() == -1L) {
                    m_clientApp.requestServerLock(m_clientApp.getSelectedLineID());
                }

                // render right area
                m_clientApp.renderRaw();
                break;
            }
            case "RESPONSE_LIST_DOCUMENTS": {
                System.out.println("[CLIENT] Received document list response");
                String docsJson = event.getEventField(CMInfo.CM_STR, "documents");
                System.out.println("[CLIENT] Received document list JSON: " + docsJson);
                
                ObjectMapper om = new ObjectMapper();
                try {
                    List<DocumentStorage.DocumentMeta> docs =
                            om.readValue(docsJson,
                                    om.getTypeFactory().constructCollectionType(List.class, DocumentStorage.DocumentMeta.class)
                            );
                    System.out.println("[CLIENT] Parsed " + docs.size() + " documents from JSON");
                    m_clientApp.updateDocumentSelect(docs);
                } catch (Exception ex) {
                    System.out.println("[CLIENT] Error parsing document list: " + ex.getMessage());
                    ex.printStackTrace();
                }
                break;
            }

            default:
                System.out.println("Unknown Event String ID: " + event.getStringID());
                break;
        }
    }

    private void releaseLogoutUserLock(CMUserEvent event) {
        System.out.println("releaseLogoutUserLock");
        String clientId = event.getEventField(CMInfo.CM_STR, "clientId");
        m_clientApp.releaseLockByClientId(clientId);
    }

    private void pushDocumentModel(CMUserEvent event) {
        long topLineId = Long.parseLong(event.getEventField(CMInfo.CM_LONG, "topLineId"));
        String serializedContents = event.getEventField(CMInfo.CM_STR, "serializedContents");

        Gson gson = CustomGsonUtils.CustomGson();
        List<TextLine> contents = gson.fromJson(serializedContents, new TypeToken<List<TextLine>>(){}.getType());

        m_clientApp.pushDocumentModel(topLineId, contents);
    }

    private void processSessionEvent(CMEvent cme) {
        CMSessionEvent se = (CMSessionEvent) cme;
        switch (se.getID()) {
            case CMSessionEvent.SESSION_REMOVE_USER:
//                System.out.println("[" + se.getUserName() + "] logs out");
                m_clientApp.setTextLabel("[" + se.getUserName() + "] 님이 로그아웃했습니다");
                break;

            case CMSessionEvent.SESSION_ADD_USER:
//                System.out.println("[" + se.getUserName() + "] logged in");
                m_clientApp.setTextLabel("[" + se.getUserName() + "] 님이 로그인했습니다.");
                break;

            default:
                break;
        }
    }

    private void processDummyEvent(CMEvent cme) {
        System.out.println("received dummy event !!");

        CMDummyEvent de = (CMDummyEvent) cme;
        String info = de.getDummyInfo();

        System.out.println(" " + de.getDummyInfo());


        ObjectMapper objectMapper = new ObjectMapper();
        EventContent eventContent = null;
        try {
            eventContent = objectMapper.readValue(info, EventContent.class);
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }

        String senderId = eventContent.getClientId();
        String myId = m_clientApp.getMyself().getName();
        if (senderId.equals(myId)) {
            return;
        }

        switch (eventContent.getType()) {
            case "edit":
                m_clientApp.editLine(eventContent.getLineId(), eventContent.getContent(), eventContent.getClientId());
                break;
//            case "insert_char":
//                m_clientApp.insertText(eventContent.getLineId(), eventContent.getContent(), eventContent.getPosition());
//                break;
            case "new_line_after":
                m_clientApp.insertLineAfter(eventContent.getLineId(), "", eventContent.getClientId());
                // lock 실패한 상태인데 lock이 풀렸다면 acquire 재요청
                break;
//            case "new_line_before":
//                m_clientApp.insertLineBefore(eventContent.getLineId());
//                break;
            case "new_line_split":
                System.out.println("received NEW_LINE_SPLIT from SERVER!!");
                m_clientApp.splitLine(eventContent.getLineId(), eventContent.getSplitIndex(), eventContent.getClientId());

                break;
            case "delete_line":
                m_clientApp.deleteLine(eventContent.getLineId(), eventContent.getClientId());
                break;
            case "merge_next_line":
                System.out.println("received MERGE_NEXT_LINE from SERVER!!");
                // splitIndex를 nextLineId로 재활용
                m_clientApp.mergeNextLine(eventContent.getLineId(), eventContent.getSplitIndex(),
                        eventContent.getContent(), eventContent.getClientId());
                break;
            default:
                System.out.println("unsupported type!!");
        }
    }

}
