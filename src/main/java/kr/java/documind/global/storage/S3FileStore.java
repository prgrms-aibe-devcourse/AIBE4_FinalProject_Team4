package kr.java.documind.global.storage;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@ConditionalOnExpression("'${spring.cloud.aws.s3.bucket:}' != ''")
public class S3FileStore implements FileStore {

    private static final List<String> ALLOWED_MIME_TYPES =
            List.of("application/pdf", "image/jpeg", "image/png");

    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "jpg", "jpeg", "png");

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
    public String save(MultipartFile file) throws IOException {
        validateFile(file);

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String storedKey = UUID.randomUUID() + "." + extension;

        s3Template.upload(
                bucket,
                storedKey,
                file.getInputStream(),
                ObjectMetadata.builder().contentType(file.getContentType()).build());

        return storedKey;
    }

    @Override
    public Resource load(String storedKey) {
        try {
            return s3Template.download(bucket, storedKey);
        } catch (Exception e) {
            throw new NotFoundException("S3 파일 조회 오류: " + storedKey, e);
        }
    }

    @Override
    public void delete(String storedKey) {
        s3Template.deleteObject(bucket, storedKey);
    }

    @Override
    public String getAccessUrl(String storedKey) {
        GetObjectRequest objectRequest =
                GetObjectRequest.builder().bucket(bucket).key(storedKey).build();
        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .getObjectRequest(objectRequest)
                        .signatureDuration(Duration.ofMinutes(15))
                        .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new BadRequestException("업로드할 파일이 비어 있습니다.");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)
                || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("허용되지 않는 파일 형식입니다: " + contentType);
        }

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(extension)
                || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BadRequestException("허용되지 않는 파일 확장자입니다: " + extension);
        }
    }
}
