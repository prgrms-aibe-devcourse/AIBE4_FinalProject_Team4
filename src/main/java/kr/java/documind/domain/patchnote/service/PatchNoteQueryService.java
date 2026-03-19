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

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PatchNoteQueryService {

    private final PatchNoteRepository patchNoteRepository;

    public List<PatchNoteSummary> listPatchNotes(UUID projectId) {
        return patchNoteRepository.findAllByProjectIdAndNotDeleted(projectId).stream()
                .map(PatchNoteSummary::from)
                .toList();
    }

    public PatchNoteDetail getDetail(UUID projectId, Long id) {
        PatchNote patchNote =
                patchNoteRepository
                        .findByIdAndDeletedAtIsNull(id)
                        .filter(p -> p.getProjectId().equals(projectId))
                        .orElseThrow(() -> new NotFoundException("패치노트를 찾을 수 없습니다. id: " + id));
        return PatchNoteDetail.from(patchNote);
    }
}
