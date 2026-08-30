package com.example.hangat.map.review.storage;

/**
 * 후기 사진 저장소. 지금은 서버 디스크(Local) 구현 하나 -
 * S3 로 갈 때 이 인터페이스의 구현만 바꾸면 후기 코드는 그대로다.
 */
public interface FileStorage {

    /** @return 저장 키와 브라우저가 접근할 URL */
    StoredFile store(String originalFilename, byte[] bytes);

    record StoredFile(String key, String url) {
    }
}
