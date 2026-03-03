package kr.java.documind.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3FileStoreTest {

    private S3FileStore s3FileStore;

    private static final String BUCKET = "test-bucket";
    @Mock private S3Presigner s3Presigner;
    @Mock private S3Template s3Template;

    @BeforeEach
    void setUp() {
        s3FileStore = new S3FileStore(BUCKET, s3Presigner, s3Template);
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("파일 저장 시 UUID 기반 저장키를 반환한다")
        void returnsUuidBasedKey() throws IOException {
            MultipartFile file = mock(MultipartFile.class);

            given(file.getOriginalFilename()).willReturn("document.pdf");
            given(file.getContentType()).willReturn("application/pdf");
            given(file.getSize()).willReturn(1024L);
            given(file.isEmpty()).willReturn(false);
            given(file.getInputStream()).willReturn(new ByteArrayInputStream(new byte[0]));

            String storedKey = s3FileStore.save(file);

            assertThat(storedKey).matches("[a-f0-9\\-]+\\.pdf");

            then(s3Template)
                    .should()
                    .upload(
                            eq(BUCKET),
                            eq(storedKey),
                            any(InputStream.class),
                            any(ObjectMetadata.class));
        }

        @Test
        @DisplayName("빈 파일이면 BadRequestException을 던진다")
        void throwsExceptionWhenFileIsEmpty() {
            MultipartFile file = mock(MultipartFile.class);

            given(file.isEmpty()).willReturn(true);

            assertThatThrownBy(() -> s3FileStore.save(file))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("비어 있습니다");
        }

        @Test
        @DisplayName("허용되지 않는 MIME 타입이면 BadRequestException을 던진다")
        void throwsExceptionWhenMimeTypeNotAllowed() {
            MultipartFile file = mock(MultipartFile.class);

            given(file.isEmpty()).willReturn(false);
            given(file.getSize()).willReturn(1024L);
            given(file.getContentType()).willReturn("text/plain");

            assertThatThrownBy(() -> s3FileStore.save(file))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("파일 형식");
        }

        @Test
        @DisplayName("허용되지 않는 확장자면 BadRequestException을 던진다")
        void throwsExceptionWhenExtensionNotAllowed() {
            MultipartFile file = mock(MultipartFile.class);

            given(file.isEmpty()).willReturn(false);
            given(file.getSize()).willReturn(1024L);
            given(file.getContentType()).willReturn("application/pdf");
            given(file.getOriginalFilename()).willReturn("file.exe");

            assertThatThrownBy(() -> s3FileStore.save(file))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("확장자");
        }
    }

    @Nested
    @DisplayName("load")
    class Load {

        @Test
        @DisplayName("저장키로 파일을 조회한다")
        void loadsResource() {
            S3Resource expected = mock(S3Resource.class);

            given(s3Template.download(BUCKET, "test-key")).willReturn(expected);

            var result = s3FileStore.load("test-key");

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("파일이 존재하지 않으면 NotFoundException을 던진다")
        void throwsNotFoundExceptionWhenFileNotExists() {
            S3Exception noSuchKeyException =
                    (S3Exception)
                            S3Exception.builder()
                                    .awsErrorDetails(
                                            AwsErrorDetails.builder()
                                                    .errorCode("NoSuchKey")
                                                    .errorMessage(
                                                            "The specified key does not exist.")
                                                    .build())
                                    .message("NoSuchKey")
                                    .build();

            given(s3Template.download(BUCKET, "invalid-key")).willThrow(noSuchKeyException);

            assertThatThrownBy(() -> s3FileStore.load("invalid-key"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("invalid-key");
        }

        @Test
        @DisplayName("S3 인프라 오류이면 BusinessException(500)을 던진다")
        void throwsBusinessExceptionOnS3InfraError() {
            S3Exception accessDeniedException =
                    (S3Exception)
                            S3Exception.builder()
                                    .awsErrorDetails(
                                            AwsErrorDetails.builder()
                                                    .errorCode("AccessDenied")
                                                    .errorMessage("Access Denied")
                                                    .build())
                                    .message("AccessDenied")
                                    .build();

            given(s3Template.download(BUCKET, "test-key")).willThrow(accessDeniedException);

            assertThatThrownBy(() -> s3FileStore.load("test-key"))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("test-key");
        }

        @Test
        @DisplayName("알 수 없는 예외이면 StorageException(500)을 던진다")
        void throwsStorageExceptionOnUnexpectedError() {
            given(s3Template.download(BUCKET, "test-key"))
                    .willThrow(new RuntimeException("unexpected"));

            assertThatThrownBy(() -> s3FileStore.load("test-key"))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("test-key");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("저장키로 파일을 삭제한다")
        void deletesObject() {
            s3FileStore.delete("test-key");

            then(s3Template).should().deleteObject(BUCKET, "test-key");
        }
    }

    @Nested
    @DisplayName("getAccessUrl")
    class GetAccessUrl {

        @Test
        @DisplayName("Pre-signed URL을 반환한다")
        void returnsPresignedUrl() throws MalformedURLException {
            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);

            given(presigned.url())
                    .willReturn(
                            new URL(
                                    "https://test-bucket.s3.amazonaws.com/test-key?X-Amz-Signature=abc"));
            given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                    .willReturn(presigned);

            String url = s3FileStore.getAccessUrl("test-key");

            assertThat(url).contains("test-bucket", "test-key");
        }
    }
}
