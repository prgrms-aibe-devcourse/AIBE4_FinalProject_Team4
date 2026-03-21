package kr.java.documind.global.storage;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import kr.java.documind.global.enums.AllowedFileType;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Component
@ConditionalOnExpression("'${spring.cloud.aws.s3.bucket:}' != ''")
public class S3FileStore implements FileStore {

    private static final Tika TIKA = new Tika();

    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(15);

    private final String bucket;
    private final S3Presigner s3Presigner;
    private final S3Template s3Template;

    public S3FileStore(
            @Value("${spring.cloud.aws.s3.bucket}") String bucket,
            S3Presigner s3Presigner,
            S3Template s3Template) {
        this.bucket = bucket;
        this.s3Presigner = s3Presigner;
        this.s3Template = s3Template;
    }

    @Override
    public FileStoreResult save(String directory, MultipartFile file) {
        ResolvedFile resolved = validateAndResolve(file);

        String storedKey = directory + "/" + UUID.randomUUID() + "." + resolved.extension();

        try (InputStream is = file.getInputStream()) {
            s3Template.upload(
                    bucket,
                    storedKey,
                    is,
                    ObjectMetadata.builder().contentType(resolved.contentType()).build());

            return new FileStoreResult(storedKey, resolved.extension());
        } catch (IOException e) {
            throw new StorageException("파일 스트림 처리 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            throw new StorageException("S3 파일 업로드에 실패했습니다: " + storedKey, e);
        }
    }

    @Override
    public Resource load(String storedKey) {
        try {
            return s3Template.download(bucket, storedKey);
        } catch (S3Exception e) {
            String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;
            if ("NoSuchKey".equals(errorCode)) {
                throw new NotFoundException("S3 파일을 찾을 수 없습니다: " + storedKey, e);
            }
            throw new StorageException("S3 파일 조회 중 오류가 발생했습니다: " + storedKey, e);
        } catch (Exception e) {
            throw new StorageException("S3 파일 조회 중 알 수 없는 오류가 발생했습니다: " + storedKey, e);
        }
    }

    @Override
    public String getAccessUrl(String storedKey) {
        GetObjectRequest objectRequest =
                GetObjectRequest.builder().bucket(bucket).key(storedKey).build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .getObjectRequest(objectRequest)
                        .signatureDuration(PRESIGN_DURATION)
                        .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public void deleteOnCommit(String storedKey) {
        validateTransactionActive();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            delete(storedKey);
                        } catch (Exception e) {
                            log.warn("트랜잭션 커밋 후 S3 파일 삭제 실패: {}", storedKey, e);
                        }
                    }
                });
    }

    @Override
    public void deleteOnRollback(String storedKey) {
        validateTransactionActive();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                delete(storedKey);
                            } catch (Exception e) {
                                log.warn("트랜잭션 롤백 후 S3 파일 삭제 실패: {}", storedKey, e);
                            }
                        }
                    }
                });
    }

    private record ResolvedFile(String contentType, String extension) {}

    private ResolvedFile validateAndResolve(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new BadRequestException("업로드할 파일이 비어 있습니다.");
        }

        String detectedType = detectContentType(file);
        AllowedFileType fileType = AllowedFileType.fromMimeType(detectedType);
        if (fileType == null) {
            throw new BadRequestException("허용되지 않는 파일 형식입니다: " + detectedType);
        }

        return new ResolvedFile(fileType.getMimeType(), fileType.getExtension());
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return TIKA.detect(is, file.getOriginalFilename());
        } catch (IOException e) {
            throw new StorageException("파일 형식 감지에 실패했습니다.", e);
        }
    }

    private void validateTransactionActive() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("활성 트랜잭션이 없어 삭제를 등록할 수 없습니다.");
        }
    }

    private void delete(String storedKey) {
        s3Template.deleteObject(bucket, storedKey);
    }
}
