package kr.java.documind.global.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import kr.java.documind.global.exception.StorageException;
import org.springframework.web.multipart.MultipartFile;

public final class FileUtil {

    private FileUtil() {}

    public static String computeSha256(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream();
                    DigestInputStream dis = new DigestInputStream(is, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // DigestInputStream이 read 과정에서 해시를 누적하므로 별도 처리 불필요
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 알고리즘을 사용할 수 없습니다.");
        }
    }
}
