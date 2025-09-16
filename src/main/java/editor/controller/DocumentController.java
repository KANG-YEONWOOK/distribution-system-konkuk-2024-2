package editor.controller;

import editor.model.DocumentModel;
import editor.model.LockStatus;
import editor.model.TextLine;
import java.util.List;
import java.util.Optional;

public abstract class DocumentController {
    protected DocumentModel documentModel;

    protected DocumentController(DocumentModel documentModel) {
        this.documentModel = documentModel;
    }

    protected boolean isLineValid(long lineID) {
        return lineID >= 0L && lineID < (long) documentModel.getAllLines().size();
    }

    public List<TextLine> getAllLines() {
        return this.documentModel.getAllLines();
    }

    /**
     * 특정 라인을 수정할때 호출하는 함수
     */
    public void insertText(long lineID, String content, int pos) {
        documentModel.insertTextAt(lineID, content, pos);
    }

    public void editLine(long lineID, String content, String clientID) {
        documentModel.forceUpdateContent(lineID, content);
    }

    public void insertLineAfter(long lineID, String content, String clientId) {
        documentModel.insertLineAt(lineID, content, false, clientId);
    }

    public TextLine getTextLineByOffset(int offset) {
        return documentModel.getAllLines().get(offset);
    }

//    public void insertLineBefore(long lineID) {
//        documentModel.insertLineAt(lineID, "", true);
//    }

    public Optional<TextLine> getTextLineByLineId(long lineID) {
        for (TextLine textLine : documentModel.getAllLines()) {
            if (textLine.getLineID() == lineID) {
                return Optional.of(textLine);
            }
        }
        return Optional.empty();
    }

    public synchronized void splitLine(long lineID, long splitIndex, String clientId) {
        System.out.println("splitLine :: lineId = " + lineID + ", splitIndex = " + splitIndex);
        Optional<TextLine> textLineByLineId = getTextLineByLineId(lineID);
        if (textLineByLineId.isEmpty()) {
            return;
        }
        TextLine oldLine = textLineByLineId.get();

        // 락이 있는 사용자만 수정 가능
        if(oldLine.isLocked() && !oldLine.getLockClientID().equals(clientId)){
            return;
        }

        String oldContent = oldLine.getContent();
        String left = oldContent.substring(0, (int) splitIndex);
        String right = oldContent.substring((int) splitIndex);
        System.out.println("splitLine :: left = " + left + ", right=" + right);
        documentModel.forceUpdateContent(lineID, left);
        documentModel.insertLineAt(lineID, right, false, oldLine.getLockClientID());
    }

    public void deleteLine(long lineID, String clientId) {
        Optional<TextLine> textLineByLineId = getTextLineByLineId(lineID);
        if (textLineByLineId.isEmpty()) return;
        String contentInLineToRemove = textLineByLineId.get().getContent();
        if (!contentInLineToRemove.isEmpty()) {
            TextLine prevLine = null;
            for (TextLine textLine : documentModel.getAllLines()) {
                if (textLine.getLineID() == lineID) {
                    if (prevLine != null) {
                        @SuppressWarnings("OptionalGetWithoutIsPresent")
                        String contentToUpdate = getTextLineByLineId(prevLine.getLineID()).get().getContent();
                        documentModel.forceUpdateContent(prevLine.getLineID(), contentToUpdate + contentInLineToRemove);
                    }
                    break;
                }
                prevLine = textLine;
            }
        }
        documentModel.deleteLineAt(lineID, clientId);
    }

    public long getTopLineId() {
        return documentModel.getTopLineId();
    }

    public String getSerializedContents() {
        return documentModel.getSerializedContents();
    }
}
