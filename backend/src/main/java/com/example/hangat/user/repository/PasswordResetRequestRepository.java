package com.example.hangat.user.repository;

import com.example.hangat.user.model.PasswordResetRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PasswordResetRequestRepository
        extends JpaRepository<PasswordResetRequest, Long> {

    /**
     * 2단계 조회 키.
     * 코드가 틀렸을 때도 행을 찾아야 attempt_count를 올릴 수 있어서
     * 코드 해시가 아니라 requestId로 찾는다.
     */
    Optional<PasswordResetRequest> findByRequestId(String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PasswordResetRequest request where request.requestId = :requestId")
    Optional<PasswordResetRequest> findByRequestIdForUpdate(@Param("requestId") String requestId);

    // 3단계
    Optional<PasswordResetRequest> findByTicketHash(String ticketHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PasswordResetRequest request where request.ticketHash = :ticketHash")
    Optional<PasswordResetRequest> findByTicketHashForUpdate(@Param("ticketHash") String ticketHash);

    List<PasswordResetRequest> findAllByUserIdAndUsedAtIsNull(Long userId);
}
