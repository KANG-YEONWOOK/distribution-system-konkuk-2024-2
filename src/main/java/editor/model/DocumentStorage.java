package editor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocumentStorage {
    private Map<String, DocumentModel> documents = new HashMap<>();
    private Map<String, String> documentTitles = new HashMap<>(); // 문서ID → 제목

    // 문서 저장
    public void saveDocument(String docId, String title, DocumentModel model) {
        documents.put(docId, model); // 깊은 복사 권장
        documentTitles.put(docId, title);
    }

    // 문서 불러오기
    public DocumentModel loadDocument(String docId) {
        return documents.get(docId);
    }

    // 문서 목록 반환 (ID, 제목)
    public List<DocumentMeta> listDocuments() {
        List<DocumentMeta> list = new ArrayList<>();
        for (String docId : documents.keySet()) {
            list.add(new DocumentMeta(docId, documentTitles.get(docId)));
        }
        return list;
    }

    public static class DocumentMeta {
        public String id;
        public String title;

        // Jackson을 위한 기본 생성자
        public DocumentMeta() {
        }

        @JsonCreator
        public DocumentMeta(@JsonProperty("id") String id, @JsonProperty("title") String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
