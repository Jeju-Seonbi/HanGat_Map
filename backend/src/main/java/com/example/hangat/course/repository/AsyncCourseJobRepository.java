package com.example.hangat.course.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 생성 대기열의 조회와 상태 변경을 담당한다.
 * 행 잠금과 DB 시각 기반 실행 권한 검사는 기존 SQL을 유지한다.
 * 잠금이 필요한 호출은 서비스의 트랜잭션 안에서 실행한다.
 */
@Repository
public class AsyncCourseJobRepository {

    private final JdbcTemplate jdbc;

    public AsyncCourseJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 접수와 실행 수 검사에 사용하는 공용 대기열 행을 잠근다. */
    public void lockQueue() {
        jdbc.queryForObject("""
                SELECT id
                FROM ai_queue_control
                WHERE id = 'COURSE_GENERATION'
                FOR UPDATE
                """,
                String.class
        );
    }

    /** 사용자의 요청 키로 기존 접수 내역을 조회한다. */
    public List<Map<String, Object>> findByUserAndRequestKey(Long userId, String requestKey) {
        return jdbc.queryForList("""
                SELECT *
                FROM course_generation_jobs
                WHERE user_id = ? AND request_key = ?
                """,
                userId, requestKey
        );
    }

    /** 사용자가 대기 또는 실행 중인 작업 수를 조회한다. */
    public Long countActiveByUser(Long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM course_generation_jobs
                WHERE user_id = ?
                  AND status IN ('QUEUED', 'RUNNING')
                """,
                Long.class, userId
        );
    }

    /** 전체 대기 및 실행 작업 수를 조회한다. */
    public Long countActive() {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM course_generation_jobs
                WHERE status IN ('QUEUED', 'RUNNING')
                """,
                Long.class
        );
    }

    /** 검증된 생성 요청을 대기 상태로 저장한다. */
    public void insertQueued(
            String id, Long userId, String requestKey, String requestHash,
            String requestJson, LocalDate startDate, LocalDate endDate
    ) {
        jdbc.update("""
                INSERT INTO course_generation_jobs (
                    id, user_id, request_key, request_hash,
                    request_json, status, start_date, end_date,
                    created_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, 'QUEUED', ?, ?, UTC_TIMESTAMP(6)
                )
                """,
                id, userId, requestKey, requestHash, requestJson, startDate, endDate
        );
    }

    /** 실행기에 전달하지 못한 작업을 같은 실행 권한일 때만 대기 상태로 돌린다. */
    public int requeueIfOwned(String id, String leaseToken) {
        return jdbc.update("""
                UPDATE course_generation_jobs
                SET status = 'QUEUED',
                    lease_token = NULL,
                    lease_until = NULL,
                    execution_deadline = NULL
                WHERE id = ?
                  AND status = 'RUNNING'
                  AND lease_token = ?
                """,
                id, leaseToken
        );
    }

    /** 현재 실행 상태인 작업 수를 조회한다. */
    public Long countRunning() {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM course_generation_jobs
                WHERE status = 'RUNNING'
                """,
                Long.class
        );
    }

    /** 접수 순서상 가장 오래된 대기 작업 한 건을 잠근다. */
    public List<Map<String, Object>> lockOldestQueued() {
        return jdbc.queryForList("""
                SELECT *
                FROM course_generation_jobs
                WHERE status = 'QUEUED'
                ORDER BY created_at, id
                LIMIT 1
                FOR UPDATE
                """);
    }

    /** 잠근 작업에 실행 권한과 최대 실행 기한을 설정한다. */
    public void markRunning(String id, String leaseToken) {
        jdbc.update("""
                UPDATE course_generation_jobs
                SET status = 'RUNNING',
                    lease_token = ?,
                    lease_until = DATE_ADD(
                        UTC_TIMESTAMP(6), INTERVAL 45 SECOND
                    ),
                    execution_deadline = DATE_ADD(
                        UTC_TIMESTAMP(6), INTERVAL 20 MINUTE
                    ),
                    started_at = UTC_TIMESTAMP(6)
                WHERE id = ?
                """,
                leaseToken, id
        );
    }

    /** 실행 권한과 기한이 유효한 작업만 성공으로 변경하고 변경 건수를 반환한다. */
    public int markSucceededIfLeaseValid(String id, String leaseToken, Long courseId) {
        return jdbc.update("""
                UPDATE course_generation_jobs
                SET status = 'SUCCEEDED',
                    course_id = ?,
                    completed_at = UTC_TIMESTAMP(6),
                    lease_token = NULL,
                    lease_until = NULL
                WHERE id = ?
                  AND status = 'RUNNING'
                  AND lease_token = ?
                  AND lease_until > UTC_TIMESTAMP(6)
                  AND execution_deadline > UTC_TIMESTAMP(6)
                """,
                courseId, id, leaseToken
        );
    }

    /** 유효한 실행 권한만 최대 실행 기한 이내에서 연장한다. */
    public int extendLeaseIfValid(String id, String leaseToken) {
        return jdbc.update("""
                UPDATE course_generation_jobs
                SET lease_until = LEAST(
                    DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 45 SECOND),
                    execution_deadline
                )
                WHERE id = ?
                  AND status = 'RUNNING'
                  AND lease_token = ?
                  AND lease_until > UTC_TIMESTAMP(6)
                  AND execution_deadline > UTC_TIMESTAMP(6)
                """,
                id, leaseToken
        );
    }

    /** 실행 권한 또는 최대 실행 기한이 만료된 후보를 최대 50건 조회한다. */
    public List<Map<String, Object>> findExpiredRunning() {
        return jdbc.queryForList("""
                SELECT id, lease_token
                FROM course_generation_jobs
                WHERE status = 'RUNNING'
                  AND (
                      lease_until <= UTC_TIMESTAMP(6)
                      OR execution_deadline <= UTC_TIMESTAMP(6)
                  )
                LIMIT 50
                """);
    }

    /** 작업 행을 잠그고 DB 시각으로 계산한 실행 권한 유효 여부를 함께 조회한다. */
    public Map<String, Object> lockJob(String id) {
        return jdbc.queryForMap("""
                SELECT *,
                       (
                           lease_until > UTC_TIMESTAMP(6)
                           AND execution_deadline > UTC_TIMESTAMP(6)
                       ) AS lease_valid
                FROM course_generation_jobs
                WHERE id = ?
                FOR UPDATE
                """,
                id
        );
    }

    /** 서비스가 행 잠금 후 실행 권한을 확인한 작업을 실패로 변경한다. */
    public void markFailed(String id, String errorCode) {
        jdbc.update("""
                UPDATE course_generation_jobs
                SET status = 'FAILED',
                    error_code = ?,
                    completed_at = UTC_TIMESTAMP(6),
                    lease_token = NULL,
                    lease_until = NULL
                WHERE id = ?
                """,
                errorCode, id
        );
    }

    /** 작업 소유자의 활성 계정 수를 조회한다. */
    public Long countActiveUser(Long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM users
                WHERE id = ? AND status = 'ACTIVE'
                """,
                Long.class, userId
        );
    }

    /** 사용자의 최근 작업을 최신순으로 최대 20건 조회한다. */
    public List<Map<String, Object>> findRecentByUser(Long userId) {
        return jdbc.queryForList("""
                SELECT *
                FROM course_generation_jobs
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 20
                """,
                userId
        );
    }

    /** 요청한 사용자 소유의 작업만 조회한다. */
    public List<Map<String, Object>> findOwned(String id, Long userId) {
        return jdbc.queryForList("""
                SELECT *
                FROM course_generation_jobs
                WHERE id = ? AND user_id = ?
                """,
                id, userId
        );
    }

    /** 결과 열람과 저장 증명 발급에 사용할 본인 코스를 잠근다. */
    public List<Map<String, Object>> lockOwnedResultCourse(Long courseId, Long userId) {
        return jdbc.queryForList("""
                SELECT id, user_id, status
                FROM courses
                WHERE id = ?
                  AND user_id = ?
                  AND status IN ('READY', 'SAVED')
                FOR UPDATE
                """,
                courseId, userId
        );
    }
}
