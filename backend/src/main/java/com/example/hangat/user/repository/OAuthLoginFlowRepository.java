package com.example.hangat.user.repository;

import com.example.hangat.user.model.oauth.OAuthLoginFlow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 *  OAuth 가입, 연결 진행상태를 조회.
 *  중복 가입이나 동시에 들어오는 계정 연결 요청을 한번만 처리하도록함.
 *  (비관적 락으로 할 것.)
 */
public interface OAuthLoginFlowRepository
        extends JpaRepository<OAuthLoginFlow, Long> {

    @Query("""
            select flow
            from OAuthLoginFlow flow
            left join fetch flow.targetUser
            where flow.flowTokenHash = :flowTokenHash
            """)
    Optional<OAuthLoginFlow> findByFlowTokenHash(
            @Param("flowTokenHash") String flowTokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select flow
            from OAuthLoginFlow flow
            left join fetch flow.targetUser
            where flow.flowTokenHash = :flowTokenHash
            """)
    Optional<OAuthLoginFlow> findByFlowTokenHashForUpdate(
            @Param("flowTokenHash") String flowTokenHash
    );
}
