package com.example.hangat.course.model.enums;

/**
 * 코스 상태 - 테이블 명세서 21.0 courses.status.
 *
 * <p>전이는 엔티티의 이름 있는 메서드로만 한다(markReady/markFailed/markSaved/softDelete) -
 * setter로 상태만 바꾸면 명세서 CHECK가 요구하는 부속 값(saved_at, deleted_at 등)이 빠진 행이 생긴다.
 *
 * <p>⚠️ <b>상수를 추가하면 dev·prod DB에 수동 ALTER가 필요하다.</b> Hibernate 6은 MariaDB에서
 * {@code @Enumerated(STRING)}을 네이티브 ENUM 컬럼으로 생성하는데(@Column length는 무시됨),
 * ddl-auto:update는 기존 컬럼 타입을 바꾸지 않는다 - 새 상수를 저장하는 순간
 * 'Data truncated' 오류로 터진다. 테스트(H2 create-drop)로는 안 잡힌다.
 * 이 함정은 코스 도메인의 enum 컬럼 8개 전부와 map 도메인 enum 컬럼에도 똑같이 적용된다.
 */
public enum CourseStatus {

    /** 생성 요청 접수, 엔진 실행 중. 기본값. */
    GENERATING,

    /** 생성 완료 - 화면에 보여줄 수 있는 상태. 샘플 공개 대상도 READY만. */
    READY,

    /** 회원이 저장한 코스. user_id·title·saved_at이 반드시 함께 있어야 한다(명세서 CHECK). */
    SAVED,

    /** 생성 실패 - generation_error_code로 재시도 안내. */
    FAILED,

    /** 저장되지 않은 임시 코스의 정리 상태. */
    EXPIRED,

    /** 논리 삭제(MY_002) - deleted_at과 반드시 함께(명세서 CHECK). 물리 삭제하지 않는다. */
    DELETED
}
