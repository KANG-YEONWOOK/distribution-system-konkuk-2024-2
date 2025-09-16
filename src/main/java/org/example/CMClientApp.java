package org.example;

import editor.controller.DocumentClientController;
import editor.model.DocumentModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import editor.model.DocumentStorage;
import editor.model.LockStatus;
import editor.model.TextLine;
import kr.ac.konkuk.ccslab.cm.entity.CMUser;
import kr.ac.konkuk.ccslab.cm.event.CMDummyEvent;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.info.CMInteractionInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;
import lombok.Getter;
import org.example.dto.EventContent;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import static java.lang.System.exit;

public class CMClientApp extends JFrame {
    @Getter
    private CMClientStub clientStub;
    private CMClientEventHandler m_eventHandler;
    JLabel textLabel;
//    JTextArea editorTextArea;

    final JTextArea leftArea = new JTextArea();
    final JTextPane lockPane = new JTextPane();
    final JTextArea rightArea = new JTextArea();
    private final DocumentClientController controller;
    private final JSplitPane splitPane;
    @Getter
    private JComboBox<DocumentStorage.DocumentMeta> documentSelect;

    public CMClientApp() {
        super("Text Editor Client");
        DocumentModel model = new DocumentModel();
        controller = new DocumentClientController(model, null, this);

        clientStub = new CMClientStub();
        m_eventHandler = new CMClientEventHandler(clientStub, this);

//        leftArea = new JTextArea();
//        rightArea = new JTextArea();
        rightArea.setEditable(false);

        // font
        leftArea.setFont(new Font("Arial", Font.PLAIN, 14));
        rightArea.setFont(new Font("Arial", Font.PLAIN, 14));
        rightArea.setHighlighter(null);

        lockPane.setEditable(false);
        lockPane.setHighlighter(null);
        lockPane.setOpaque(false);
        lockPane.setFont(new Font("Arial", Font.PLAIN, 14));
        lockPane.setMargin(new Insets(0,0,0,0));

        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        lockPane.getStyledDocument().setParagraphAttributes(
                0, lockPane.getDocument().getLength(), center, false);

        JSplitPane rawSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(lockPane),
                new JScrollPane(leftArea)
        );
        rawSplit.setDividerSize(3);
        rawSplit.setResizeWeight(0);
        rawSplit.setEnabled(false);

