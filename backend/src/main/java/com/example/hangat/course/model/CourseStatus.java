package com.example.hangat.course.model;

public enum CourseStatus {
    GENERATING,
    //    코스 생성 중
    READY,
    //    생성 완료
    SAVED,
    //    사용자가 저장함
    FAILED,
    //    생성 실패
    EXPIRED,
    //    임시 코스 만료
    DELETED
    //    삭제 처리됨
}
