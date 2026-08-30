package com.example.hangat.map.image;

import com.example.hangat.map.image.model.PlaceImageItem;
import com.example.hangat.map.model.entity.Place;
import com.example.hangat.map.model.entity.PlaceImage;
import com.example.hangat.map.repository.PlaceImageRepository;
import com.example.hangat.map.repository.PlaceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** 사진 청크 저장. 트랜잭션 분리 사유는 PlaceDetailIngestWriter 참고. */
@Component
public class PlaceImageIngestWriter {

    private final PlaceRepository placeRepository;
    private final PlaceImageRepository imageRepository;

    public PlaceImageIngestWriter(PlaceRepository placeRepository, PlaceImageRepository imageRepository) {
        this.placeRepository = placeRepository;
        this.imageRepository = imageRepository;
    }

    /** 한 장소의 사진 전체. */
    public record Row(Long placeId, List<PlaceImageItem> items) {
    }

    public record ChunkResult(int updated, int saved) {
    }

    @Transactional
    public ChunkResult saveChunk(List<Row> rows, String attribution) {
        int updated = 0;
        int saved = 0;

        for (Row row : rows) {
            Place place = placeRepository.findById(row.placeId()).orElse(null);
            if (place == null) {
                continue;
            }
            // 재적재 = 지우고 다시 넣기 (사진은 부분 갱신할 근거가 없다)
            imageRepository.deleteByPlace(place);

            int order = 0;
            Set<String> seen = new HashSet<>();
            for (PlaceImageItem it : row.items()) {
                String url = toHttps(nz(it.originimgurl()));
                // 같은 URL이 두 번 오는 응답 방어 - UK 로 죽는 대신 첫 장만 남긴다
                if (url == null || !seen.add(url)) {
                    continue;
                }
                imageRepository.save(PlaceImage.builder()
                        .place(place)
                        .imageUrl(url)
                        .thumbnailUrl(toHttps(nz(it.smallimageurl())))
                        .urlHash(sha256(url))
                        .caption(nz(it.imgname()))
                        .licenseCode(nz(it.cpyrhtDivCd()))
                        .attribution(attribution)
                        .sortOrder(order)
                        .isPrimary(order == 0)
                        .build());
                order++;
                saved++;
            }
            if (order > 0) {
                updated++;
            }
        }
        return new ChunkResult(updated, saved);
    }

    /** KTO가 http/https를 섞어 준다(실측 48/57이 http). https 페이지에서 http 사진은 차단되므로 통일한다 */
    static String toHttps(String url) {
        return (url != null && url.startsWith("http://")) ? "https://" + url.substring(7) : url;
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