        /* splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(leftArea),
                new JScrollPane(rightArea)
        ); */
        splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                rawSplit,
                new JScrollPane(rightArea)
        );
        splitPane.setResizeWeight(0.5);

        // 기본 숨김 처리
        splitPane.setRightComponent(null);

        JToolBar toolbar = new JToolBar();
        documentSelect = new JComboBox<>();
        toolbar.add(documentSelect);

        documentSelect.addActionListener(e -> {
            DocumentStorage.DocumentMeta selected = (DocumentStorage.DocumentMeta) documentSelect.getSelectedItem();
            if (selected != null) {
                // 필요하면 저장: requestSaveCurrentDocument("임시제목/타임스탬프");
                requestLoadDocument(selected.id);
            }
        });

        JButton btnSave = new JButton("Save Document");
        btnSave.addActionListener(e -> {
            // 예: 저장할 제목을 입력받아서 서버로 저장 요청
            String title = JOptionPane.showInputDialog(this, "문서 제목을 입력하세요:", "문서 저장", JOptionPane.PLAIN_MESSAGE);
            if (title != null && !title.trim().isEmpty()) {
                requestSaveCurrentDocument(title.trim());
                // 저장 후 문서 목록 갱신 요청
                requestDocumentList();
            }
        });
        toolbar.add(btnSave);

        JToggleButton toggleRaw = new JToggleButton("Show Raw", false);
        toggleRaw.addActionListener(e -> {
            boolean show = toggleRaw.isSelected();
            if (show) {
                splitPane.setRightComponent(new JScrollPane(rightArea));
                splitPane.setResizeWeight(0.5);
            } else {
                splitPane.setRightComponent(null);
            }
            // 리페인트 & 레이아웃 갱신
            splitPane.revalidate();
            splitPane.repaint();
        });
        toolbar.add(toggleRaw);

        // 2) 로그아웃 버튼
        JButton btnLogout = new JButton("Logout");
        btnLogout.addActionListener(e -> logout());
        toolbar.add(btnLogout);

        // 3) 강제 렌더 버튼
        JButton btnRender = new JButton("Render");
        btnRender.addActionListener(e -> {
            controller.render(-1, leftArea); // 모든 데이터 렌더링
            controller.renderRaw(rightArea, lockPane);
        });
        toolbar.add(btnRender);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(splitPane, BorderLayout.CENTER);

        // — 툴바를 프레임 상단에 부착 —
        getContentPane().add(toolbar, BorderLayout.NORTH);

        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        AtomicBoolean arrowKeyPressed = new AtomicBoolean(false);

        leftArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // ↑↓ 키 눌림 여부를 추적하는 KeyListener 추가
                if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    arrowKeyPressed.set(true);
                }

                final int keyCode = e.getKeyCode();
                final boolean isArrow =
                        keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT ||
                        keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN;

                // 편집 가능한지 체크
                if (!controller.isEditable()) {
                    // Allow arrow keys to bypass the edit lock to enable navigation within the text area.
                    if (isArrow) return;
                    e.consume();
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }

                if (keyCode == KeyEvent.VK_BACK_SPACE) {
                    try {
                        int oldPos = leftArea.getCaretPosition();
                        int oldLine = leftArea.getLineOfOffset(oldPos);
                        TextLine toRemoveLine = controller.getTextLineByOffset(oldLine);

                        int lineStart = leftArea.getLineStartOffset(oldLine);
                        int offsetInLine = oldPos - lineStart;

                        boolean changed = controller.handleKeyEvent(e, leftArea);
                        if (changed) {
                            controller.render(-1, leftArea);
                            controller.renderRaw(rightArea, lockPane);
                            int newPos = Math.max(0, oldPos - 1);
                            int docLen = leftArea.getDocument().getLength();
                            leftArea.setCaretPosition(Math.min(newPos, docLen));

                            if (offsetInLine == 0 && oldPos != 0) { // 첫번째 줄이 아닌 라인의 맨 처음이어야함
                                onDeleteLine((long) toRemoveLine.getLineID());
                            } else {
                                onEditInLine((long) toRemoveLine.getLineID(), toRemoveLine.getContent());
                            }
                        }
                    } catch (BadLocationException ex) {
                        //noinspection CallToPrintStackTrace
                        ex.printStackTrace();
                    }
                    e.consume();
                } else if (keyCode == KeyEvent.VK_ENTER) {
                    try {
                        int oldPos = leftArea.getCaretPosition();
                        int oldLine = leftArea.getLineOfOffset(oldPos);
                        TextLine currLine = controller.getTextLineByOffset(oldLine);
                        long oldLineID = currLine.getLineID();

                        int lineStart = leftArea.getLineStartOffset(oldLine);
                        int offsetInLine = oldPos - lineStart;
                        int end = currLine.getContent().length();

                        boolean changed = controller.handleKeyEvent(e, leftArea);
                        if (!changed) return;

                        controller.render(-1, leftArea);
                        controller.renderRaw(rightArea, lockPane);
                        int newStart = leftArea.getLineStartOffset(oldLine + 1);

                        // new line, split line 이벤트 전송
                        if (offsetInLine >= end) { // 엔터 뒤에 문자열이 없었다면
                            onNewLineAfter(oldLineID);
                        }
//                        else if(offsetInLine <= 0){ // 엔터앞에 문자열이 없었다면
//                            onNewLineBefore((long) oldLineID);
//                        }
                        else {
                            onSplitLine(oldLineID, (long) offsetInLine);
                        }

                        leftArea.setCaretPosition(newStart); // 서버에 새로운 라인이 추가된 뒤에 커서 위치를 옮겨야 함. 그래야 락을 잡을 수 있음

                    } catch (BadLocationException ex) {
                        //noinspection CallToPrintStackTrace
                        ex.printStackTrace();
                    }
                    e.consume();
                } else if (keyCode == KeyEvent.VK_DELETE) {
                try {
                    int oldPos = leftArea.getCaretPosition();
                    int oldLine = leftArea.getLineOfOffset(oldPos);
                    TextLine currLine = controller.getTextLineByOffset(oldLine);
                    long oldLineID = currLine.getLineID();

                    int lineStart = leftArea.getLineStartOffset(oldLine);
                    int offsetInLine = oldPos - lineStart;
                    String content = currLine.getContent();

                    // DELETE 처리 전에 다음 라인 정보 저장 (병합 시 필요)
                    TextLine nextLine = null;
                    if (offsetInLine >= content.length() && oldLine < controller.getAllLines().size() - 1) {
                        nextLine = controller.getTextLineByOffset(oldLine + 1);
                    }

                    boolean changed = controller.handleKeyEvent(e, leftArea);
                    if (changed) {
                        controller.render(-1, leftArea);
                        controller.renderRaw(rightArea, lockPane);

                        // 커서 위치 유지 (DELETE는 커서가 움직이지 않음)
                        int docLen = leftArea.getDocument().getLength();
                        leftArea.setCaretPosition(Math.min(oldPos, docLen));

                        // 라인 끝에서 DELETE이고 다음 라인이 병합된 경우
                        if (nextLine != null) {
                            // 다음 라인이 현재 라인에 병합됨 - 새로운 병합 이벤트 전송
                            TextLine updatedCurrentLine = controller.getTextLineByOffset(oldLine);
                            onMergeNextLine(oldLineID, nextLine.getLineID(), updatedCurrentLine.getContent());
                        } else if (offsetInLine < content.length()) {
                            // 라인 중간에서 DELETE: 한 글자 삭제
                            onEditInLine(oldLineID, currLine.getContent());
                        }
                    }
                } catch (BadLocationException ex) {
                    //noinspection CallToPrintStackTrace
                    ex.printStackTrace();
                }
                e.consume();
            }


            }

            @Override
            public void keyTyped(KeyEvent e) {
                // 편집 가능한지 체크
                if (!controller.isEditable()) {
                    e.consume();
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    char c = e.getKeyChar();
                    if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) && !isEnter(c) || isPunctuation(c)) {
                        try {
                            int pos = leftArea.getCaretPosition(); // 전체 caret 위치
                            int offset = leftArea.getLineOfOffset(pos); // 줄 번호
                            int start = leftArea.getLineStartOffset(offset);
                            int end = leftArea.getLineEndOffset(offset);
                            String currentLine = leftArea.getText(start, end - start);
                            if (currentLine.endsWith("\r\n")) {
                                currentLine = currentLine.substring(0, currentLine.length() - 2);
                            } else if (currentLine.endsWith("\n") || currentLine.endsWith("\r")) {
                                currentLine = currentLine.substring(0, currentLine.length() - 1);
                            }
                            TextLine textLine = controller.getTextLineByOffset(offset);
                            long lineId = textLine.getLineID();

                            onEditInLine(lineId, currentLine);
                        } catch (BadLocationException ex) {
                            ex.printStackTrace();
                        }
                    }
                });
            }

