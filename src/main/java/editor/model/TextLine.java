package editor.model;

import lombok.Getter;

import java.time.LocalDateTime;

public class TextLine {
    @Getter
    private final long lineID;
    @Getter
    private String content;
    private boolean lock = false;
    private String lockClientID = null;
    @Getter
    private LocalDateTime lastEdited;

    public TextLine(long lineID, String content) {
        this.lineID = lineID;
        this.content = content;
        this.lastEdited = LocalDateTime.now();
    }

    public TextLine copy() {
        TextLine copied = new TextLine(this.lineID, this.content);
        // 락 상태와 클라이언트 정보까지 복사
        copied.lock = this.lock;
        copied.lockClientID = this.lockClientID;
        copied.lastEdited = this.lastEdited; // LocalDateTime은 불변 객체라 그대로 복사해도 안전
        return copied;
    }

    public void setContent(String content) {
        this.content = content;
        this.lastEdited = LocalDateTime.now();
    }

    public synchronized boolean acquireLock(String clientID) {
        if (lock) {
            if(lockClientID.equals(clientID)) return true;
            return false;
        }
        lock = true;
        lockClientID = clientID;
        return true;
    }

    public synchronized boolean releaseLock(String clientID) {
        if (!lock || !clientID.equals(lockClientID)) return false;
        lock = false;
        lockClientID = null;
        return true;
    }

    public synchronized boolean isLocked() {
        return lock;
    }

    public synchronized String getLockClientID() {
        if (lockClientID == null) {
            return "";
        }
        return lockClientID;
    }

    // 서버가 lock 정보 수정할 때 사용
    public void setLockInfoByServer(LockStatus lock, String lockClientID) {
        this.lock = lock.toBoolean();

        if (lock == LockStatus.RELEASE) {
            this.lockClientID = null;
        } else {
            this.lockClientID = lockClientID;
        }
    }

    public void resetLockInfo() {
        this.lock = false;
        this.lockClientID = null;
    }
}
