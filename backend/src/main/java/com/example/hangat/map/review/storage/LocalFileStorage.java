package com.example.hangat.map.review.storage;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** 서버 디스크 저장. 파일명은 UUID 로 새로 만든다 - 원본명을 쓰면 경로조작·덮어쓰기가 된다 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path dir;

    public LocalFileStorage(@Value("${app.upload-dir:uploads/reviews}") String uploadDir) {
        this.dir = Path.of(uploadDir);
    }

    @Override
    public StoredFile store(String originalFilename, byte[] bytes) {
        String ext = extensionOf(originalFilename);
        String key = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(key), bytes);
        } catch (IOException e) {
            throw new BaseException(BaseResponseStatus.FAIL);
        }
        return new StoredFile(key, "/uploads/reviews/" + key);
    }

    public static String extensionOf(String filename) {
        if (filename == null) {
            return "jpg";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "jpg" : filename.substring(dot + 1).toLowerCase();
    }
}