//            @Override
//            public void keyReleased(KeyEvent e) {
//                if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN) {
//                    // 다음 이벤트 루프에서 false로 리셋 (caretUpdate 직후 실행되게)
//                    SwingUtilities.invokeLater(() -> arrowKeyPressed.set(false));
//                }
//            }

            private boolean isPunctuation(char c) {
                // 필요시 여기에 문장부호 더 추가 가능
                return "!@#$%^&*()_+-={}[]|\\:;\"\'<>,.?/`~".indexOf(c) >= 0;
            }

            private boolean isEnter(char c) {
                return "\n".indexOf(c) >= 0;
            }

        });

        leftArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                controller.handleMouseClick(e, leftArea);
                controller.render(-1, leftArea);
                controller.renderRaw(rightArea, lockPane);
            }
        });

        leftArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateModelLine(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateModelLine(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateModelLine(e);
            }

            private void updateModelLine(DocumentEvent e) {
                // ** suppress 플래그가 켜져 있으면 아무것도 하지 않음 **
//                if (controller.isSuppressDocumentEvents()) {
//                    return;
//                }

                SwingUtilities.invokeLater(() -> {
                    try {
                        int offset = e.getOffset();
                        int lineIndex = leftArea.getLineOfOffset(offset);
                        int start = leftArea.getLineStartOffset(lineIndex);
                        int end = leftArea.getLineEndOffset(lineIndex);
                        String lineText = leftArea.getText(start, end - start).replaceAll("\\r?\\n$", "");

                        // 편집 가능한지 체크
                        if (controller.isEditable(controller.getTextLineByOffset(lineIndex).getLineID())) {
                            controller.updateLineFromUI(lineIndex, lineText);
                        }
                        controller.renderRaw(rightArea, lockPane);
                    } catch (Exception ex) {
                        //noinspection CallToPrintStackTrace
                        ex.printStackTrace();
                    }
                });
            }
        });

        // CaretListener: ↑↓ 키로 인한 이동일 때만 반응을 위해 추가
        leftArea.addCaretListener(new CaretListener() {
            int lastLine = 0;
            private boolean suppress = false;

            @Override
            public void caretUpdate(CaretEvent e) {
                // 화살표 키에 의한 이동일 때만 처리
                if (!arrowKeyPressed.get() || suppress) return;

                suppress = true;
                try {
                    int dot = e.getDot();
                    int newLine = leftArea.getLineOfOffset(dot);
                    if (newLine != lastLine) {
                        controller.moveLock(lastLine, newLine);
                        controller.renderRaw(rightArea, lockPane);
                        lastLine = newLine;
                    }
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                } finally {
                    suppress = false;
                    arrowKeyPressed.set(false);  // 반드시 여기서 reset!
                }
            }
        });

