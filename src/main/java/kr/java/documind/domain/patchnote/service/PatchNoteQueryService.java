package kr.java.documind.domain.patchnote.service;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDetail;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteSummary;
import kr.java.documind.domain.patchnote.model.entity.PatchNote;
import kr.java.documind.domain.patchnote.model.repository.PatchNoteRepository;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 패치노트 읽기 서비스.
 *
 * <p>목록·단건 조회에 특화된 읽기 전용 서비스다. 쓰기 작업은 {@link PatchNoteCommandService}에서 처리한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PatchNoteQueryService {

    private final PatchNoteRepository patchNoteRepository;

    /**
     * 프로젝트의 활성 패치노트 목록을 최신순으로 조회한다.
     *
     * <p>soft delete({@code deleted_at IS NOT NULL}) 항목은 제외한다.
     *
     * @param projectId 프로젝트 UUID
     * @return 패치노트 요약 목록 (생성일시 내림차순)
     */
    public List<PatchNoteSummary> listPatchNotes(UUID projectId) {
        return patchNoteRepository.findAllByProjectIdAndNotDeleted(projectId).stream()
                .map(PatchNoteSummary::from)
                .toList();
    }

    /**
     * 패치노트 단건 상세를 조회한다.
     *
     * @param projectId 프로젝트 UUID (소유권 검증)
     * @param id        조회 대상 패치노트 ID
     * @return 패치노트 상세 DTO
     * @throws NotFoundException 존재하지 않거나 삭제된 경우, 또는 다른 프로젝트 소속인 경우
     */
    public PatchNoteDetail getDetail(UUID projectId, Long id) {
        PatchNote patchNote =
                patchNoteRepository
                        .findByIdAndDeletedAtIsNull(id)
                        .filter(p -> p.getProjectId().equals(projectId))
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "패치노트를 찾을 수 없습니다. id: " + id));
        return PatchNoteDetail.from(patchNote);
    }
}
