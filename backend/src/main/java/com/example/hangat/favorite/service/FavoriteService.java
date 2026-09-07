package com.example.hangat.favorite.service;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.common.model.PageResponse;
import com.example.hangat.favorite.model.Favorite;
import com.example.hangat.favorite.model.FavoriteResponse;
import com.example.hangat.favorite.repository.FavoriteRepository;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.repository.PlaceRepository;
import com.example.hangat.user.model.User;
import com.example.hangat.user.model.UserStatus;
import com.example.hangat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원별 찜 관리.
 * 조회는 본인 데이터만 반환하고, 저장·해제는 반복 요청에도 같은 결과를 유지한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favorites;
    private final PlaceRepository places;
    private final UserRepository users;

    // ────────────────────────── 목록 및 상태 조회 ──────────────────────────

    public PageResponse<FavoriteResponse> list(
            Long userId, int page, int size, String sort
    ) {
        requireActive(userId);

        Sort order = switch (sort) {
            case "recent" -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
            case "name" -> Sort.by(
                    Sort.Order.asc("place.name"),
                    Sort.Order.desc("id")
            );
            case "category" -> Sort.by(
                    Sort.Order.asc("place.primaryCategory.name"),
                    Sort.Order.asc("place.name"),
                    Sort.Order.desc("id")
            );
            default -> throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        };

        return PageResponse.from(
                favorites.findByUserId(
                        userId,
                        PageRequest.of(page, size, order)
                ).map(FavoriteResponse::from)
        );
    }

    /** 지도에서 현재 장소의 하트 상태를 확인한다. */
    public boolean contains(Long userId, Long placeId) {
        requireActive(userId);
        return favorites.existsByUserIdAndPlaceId(userId, placeId);
    }

    // ────────────────────────── 찜 저장 및 해제 ──────────────────────────

    @Transactional
    public void add(Long userId, Long placeId) {
        User user = lockActiveUser(userId);

        // 같은 회원의 동시 요청은 회원 행 잠금으로 순서대로 처리한다.
        if (favorites.existsByUserIdAndPlaceId(userId, placeId)) {
            return;
        }

        Place place = places.findById(placeId)
                .orElseThrow(() ->
                        new BaseException(BaseResponseStatus.PLACE_NOT_FOUND));

        favorites.save(new Favorite(user, place));
    }

    @Transactional
    public void remove(Long userId, Long placeId) {
        lockActiveUser(userId);

        // 이미 해제된 찜이면 아무것도 하지 않는다.
        favorites.findByUserIdAndPlaceId(userId, placeId)
                .ifPresent(favorites::delete);
    }

    // ────────────────────────── 계정 확인 ──────────────────────────

    private void requireActive(Long userId) {
        if (userId == null) {
            throw new BaseException(BaseResponseStatus.LOGIN_REQUIRED);
        }
        if (!users.existsByIdAndStatus(userId, UserStatus.ACTIVE)) {
            throw new BaseException(BaseResponseStatus.ACCOUNT_SUSPENDED);
        }
    }

    private User lockActiveUser(Long userId) {
        if (userId == null) {
            throw new BaseException(BaseResponseStatus.LOGIN_REQUIRED);
        }

        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new BaseException(BaseResponseStatus.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(BaseResponseStatus.ACCOUNT_SUSPENDED);
        }
        return user;
    }
}