//        leftArea.addCaretListener(new CaretListener() {
//            int lastLine = 0;
//
//            @Override
//            public void caretUpdate(CaretEvent e) { // todo? render 후에 실행
//                try {
//                    int dot = e.getDot();
//                    int newLine = leftArea.getLineOfOffset(dot);
//                    if (newLine != lastLine) {
//                        controller.moveLock(lastLine, newLine); // todo? 서버에 추가되기 전에 이게 호출되는 것이 문제임
//                        controller.renderRaw(rightArea);
//                        lastLine = newLine;
//                    }
//                } catch (BadLocationException ex) {
//                    //noinspection CallToPrintStackTrace
//                    ex.printStackTrace();
//                }
//            }
//        });


        // 로그인, 로그아웃 정보를 출력하는 텍스트 패널 생성
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new FlowLayout());
        textLabel = new JLabel("로그인을 해주세요.");
        textPanel.add(textLabel);
        add(textPanel, BorderLayout.SOUTH);

        // 화면 닫을 때 로그아웃 함수 실행
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                logout();
            }
        });

        setVisible(true);

        // 처음에 leftArea에 포커스가 없는 문제 해결
        SwingUtilities.invokeLater(() -> {
            leftArea.requestFocusInWindow();  // 포커스 부여
            leftArea.setCaretPosition(0);     // 커서 위치 지정
        });
    }

