package com.example.hangat.course;

import com.example.hangat.course.model.TourPlaceDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TourApiServiceTest {

    @Test
    void collectsMoreThanTenAcrossPagesAndUsesTotalCount() {
        Fixture fixture = fixture();
        List<String> first = IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).toList();
        fixture.expectPage(1, response(1, 12, first));
        fixture.expectPage(2, response(2, 12, List.of("11", "12")));

        List<TourPlaceDto> result = fixture.service().getTourPlaces();

        assertThat(result).hasSize(12);
        assertThat(result.get(10).getContentId()).isEqualTo("11");
        fixture.server().verify();
    }

    @Test
    void removesDuplicateContentIdsWhileKeepingFirstOrder() {
        Fixture fixture = fixture();
        fixture.expectPage(1, response(1, 4, List.of("1", "2")));
        fixture.expectPage(2, response(2, 4, List.of("2", "3")));

        List<TourPlaceDto> result = fixture.service().getTourPlaces();

        assertThat(result).extracting(TourPlaceDto::getContentId)
                .containsExactly("1", "2", "3");
        fixture.server().verify();
    }

    @Test
    void stopsOnEmptyPageEvenWhenTotalCountIsLarger() {
        Fixture fixture = fixture();
        fixture.expectPage(1, response(1, 500, List.of("1")));
        fixture.expectPage(2, response(2, 500, List.of()));

        assertThat(fixture.service().getTourPlaces())
                .extracting(TourPlaceDto::getContentId)
                .containsExactly("1");
        fixture.server().verify();
    }

    @Test
    void stopsAtThreePagesAndThreeHundredRawCandidates() {
        Fixture fixture = fixture();
        for (int page = 1; page <= TourApiService.MAX_PAGES; page++) {
            int offset = (page - 1) * TourApiService.PAGE_SIZE;
            List<String> ids = IntStream.rangeClosed(1, TourApiService.PAGE_SIZE)
                    .mapToObj(index -> String.valueOf(offset + index))
                    .toList();
            fixture.expectPage(page, response(page, 1_000, ids));
        }

        List<TourPlaceDto> result = fixture.service().getTourPlaces();

        assertThat(result).hasSize(TourApiService.MAX_RAW_CANDIDATES);
        assertThat(result.get(0).getContentId()).isEqualTo("1");
        assertThat(result.get(result.size() - 1).getContentId()).isEqualTo("300");
        fixture.server().verify();
    }

    @Test
    void returnsEmptyForNullItemsWithoutRequestingAnotherPage() {
        Fixture fixture = fixture();
        fixture.expectPage(1, """
                {"response":{"header":{"resultCode":"0000"},"body":{
                  "items":null,"numOfRows":100,"pageNo":1,"totalCount":20
                }}}
                """);

        assertThat(fixture.service().getTourPlaces()).isEmpty();
        fixture.server().verify();
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(
                new TourApiService(builder.build(), "https://tour.test/areaBasedList2", "decoded-key"),
                server);
    }

    private String response(int pageNo, int totalCount, List<String> contentIds) {
        String items = contentIds.stream()
                .map(id -> """
                        {"contentid":"%s","title":"장소%s","addr1":"제주특별자치도 제주시",
                         "mapy":33.4,"mapx":126.5,"cat1":"A01"}
                        """.formatted(id, id).trim())
                .reduce((first, second) -> first + "," + second)
                .orElse("");
        return """
                {"response":{"header":{"resultCode":"0000"},"body":{
                  "items":{"item":[%s]},"numOfRows":100,"pageNo":%d,"totalCount":%d
                }}}
                """.formatted(items, pageNo, totalCount);
    }

    private record Fixture(TourApiService service, MockRestServiceServer server) {
        private void expectPage(int pageNo, String response) {
            server.expect(once(), requestTo(allOf(
                            containsString("pageNo=" + pageNo),
                            containsString("numOfRows=100"),
                            containsString("arrange=C"),
                            containsString("areaCode=39"))))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        }
    }
}
