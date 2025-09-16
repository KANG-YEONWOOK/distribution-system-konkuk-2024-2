package editor.model;

import com.google.gson.Gson;
import org.example.util.CustomGsonUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DocumentModel {
    private long topLineID = 0L;
    private final List<TextLine> contents = new ArrayList<>();

    public DocumentModel() {
        // Initialize with one empty line
        TextLine first = new TextLine(++topLineID, "");
        contents.add(first);
    }

    public DocumentModel copy() {
        DocumentModel copied = new DocumentModel();
        // topLineID도 동일하게 맞춰줌
        copied.topLineID = this.topLineID;

        // 기존 라인들을 deep copy해서 contents에 넣기
        copied.contents.clear(); // 기존 초기 라인 제거
        for (TextLine line : this.contents) {
            // TextLine도 복사 생성자 구현 필요
            copied.contents.add(line.copy());
        }

        return copied;
    }

    // 해당 client ID가 이미 lock을 가지고 있는지 체크
    private boolean checkClientHasLock(String clientID) {
        for (TextLine line : contents) {
            if (line.getLockClientID().equals(clientID)) {
                return true;
            }
        }
        return false;
    }

    // 해당 client가 가지고 있는 lock line ID 반환
    public long findLockLineIDByClientID(String clientID) {
        for (TextLine line: contents) {
            if (line.getLockClientID().equals(clientID)) {
                return line.getLineID();
            }
        }
        return -1; // 가지고 있는 lock이 없는 경우 -1
    }

    // 클라이언트에서 임의 접근 금지
    public synchronized boolean acquireLock(long lineID, String clientID) {
        for (TextLine line : contents) {
            if (line.getLineID() == lineID) {
                return line.acquireLock(clientID);
            }
        }
        return false;
    }

    // 클라이언트에서 임의 접근 금지
    public synchronized boolean releaseLock(long lineID, String clientID) {
        for (TextLine line : contents) {
            if (line.getLineID() == lineID) {
                return line.releaseLock(clientID);
            }
        }
        return false;
    }

    public synchronized boolean releaseLockByClientId(String clientId) {
        for (TextLine line : contents) {
            if (clientId.equals(line.getLockClientID())) {
                return line.releaseLock(clientId);
            }
        }
        return false;
    }

    public synchronized void updateLineAt(long lineID, String content, String clientID) {
        for (TextLine line : contents) {
            if (line.getLineID() == lineID) {
                if (!line.isLocked()) {
                    if (!line.acquireLock(clientID)) return;
                }
                if (clientID.equals(line.getLockClientID())) {
                    line.setContent(content);
                }
                return;
            }
        }
    }

    public synchronized long insertLineAt(long lineID, String content, boolean before, String clientId) {
        int index = 0;
        for (; index < contents.size(); index++) {
            if (contents.get(index).getLineID() == lineID) {
                break;
            }
        }
        int insertPos = before ? index : index + 1;
        TextLine newLine = new TextLine(++topLineID, content);
        contents.add(insertPos, newLine);
        releaseLock(lineID, clientId);
        acquireLock(topLineID, clientId);
        return topLineID;
    }

    public synchronized void deleteLineAt(long lineID, String clientID) {
        for (int i = 0; i < contents.size(); i++) {
            TextLine currentLine = contents.get(i);
            if (currentLine.getLineID() == lineID) {
                // 1. 해당 라인의 락을 해제
                currentLine.releaseLock(clientID);

                // 2. 라인 삭제
                contents.remove(i);

                // 3. 이전 라인이 존재하면 락을 획득 시도
                if (i - 1 >= 0) {
                    TextLine prevLine = contents.get(i - 1);
                    prevLine.acquireLock(clientID);
                }

                return;
            }
        }
    }

    public synchronized void addNewLine() {
        TextLine newLine = new TextLine(++topLineID, "");
        contents.add(newLine);
    }

    public synchronized List<TextLine> getAllLines() {
        return new ArrayList<>(contents);
    }

    public synchronized void forceUpdateContent(long lineID, String content) {
        for (TextLine line : contents) {
            if (line.getLineID() == lineID) {
                System.out.println("forceUpdateContent success");
                line.setContent(content);
                return;
            }
        }
    }

    public synchronized void insertTextAt(long lineID, String text, int pos) {
        for (TextLine line : contents) {
            if (line.getLineID() == lineID) {
                String old = line.getContent();
                if (pos < 0 || pos > old.length()) pos = old.length(); // 안전검사
                String newContent = old.substring(0, pos) + text + old.substring(pos);
                line.setContent(newContent);
                return;
            }
        }
    }

    // 서버에서 lock 정보 업데이트 시 사용
    public void setLockInfoByServer(long lineID, LockStatus lock, String lockClientID) {
        for (TextLine line : contents) {
            if (line.getLineID() == lineID) {
                line.setLockInfoByServer(lock, lockClientID);
            }
        }
    }

    public long getTopLineId() {
        return topLineID;
    }

    public String getSerializedContents() {
        Gson gson = CustomGsonUtils.CustomGson();
        return gson.toJson(contents);
    }

    public void pushFromServer(long topLineId, List<TextLine> contents) {
        this.topLineID = topLineId;
        this.contents.clear();
        this.contents.addAll(contents);
    }

    public void resetAllLockInfo() {
        for (TextLine line : contents) {
            line.resetLockInfo();
        }
    }
}