//    public void onInsertChar(long lineId, String charText, int position) {
//        CMUser myself = getMyself();
//        EventContent eventContent = EventContent.builder()
//                .type("insert_char")
//                .lineId(lineId)
//                .content(charText)
//                .position(position-1)
//                .clientId(myself.getName())
//                .timestamp(LocalDateTime.now())
//                .build();
//
//        broadcastEvent(eventContent);
//    }

    public void mergeNextLine(long currentLineId, long nextLineId, String mergedContent, String clientId) {
        System.out.println("mergeNextLine: currentLineId=" + currentLineId +
                ", nextLineId=" + nextLineId + ", content=" + mergedContent);

        // 서버에서 이미 모델 업데이트가 완료되었으므로,
        // 클라이언트에서는 직접 모델 업데이트 (중복 로직 방지)

        // 1. 현재 라인 내용 직접 업데이트
        controller.directUpdateLineContent(currentLineId, mergedContent);

        // 2. 다음 라인 직접 삭제
        controller.directDeleteLine(nextLineId, clientId);

        // 3. UI 렌더링만 수행
        controller.render(0, leftArea);
        controller.renderRaw(rightArea, lockPane);
    }

    /**
     * DELETE 키로 다음 라인을 현재 라인에 병합할 때 호출하는 함수
     *
     * @param currentLineId : 현재 라인의 id (병합 결과가 저장될 라인)
     * @param nextLineId : 다음 라인의 id (삭제될 라인)
     * @param mergedContent : 병합된 최종 내용
     */
    public void onMergeNextLine(long currentLineId, long nextLineId, String mergedContent) {
        System.out.println("send MERGE_NEXT_LINE_REQUEST");

        CMUserEvent userEvent = new CMUserEvent();
        userEvent.setStringID("MERGE_NEXT_LINE_REQUEST");
        userEvent.setEventField(CMInfo.CM_LONG, "currentLineId", String.valueOf(currentLineId));
        userEvent.setEventField(CMInfo.CM_LONG, "nextLineId", String.valueOf(nextLineId));
        userEvent.setEventField(CMInfo.CM_STR, "mergedContent", mergedContent);
        userEvent.setEventField(CMInfo.CM_STR, "clientId", getMyself().getName());
        clientStub.send(userEvent, "SERVER");
    }

    // 기존의 public method들과 함께 위치
    public void requestDocumentList() {
        System.out.println("[CLIENT] Requesting document list");
        CMUserEvent event = new CMUserEvent();
        event.setStringID("REQUEST_LIST_DOCUMENTS");
        boolean result = clientStub.send(event, "SERVER");
        System.out.println("[CLIENT] Document list request result: " + result);
    }

    public void requestLoadDocument(String docId) {
        CMUserEvent event = new CMUserEvent();
        event.setStringID("REQUEST_LOAD_DOCUMENT");
        event.setEventField(CMInfo.CM_STR, "docId", docId);
        clientStub.send(event, "SERVER");
    }

    public void requestSaveCurrentDocument(String title) {
        System.out.println("[CLIENT] Requesting to save document with title: " + title);
        CMUserEvent event = new CMUserEvent();
        event.setStringID("REQUEST_SAVE_DOCUMENT");
        event.setEventField(CMInfo.CM_STR, "title", title);
        boolean result = clientStub.send(event, "SERVER");
        System.out.println("[CLIENT] Save document request result: " + result);
    }

    private void testSendingEvent(String content) {
        CMInteractionInfo interactionInfo = clientStub.getCMInfo().getInteractionInfo();
        CMUser myself = interactionInfo.getMyself();

        CMDummyEvent event = new CMDummyEvent();
        event.setHandlerSession(myself.getCurrentSession());
        event.setHandlerGroup(myself.getCurrentGroup());
        event.setDummyInfo(content);
        clientStub.broadcast(event);
    }

    public CMClientEventHandler getClientEventHandler() {
        return m_eventHandler;
    }

    public static void main(String[] args) {
        CMClientApp client = new CMClientApp();
        CMClientStub clientStub = client.getClientStub();
        CMClientEventHandler eventHandler = client.getClientEventHandler();
        boolean ret = false;

        clientStub.setAppEventHandler(eventHandler);
        ret = clientStub.startCM();

        if (ret)
            System.out.println("init success");
        else {
            System.err.println("init error!");
            return;
        }

        // 로그인 함수 호출
        client.login();
    }

    // 서버에 Lock Move 요청
    // lineID가 -1이면 release만 요청함
    public boolean requestServerLock(long lineID) {
        CMUserEvent event = new CMUserEvent();
        event.setStringID("LOCK_MOVE_REQUEST");
        event.setHandlerSession("SERVER");
        event.setEventField(CMInfo.CM_STR, "client_id", this.controller.getClientID());
        event.setEventField(CMInfo.CM_LONG, "line_id", String.valueOf(lineID));

        boolean result = clientStub.send(event, "SERVER");

        if (result) {
            System.out.println("[SUCCESS - LOCK_MOVE_REQUEST] (lineID: " + lineID + ", clientID: " + this.controller.getClientID() + ")");
        } else {
            System.out.println("[FAILED - LOCK_MOVE_REQUEST] (lineID: " + lineID + ", clientID: " + this.controller.getClientID() + ")");
        }

        return result;
    }

    private void login() {
        String strUserName = null;
        String strPassword = null;
        boolean bRequestResult = false;

        System.out.println("====== login to default server\n");
        JTextField userNameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        Object[] message = {
                "Username:", userNameField,
                "Password:", passwordField
        };
        int option = JOptionPane.showConfirmDialog(null, message, "Login Input", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            strUserName = userNameField.getText();
            strPassword = new String(passwordField.getPassword()); // security problem?

            // user name은 "SERVER"일 수 없음
            if (strUserName.equals("SERVER")) {
                this.setTextLabel("username은 \"SERVER\"일 수 없습니다.");
                return;
            }
            if (strUserName.isBlank()) { // == strUserName.strip().isEmpty()
                this.setTextLabel("username을 입력해주세요.");
                return;
            }
            if (!strUserName.matches("^[A-Za-z0-9]+$")) {
                setTextLabel("username은 알파벳과 숫자만 사용할 수 있습니다.");
                return;
            }

            bRequestResult = clientStub.loginCM(strUserName, strPassword);
            if (bRequestResult) {
                System.out.println("successfully sent the login request.\n");

                // 창 제목 변경
                setTitle("Text Editor Client - [" + strUserName + "]");

                // 로그인 성공 시 controller에 ID 세팅 및 렌더링 시작
                this.controller.initializeController(strUserName);
                this.controller.render(-1, leftArea);
                this.controller.renderRaw(rightArea, lockPane);
            } else {
                System.out.println("failed the login request!\n");
            }
        }
    }

    private void logout() {
        boolean requestResult = clientStub.logoutCM();
        if (requestResult) {
            System.out.println("Logout request sent successfully.");
            this.setTextLabel("로그아웃 되었습니다. 로그인을 해주세요.");
            exit(0);
            // this.login();
        } else {
            System.out.println("Failed to send logout request!");
            this.setTextLabel("로그아웃에 실패했습니다.");
        }
    }

    public void setTextLabel(String text) {
        textLabel.setText(text);
    }

