package editor.controller;

import editor.model.DocumentModel;
import editor.model.LockStatus;
import editor.model.TextLine;
import lombok.Getter;
import lombok.Setter;
import org.example.CMClientApp;
// import lombok.Getter;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

public class DocumentClientController extends DocumentController {
    @Getter
    private String clientID;

    @Setter
    @Getter
    private long selectedLineID; // 커서가 선택된 TextLine의 line ID

    @Getter
    @Setter
    private long lockAcquiredLineID = -1L; // lock을 건 line ID
    /**
     * -- GETTER --
     *  렌더링 중인지 체크용
     */
    // @Getter
    // private boolean suppressDocumentEvents = false;

    private CMClientApp app;

    public DocumentClientController(DocumentModel model, String clientID, CMClientApp app) {
        super(model);
        this.clientID = clientID;
        this.app = app;
        this.selectedLineID = 1L; // TODO: Late Comer 작업 시 서버에서 받아온 후 첫 번째 줄의 LineID로 설정 필요

        if (clientID != null) {
            this.initializeController(clientID);
        }
    }


    /**
     * 서버 이벤트 수신 시 모델 직접 업데이트 (중복 처리 방지)
     */
    public void directUpdateLineContent(long lineID, String content) {
        documentModel.forceUpdateContent(lineID, content);
    }

    /**
     * 서버 이벤트 수신 시 라인 직접 삭제 (중복 처리 방지)
     */
    public void directDeleteLine(long lineID, String clientID) {
        documentModel.deleteLineAt(lineID, clientID);
    }

    public void initializeController(String clientID) {
        this.clientID = clientID;
        app.requestServerLock(this.selectedLineID);
    }

    /**
     * 전체 또는 특정 줄(render 인자 무시) 렌더링
     */
    public void render(long lineID, JTextArea textArea) {
        // 현재 커서 위치 저장
        int caretPosition = textArea.getCaretPosition();
        int lockingLineOffset = -1;

        // 렌더링
        List<TextLine> lines = documentModel.getAllLines();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if(lines.get(i).getLockClientID().equals(clientID)){
                lockingLineOffset = i;
            }

            sb.append(lines.get(i).getContent());
            if (i < lines.size() - 1) sb.append("\n");
        }
        textArea.setText(sb.toString());

        // 락을 건 라인 위치로 커서를 이동
        if (lockingLineOffset != -1) {
            int offset = 0;
            for (int i = 0; i < lockingLineOffset; i++) {
                offset += lines.get(i).getContent().length() + 1; // +1 for '\n'
            }
            textArea.setCaretPosition(Math.min(offset, textArea.getDocument().getLength()));
            return;
        }


