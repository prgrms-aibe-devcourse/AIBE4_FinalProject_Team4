package kr.java.documind.domain.patchnote.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteCreateRequest;
import kr.java.documind.domain.patchnote.model.entity.PatchNote;
import kr.java.documind.domain.patchnote.model.repository.PatchNoteRepository;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PatchNoteCommandService {

    private final PatchNoteRepository patchNoteRepository;
    private final PendingItemRepository pendingItemRepository;

    @Transactional
    public Long savePatchNote(UUID projectId, PatchNoteCreateRequest request) {

        // 버전 중복 확인 (soft delete된 버전은 재사용 허용)
        if (patchNoteRepository.existsByVersionAndNotDeleted(
                projectId,
                request.majorVersion(),
                request.minorVersion(),
                request.patchVersion())) {

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

    @Transactional
    public void deletePatchNote(UUID projectId, Long id) {
        int updated =
                patchNoteRepository.softDelete(id, projectId, OffsetDateTime.now(ZoneOffset.UTC));
        if (updated == 0) {
            throw new NotFoundException("패치노트를 찾을 수 없습니다. id: " + id);
        }
    }
}