//    /**
//     * 현재 라인의 앞에 새로운 줄이 추가되었을 때 호출하는 함수
//     *
//     * @param lineId : 현재 라인의 id
//     */
//    public void onNewLineBefore(long lineId) {
//        CMUser myself = getMyself();
//        EventContent eventContent = EventContent.builder()
//                .type("new_line_before")
//                .lineId(lineId)
//                .clientId(myself.getName())
//                .timestamp(LocalDateTime.now())
//                .build();
//        broadcastEvent(eventContent);
//    }

    /**
     * 특정 라인의 뒤에 새로운 줄이 추가되었을 때 호출하는 함수
     *
     * @param lineId : 현재 라인의 id
     */
    public void onNewLineAfter(long lineId) {
        System.out.println("onNewLineAfter");

        CMUserEvent cmUserEvent = new CMUserEvent();
        cmUserEvent.setStringID("NEW_LINE_AFTER_REQUEST");
        cmUserEvent.setEventField(CMInfo.CM_LONG, "lineId", String.valueOf(lineId));
        cmUserEvent.setEventField(CMInfo.CM_STR, "clientId", getMyself().getName());
        clientStub.send(cmUserEvent, "SERVER");

//        CMUser myself = getMyself();
//        EventContent eventContent = EventContent.builder()
//                .type("new_line_after")
//                .lineId(lineId)
//                .clientId(myself.getName())
//                .timestamp(LocalDateTime.now())
//                .build();
//        broadcastEvent(eventContent);
    }

    /**
     * 현재 라인의 호출하는 함수
     *
     * @param lineId : 현재 라인의 id
     */
    public void onSplitLine(long lineId, Long splitIndex) {
        System.out.println("send NEW_SPLIT_LINE_REQUEST");

        CMUserEvent cmUserEvent = new CMUserEvent();
        cmUserEvent.setStringID("NEW_SPLIT_LINE_REQUEST");
        cmUserEvent.setEventField(CMInfo.CM_LONG, "lineId", String.valueOf(lineId));
        cmUserEvent.setEventField(CMInfo.CM_LONG, "splitIndex", String.valueOf(splitIndex));
        cmUserEvent.setEventField(CMInfo.CM_STR, "clientId", getMyself().getName());
        clientStub.send(cmUserEvent, "SERVER");


//        CMUser myself = getMyself();
//        EventContent eventContent = EventContent.builder()
//                .type("new_line_split")
//                .lineId(lineId)
//                .splitIndex(splitIndex)
//                .clientId(myself.getName())
//                .timestamp(LocalDateTime.now())
//                .build();
//        broadcastEvent(eventContent);
    }

    /**
     * 라인을 삭제했을 때 호출하는 함수
     *
     * @param lineId : 현재 라인의 id
     */
    public void onDeleteLine(long lineId) {
        // todo 서버에 편집 발생 이벤트 전송

        System.out.println("send DELETE_LINE_REQUEST");

        CMUserEvent userEvent = new CMUserEvent();
        userEvent.setStringID("DELETE_LINE_REQUEST");
        userEvent.setEventField(CMInfo.CM_LONG, "lineId", String.valueOf(lineId));
        userEvent.setEventField(CMInfo.CM_STR, "clientId", getMyself().getName());
        clientStub.send(userEvent, "SERVER");

//        CMUser myself = getMyself();
//        EventContent eventContent = EventContent.builder()
//                .type("delete_line")
//                .lineId(lineId)
//                .clientId(myself.getName())
//                .timestamp(LocalDateTime.now())
//                .build();
//        broadcastEvent(eventContent);
    }

    /**
     * 현재 라인에서 대치 연산을 했을 때 호출하는 함수
     *
     * @param lineId  : 현재 라인의 id
     * @param content : 대치할 문자열
     */
    public void onEditInLine(long lineId, String content) {
        // todo 서버에 편집 발생 이벤트 전송

        System.out.println("send ON_EDIT_REQUEST");

        CMUserEvent userEvent = new CMUserEvent();
        userEvent.setStringID("ON_EDIT_REQUEST");
        userEvent.setEventField(CMInfo.CM_LONG, "lineId", String.valueOf(lineId));
        userEvent.setEventField(CMInfo.CM_STR, "content", content);
        userEvent.setEventField(CMInfo.CM_STR, "clientId", getMyself().getName());
        clientStub.send(userEvent, "SERVER");

//        CMUser myself = getMyself();
//        EventContent eventContent = EventContent.builder()
//                .type("edit")
//                .lineId(lineId)
//                .content(content)
//                .clientId(myself.getName())
//                .timestamp(LocalDateTime.now())
//                .build();
//        broadcastEvent(eventContent);
    }

    /**
     * 이벤트를 브로드캐스트하는 함수
     */
    private void broadcastEvent(EventContent eventContent) {
        CMUser myself = getMyself();

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
        clientStub.broadcast(de);

    }

    public CMUser getMyself() {
        CMInteractionInfo interactionInfo = clientStub.getCMInfo().getInteractionInfo();
        return interactionInfo.getMyself();
    }

    public void insertText(long lineID, String content, int position) {
        controller.insertText(lineID, content, position);
        controller.render(-1, leftArea);
        controller.renderRaw(rightArea, lockPane);
    }

    public void editLine(long lineID, String content, String clientID) {
        System.out.println("editLine");

        controller.editLine(lineID, content, clientID);
        controller.render(0, leftArea);
        controller.renderRaw(rightArea, lockPane);
    }

    public void insertLineAfter(long lineID, String content, String clientId) {
        System.out.println("insertLineAfter");
        controller.insertLineAfter(lineID, content, clientId);
        requestServerLock(controller.getSelectedLineID());
        controller.render(0, leftArea);
        controller.renderRaw(rightArea, lockPane);
    }

