package kr.java.documind.domain.archive.document.model.repository;

public interface DocumentGroupSummary {

    Long getGroupId();

    String getGroupName();

    String getCategory();

    Long getVersionOrdinal();

    Long getDocumentCount();
}
