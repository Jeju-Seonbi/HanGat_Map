package com.example.hangat.map.review;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.map.review.storage.FileStorage;
import com.example.hangat.map.review.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 사진 업로드 - 형식·개수 검증과 디스크 저장 확인 */
class ReviewPhotoServiceTest {

    @TempDir
    Path tempDir;

    private ReviewPhotoService service() {
        return new ReviewPhotoService(new LocalFileStorage(tempDir.toString()));
    }

    private MockMultipartFile jpg(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void 업로드하면_파일이_저장되고_URL이_돌아온다() throws Exception {
        List<String> urls = service().upload(List.of(jpg("cat.jpg"), jpg("dog.png")));

        assertThat(urls).hasSize(2);
        assertThat(urls.get(0)).startsWith("/uploads/reviews/").endsWith(".jpg");
        // 원본명이 아니라 UUID 로 저장된다 - 경로조작·덮어쓰기 방지
        assertThat(urls.get(0)).doesNotContain("cat");
        try (var files = Files.list(tempDir)) {
            assertThat(files.count()).isEqualTo(2);
        }
    }

    @Test
    void 여섯_장은_거부된다() {
        List<MockMultipartFile> six = Collections.nCopies(6, jpg("a.jpg"));

        assertThatThrownBy(() -> service().upload(List.copyOf(six)))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void 이미지가_아니면_거부된다() {
        MockMultipartFile exe = new MockMultipartFile("files", "virus.exe",
                "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> service().upload(List.of(exe)))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void 빈_파일은_거부된다() {
        MockMultipartFile empty = new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service().upload(List.of(empty)))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void 저장_키에_확장자가_유지된다() {
        FileStorage.StoredFile stored = new LocalFileStorage(tempDir.toString())
                .store("풍경사진.webp", new byte[]{1});

        assertThat(stored.key()).endsWith(".webp");
        assertThat(stored.url()).isEqualTo("/uploads/reviews/" + stored.key());
    }
}