//    public void insertLineBefore(long lineID) {
//        System.out.println("insertLineBefore");
//        controller.insertLineBefore(lineID);
//        controller.render(0, leftArea);
//        controller.renderRaw(rightArea);
//    }

    public void splitLine(long lineID, long splitIndex, String clientId) {
        System.out.println("splitLine");
        controller.splitLine(lineID, splitIndex, clientId);
//        requestServerLock(controller.getSelectedLineID());
        controller.render(0, leftArea);
        controller.renderRaw(rightArea, lockPane);
    }

    public void deleteLine(long lineID, String clientId) {
        System.out.println("deleteLine");
        controller.deleteLine(lineID, clientId);
        controller.render(0, leftArea);
        controller.renderRaw(rightArea, lockPane);
    }

    // 서버에서 lock 정보 업데이트 시 사용
    public void setLockInfoByServer(long lineID, LockStatus lock, String lockClientID) {
        this.controller.setLockInfoByServer(lineID, lock, lockClientID);
    }

    public void render(long lineID) {
        this.controller.render(lineID, this.leftArea);
    }

    public void renderRaw() {
        this.controller.renderRaw(this.rightArea, this.lockPane);
    }

    public void setLockAcquiredLineID(long lineID) {
        this.controller.setLockAcquiredLineID(lineID);
    }

    public String getClientID() {
        String id = this.controller.getClientID();

        if (id == null) return "";
        return id;
    }

    public long getSelectedLineID() {
        return this.controller.getSelectedLineID();
    }

    public long getLockAcquiredLineID() {
        return this.controller.getLockAcquiredLineID();
    }

    public void pushDocumentModel(long topLineId, List<TextLine> contents) {
        this.controller.pushDocumentModel(topLineId, contents);
        controller.render(0, leftArea);
        controller.renderRaw(rightArea, lockPane);
    }

    public void releaseLockByClientId(String clientId) {
        controller.releaseLockByClientId(clientId);
        controller.render(0, leftArea);
        controller.renderRaw(rightArea, lockPane);
    }

    public void updateDocumentSelect(List<DocumentStorage.DocumentMeta> docs) {
        System.out.println("[CLIENT] Updating document select with " + docs.size() + " documents");
        SwingUtilities.invokeLater(() -> {
            documentSelect.removeAllItems();
            for (DocumentStorage.DocumentMeta meta : docs) {
                System.out.println("[CLIENT] Adding document: id=" + meta.id + ", title=" + meta.title);
                documentSelect.addItem(meta);
            }
            System.out.println("[CLIENT] Current document select item count: " + documentSelect.getItemCount());
        });
    }
}
