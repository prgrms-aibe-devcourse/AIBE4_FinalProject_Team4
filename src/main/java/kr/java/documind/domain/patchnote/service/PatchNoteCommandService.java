package kr.java.documind.domain.patchnote.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteCreateRequest;
import kr.java.documind.domain.patchnote.model.entity.PatchNote;
import kr.java.documind.domain.patchnote.model.repository.PatchNoteRepository;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 패치노트 쓰기 서비스.
 *
 * <p>저장·삭제 등 상태를 변경하는 작업을 담당한다. 읽기 작업은 {@link PatchNoteQueryService}에서 처리한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PatchNoteCommandService {

    private final PatchNoteRepository patchNoteRepository;
    private final PendingItemRepository pendingItemRepository;

    /**
     * 패치노트를 DRAFT 상태로 저장한다.
     *
     * <p>처리 순서:
     * <ol>
     *   <li>버전 중복 확인 → 중복 + overwrite=false 이면 {@link ConflictException}
     *   <li>overwrite=true 이면 기존 활성 버전 soft delete 후 신규 저장
     *   <li>{@link PatchNote#createDraft(UUID, String, String, Integer, Integer, Integer)} 생성
     *   <li>DB 저장
     *   <li>요청에 포함된 {@code itemIds}를 COMPLETED 처리 (빈 목록이면 스킵)
     * </ol>
     *
     * @param projectId 프로젝트 UUID
     * @param request   저장 요청 (title, content, version, itemIds, overwrite)
     * @return 저장된 PatchNote ID
     * @throws ConflictException    버전 중복 (overwrite=false일 때)
     * @throws BadRequestException  버전이 0 미만
     */
    @Transactional
    public Long savePatchNote(UUID projectId, PatchNoteCreateRequest request) {

        // 버전 중복 확인 (soft delete된 버전은 재사용 허용)
        if (patchNoteRepository.existsByVersionAndNotDeleted(
                projectId, request.majorVersion(), request.minorVersion(), request.patchVersion())) {

            if (!request.overwrite()) {
                throw new ConflictException(
                        "이미 존재하는 버전입니다. v%d.%d.%d"
                                .formatted(
                                        request.majorVersion(),
                                        request.minorVersion(),
                                        request.patchVersion()));
            }

            // 덮어쓰기: 기존 활성 버전 soft delete
            patchNoteRepository.softDeleteByVersion(
                    projectId,
                    request.majorVersion(),
                    request.minorVersion(),
                    request.patchVersion(),
                    OffsetDateTime.now(ZoneOffset.UTC));
        }

        // 패치노트 생성 (DRAFT)
        PatchNote patchNote =
                PatchNote.createDraft(
                        projectId,
                        request.title(),
                        request.content(),
                        request.majorVersion(),
                        request.minorVersion(),
                        request.patchVersion());

        PatchNote saved = patchNoteRepository.save(patchNote);

        // 선택된 PendingItem → COMPLETED
        if (!request.itemIds().isEmpty()) {
            pendingItemRepository.markCompleted(projectId, request.itemIds());
        }

        return saved.getId();
    }

    /**
     * 패치노트를 soft delete한다.
     *
     * @param projectId 프로젝트 UUID (소유권 검증)
     * @param id        대상 패치노트 ID
     * @throws NotFoundException 존재하지 않거나 이미 삭제된 경우
     */
    @Transactional
    public void deletePatchNote(UUID projectId, Long id) {
        int updated =
                patchNoteRepository.softDelete(id, projectId, OffsetDateTime.now(ZoneOffset.UTC));
        if (updated == 0) {
            throw new NotFoundException("패치노트를 찾을 수 없습니다. id: " + id);
        }
    }
}
