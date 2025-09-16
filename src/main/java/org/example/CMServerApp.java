package org.example;

import editor.controller.DocumentServerController;
import editor.model.DocumentModel;
import editor.model.DocumentStorage;
import kr.ac.konkuk.ccslab.cm.entity.CMUser;
import kr.ac.konkuk.ccslab.cm.info.CMInteractionInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMServerStub;
import lombok.Getter;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

public class CMServerApp extends JFrame {
    private CMServerStub m_serverStub;
    private CMServerEventHandler m_eventHandler;
    private JTextPane m_outTextPane;
    private JTextArea rawArea;
    private JSplitPane splitPane;

    @Getter
    private final DocumentServerController controller;

    public CMServerApp() {
        DocumentModel model = new DocumentModel();
        DocumentStorage storge = new DocumentStorage();
        controller = new DocumentServerController(model);

        m_serverStub = new CMServerStub();
        m_eventHandler = new CMServerEventHandler(m_serverStub, this, storge);

        setTitle("Text Editor Server");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // 출력 화면
        m_outTextPane = new JTextPane();
        m_outTextPane.setEditable(false);

        StyledDocument doc = m_outTextPane.getStyledDocument();
        addStylesToDocument(doc);

        add(m_outTextPane, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(m_outTextPane,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        rawArea = new JTextArea();
        rawArea.setEditable(false);
        JScrollPane rawScroll = new JScrollPane(
                rawArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, rawScroll);
        splitPane.setResizeWeight(0.5);

        add(splitPane, BorderLayout.CENTER);
        setVisible(true);

        renderRaw();
    }

    public CMServerStub getServerStub() {
        return m_serverStub;
    }

    public CMServerEventHandler getServerEventHandler() {
        return m_eventHandler;
    }

    public static void main(String[] args) {
        CMServerApp server = new CMServerApp();
        CMServerStub cmStub = server.getServerStub();
        cmStub.setAppEventHandler(server.getServerEventHandler());
        cmStub.startCM();
    }

    private void addStylesToDocument(StyledDocument doc) {
        Style defStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

        Style regularStyle = doc.addStyle("regular", defStyle);
        StyleConstants.setFontFamily(regularStyle, "SansSerif");

        Style boldStyle = doc.addStyle("bold", defStyle);
        StyleConstants.setBold(boldStyle, true);
    }

    public void printMessage(String strText) {
        StyledDocument doc = m_outTextPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), strText, null);
            m_outTextPane.setCaretPosition(m_outTextPane.getDocument().getLength());

        } catch (BadLocationException e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    /**
     * DELETE 병합 시 직접 모델 업데이트 (중복 처리 방지)
     */
    public void directMergeLines(long currentLineId, long nextLineId, String mergedContent, String clientId) {
        // 1. 현재 라인 내용 직접 업데이트
        controller.getDocumentModel().forceUpdateContent(currentLineId, mergedContent);

        // 2. 다음 라인 직접 삭제
        controller.getDocumentModel().deleteLineAt(nextLineId, clientId);

        // 3. 서버 화면 갱신
        renderRaw();
    }

    // Lock - Interface Functions
    public boolean acquireServerLock(long lineID, String clientID) {
        return this.controller.acquireLock(lineID, clientID);
    }

    public boolean releaseServerLock(long lineID, String clientID) {
        return this.controller.releaseLock(lineID, clientID);
    }

    public long findLockLineIDByClientID(String clientID) {
        return this.controller.findLockLineIDByClientID(clientID);
    }

    public void renderRaw() {
        StringBuilder sb = new StringBuilder();
        controller.getAllLines().forEach(line -> sb
                .append(line.getLineID()).append(": ")
                .append(line.getContent()).append(" [lock=")
                .append(line.isLocked()).append(", id=")
                .append(line.getLockClientID()).append("]\n")
        );
        rawArea.setText(sb.toString());
    }

    public CMUser getMyself() {
        CMInteractionInfo interactionInfo = m_serverStub.getCMInfo().getInteractionInfo();
        return interactionInfo.getMyself();
    }

    public void insertText(long lineID, String content, int position) {
        controller.insertText(lineID, content, position);
        renderRaw();
    }

    public void editLine(long lineID, String content, String clientID) {
        System.out.println("editLine");

        controller.editLine(lineID, content, clientID);
        renderRaw();
    }

    public void insertLineAfter(long lineID, String content, String clientId) {
        System.out.println("insertLineAfter");
        controller.insertLineAfter(lineID, content, clientId);
        renderRaw();
    }

//    public void insertLineBefore(long lineID) {
//        System.out.println("insertLineBefore");
//        controller.insertLineBefore(lineID);
//        renderRaw();
//    }

    public void splitLine(long lineID, long splitIndex, String clientId) {
        System.out.println("splitLine");
        controller.splitLine(lineID, splitIndex, clientId);
        renderRaw();
    }

    public void deleteLine(long lineID, String clientId) {
        System.out.println("deleteLine");
        controller.deleteLine(lineID, clientId);
        renderRaw();
    }

    public boolean hasLock(long lineId, String clientId){
        System.out.println("hasLock");
        long lockLineId = controller.findLockLineIDByClientID(clientId);
        if(lockLineId == -1){
            return false;
        }
        return lockLineId == lineId;
    }

    public String getSerializedContents() {
        return controller.getSerializedContents();
    }

    public long getTopLineId() {
        return controller.getTopLineId();
    }

    public void releaseServerLockAfterLogout(String clientId) {
        long lineId = controller.findLockLineIDByClientID(clientId);
        releaseServerLock(lineId, clientId);
        renderRaw();
    }
}
