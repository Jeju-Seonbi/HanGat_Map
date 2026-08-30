package com.example.hangat.map.review;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.map.review.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** 후기 사진 업로드 - 검증 후 저장소에 넘기고 URL 을 돌려준다. 작성 API 에 이 URL 을 첨부한다. */
@Service
public class ReviewPhotoService {

    /** 브라우저 표준 이미지 형식만. HEIC 등은 화면이 못 그린다 */
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp");

    private final FileStorage storage;

    public ReviewPhotoService(FileStorage storage) {
        this.storage = storage;
    }

    public List<String> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        }
        if (files.size() > ReviewService.MAX_IMAGES) {
            throw new BaseException(BaseResponseStatus.REVIEW_TOO_MANY_IMAGES);
        }
        return files.stream().map(this::uploadOne).toList();
    }

    private String uploadOne(MultipartFile file) {
        String ext = com.example.hangat.map.review.storage.LocalFileStorage
                .extensionOf(file.getOriginalFilename());
        if (file.isEmpty() || !ALLOWED.contains(ext)) {
            throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        }
        try {
            return storage.store(file.getOriginalFilename(), file.getBytes()).url();
        } catch (IOException e) {
            throw new BaseException(BaseResponseStatus.FAIL);
        }
    }
}