        // 가능한 경우에만 커서 위치 복원
        int newLength = textArea.getDocument().getLength();
        if (caretPosition <= newLength) {
            textArea.setCaretPosition(caretPosition);
        } else {
            // 텍스트가 짧아져서 원래 커서 위치가 유효하지 않을 경우, 마지막 위치로 설정
            textArea.setCaretPosition(newLength);
        }
    }

    /**
     * Raw 뷰(락 상태 포함) 렌더링
     */
    public void renderRaw(JTextArea rightArea, JTextPane lockPane) {
        List<TextLine> lines = documentModel.getAllLines();
        StringBuilder sb = new StringBuilder();
        for (TextLine line : lines) {
            sb.append(line.getLineID())
                    .append(": ")
                    .append(line.getContent())
                    .append(" [lock=")
                    .append(line.isLocked())
                    .append(", id=")
                    .append(line.getLockClientID())
                    .append("]\n");
        }
        rightArea.setText(sb.toString());
        renderLockPanel(lockPane);
    }

    private static char toCircled(char c){
        if('A'<=c && c<='Z') return (char)('\u24B6' + (c-'A'));   // Ⓐ~Ⓩ
        if('a'<=c && c<='z') return (char)('\u24D0' + (c-'a'));   // ⓐ~ⓩ
        if('1'<=c && c<='9') return (char)('\u2460' + (c-'1'));   // ①~⑨
        if( c=='0' )          return '\u24EA';                    // ⓪
        return '?';  // 허용 문자 아님
    }

    private void renderLockPanel(JTextPane lockPane) {
        StyledDocument doc = lockPane.getStyledDocument();
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}

        SimpleAttributeSet center = new SimpleAttributeSet();
        StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
        doc.setParagraphAttributes(0, 0, center, false);

        this.getAllLines().forEach(line -> {
            String id = line.getLockClientID();
            char circled = id.isEmpty()? ' ' : toCircled(id.charAt(0));
            try {
                doc.insertString(doc.getLength(), circled + "\n", null);
            } catch (BadLocationException ignored) {}
        });
    }

    /**
     * ENTER, BACK_SPACE 처리
     * - ENTER: split → 이전 줄 락 해제 → 새 줄 락 획득
     * - BACK_SPACE: 맨앞 merge→이전 줄 락 이동, 중간 삭제→락 유지
     */
    public boolean handleKeyEvent(KeyEvent e, JTextArea textArea) {
        int keyCode = e.getKeyCode();
        try {
            int caretPos = textArea.getCaretPosition();
            int lineIndex = textArea.getLineOfOffset(caretPos);
            List<TextLine> lines = documentModel.getAllLines();
            if (lineIndex < 0 || lineIndex >= lines.size()) return false;

            TextLine currLine = lines.get(lineIndex);
            long oldLineID = currLine.getLineID();

            switch (keyCode) {
                case KeyEvent.VK_ENTER: {
                    // 분할 위치 계산
                    int lineStart = textArea.getLineStartOffset(lineIndex);
                    int offsetInLine = caretPos - lineStart;
                    String content = currLine.getContent();
                    String before = content.substring(0, offsetInLine);
                    String after = content.substring(offsetInLine);

                    // 모델 업데이트
                    documentModel.updateLineAt(oldLineID, before, clientID);
                    documentModel.insertLineAt(oldLineID, after, false, clientID);

                    // 락 이동
                    // documentModel.releaseLock(oldLineID, clientID);
                    List<TextLine> updated = documentModel.getAllLines();
                    TextLine newLine = updated.get(lineIndex + 1);
                    long newID = newLine.getLineID();
                    // documentModel.acquireLock(newID, clientID);
                    // app.requestServerLock(newID);
                    selectedLineID = newID;
                    setLockAcquiredLineID(newID); // 와 이게 문제 였네...
                    break;
                }
                case KeyEvent.VK_DELETE: {
                    int lineStart = textArea.getLineStartOffset(lineIndex);
                    int offsetInLine = caretPos - lineStart;
                    String content = currLine.getContent();

                    // 라인 끝에서 DELETE이고 다음 라인이 존재하는 경우
                    if (offsetInLine >= content.length() && lineIndex < lines.size() - 1) {
                        TextLine next = lines.get(lineIndex + 1);
                        long nextID = next.getLineID();

                        // 다음 라인이 잠겨있고, 그 락의 소유자가 내가 아니면 병합 시도 X
                        if (next.isLocked() && !clientID.equals(next.getLockClientID())) {
                            Toolkit.getDefaultToolkit().beep();
                            return false;
                        }

                        // 병합 로직 (현재 라인 + 다음 라인)
                        String merged = currLine.getContent() + next.getContent();
                        documentModel.updateLineAt(oldLineID, merged, clientID);
                        documentModel.deleteLineAt(nextID, clientID);

                        // 락은 현재 라인에 그대로 유지
                        selectedLineID = oldLineID;
                        setLockAcquiredLineID(documentModel.findLockLineIDByClientID(clientID));
                    } else if (offsetInLine < content.length()) {
                        // 라인 중간에서 DELETE: 커서 다음 글자 삭제
                        String updated = content.substring(0, offsetInLine)
                                + content.substring(offsetInLine + 1);
                        documentModel.updateLineAt(oldLineID, updated, clientID);
                    }
                    // 마지막 라인 끝이거나 라인이 하나뿐이면 아무것도 하지 않음
                    break;
                }
                case KeyEvent.VK_BACK_SPACE: {
                    int lineStart = textArea.getLineStartOffset(lineIndex);
                    int offsetInLine = caretPos - lineStart;
                    if (offsetInLine == 0 && lineIndex > 0) {
                        // 라인 맨 앞에서만 병합 로직 진입
                        TextLine prev = lines.get(lineIndex - 1);
                        long prevID = prev.getLineID();

                        // 이전 라인이 잠겨있지 않고, 그 락의 소유자가 내가 아니면 병합 시도 X (lock)
                        if (prev.isLocked() && !clientID.equals(prev.getLockClientID())) {
                            Toolkit.getDefaultToolkit().beep();
                            return false;
                        }

                        // 기존 병합 로직 (락 검사 통과 시)
                        String merged = prev.getContent() + currLine.getContent();
                        documentModel.updateLineAt(prevID, merged, clientID);
                        documentModel.deleteLineAt(oldLineID, clientID);

                        selectedLineID = prevID;
                        setLockAcquiredLineID(documentModel.findLockLineIDByClientID(clientID)); // 이게 없으면 편집이 막힘
                    } else {
                        // 한 글자 삭제
                        String curr = currLine.getContent();
                        String updated = curr.substring(0, offsetInLine - 1)
                                + curr.substring(offsetInLine);
                        documentModel.updateLineAt(oldLineID, updated, clientID);
                    }
                    break;
                }
                default:
                    // 나머지는 DocumentListener/CaretListener로 처리
                    break;
            }
        } catch (BadLocationException ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        }
        return true;
    }

    /**
     * 마우스 클릭 시 줄 이동 락 관리
     */
    public void handleMouseClick(MouseEvent e, JTextArea textArea) {
        try {
            int pos = textArea.viewToModel(e.getPoint());
            int lineIndex = textArea.getLineOfOffset(pos);
            List<TextLine> lines = documentModel.getAllLines();
            if (lineIndex < 0 || lineIndex >= lines.size()) return;

            long newID = lines.get(lineIndex).getLineID();
            // documentModel.releaseLock(currentLineID, clientID);
            // if (documentModel.acquireLock(newID, clientID)) {
            //     currentLineID = newID;
            // }
            app.requestServerLock(newID);
        } catch (BadLocationException ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
        }
    }

    /**
     * UI 직접 타이핑 시 해당 줄을 모델에 업데이트
     */
    public void updateLineFromUI(int lineIndex, String content) {
        List<TextLine> lines = documentModel.getAllLines();
        if (lineIndex < 0 || lineIndex >= lines.size()) return;

        long lineID = lines.get(lineIndex).getLineID();
        // 오직 현재 락이 걸려 있는 줄만 업데이트
        if (lineID == selectedLineID) {
            // 락 상태나 clientID를 건드리지 않고 내용만 바꿔주는 메서드
            documentModel.forceUpdateContent(lineID, content);
        }
    }

    /**
     * CaretListener 사용 시 호출:
     * - 이전 줄 락 해제 → 새 줄 락 획득
     */
    public void moveLock(int oldLineIndex, int newLineIndex) {
        List<TextLine> lines = documentModel.getAllLines();
        /* if (oldLineIndex >= 0 && oldLineIndex < lines.size()) {
            // documentModel.releaseLock(lines.get(oldLineIndex).getLineID(), clientID);
        }
        if (newLineIndex >= 0 && newLineIndex < lines.size()) {
            long id = lines.get(newLineIndex).getLineID();
            // documentModel.acquireLock(id, clientID);
            selectedLineID = id;
        } */
        long lineID = lines.get(newLineIndex).getLineID(); // todo 여기서 lock 값이 바뀜
        app.requestServerLock(lineID);
        selectedLineID = lineID;
    }

    // 서버에서 lock 정보 업데이트 시 사용
    public void setLockInfoByServer(long lineID, LockStatus lock, String lockClientID) {
        this.documentModel.setLockInfoByServer(lineID, lock, lockClientID);
    }

    public boolean isEditable() {
        // 현재 선택한 Line ID가 Lock을 가진 Line ID인 경우, 단 둘 다 -1이면 안됨 -> 수정 가능
        return (this.selectedLineID == this.lockAcquiredLineID && this.lockAcquiredLineID >= 0);
    }

    public boolean isEditable(long lineID) {
        return (this.lockAcquiredLineID == lineID && this.lockAcquiredLineID >= 0);
    }

    public void pushDocumentModel(long topLineId, List<TextLine> contents) {
        this.documentModel.pushFromServer(topLineId, contents);
    }

    public void releaseLockByClientId(String clientId) {
        this.documentModel.releaseLockByClientId(clientId);
    }
}
