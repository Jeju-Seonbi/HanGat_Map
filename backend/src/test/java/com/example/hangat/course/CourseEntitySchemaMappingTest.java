package com.example.hangat.course;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CourseEntitySchemaMappingTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void generatesMigrationCompatibleNumericAndNullableColumns() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertColumn(connection, "regions", "id", Types.SMALLINT, false, null, null);
            assertColumn(connection, "regions", "kma_grid_x", Types.SMALLINT, true, null, null);
            assertColumn(connection, "regions", "kma_grid_y", Types.SMALLINT, true, null, null);
            assertColumn(connection, "regions", "display_order", Types.TINYINT, false, null, null);
            assertColumn(connection, "place_categories", "id", Types.SMALLINT, false, null, null);
            assertColumn(
                    connection,
                    "place_categories",
                    "display_order",
                    Types.SMALLINT,
                    false,
                    null,
                    null);
            assertColumn(connection, "places", "region_id", Types.SMALLINT, false, null, null);
            assertColumn(
                    connection,
                    "places",
                    "primary_category_id",
                    Types.SMALLINT,
                    false,
                    null,
                    null);
            assertColumn(connection, "courses", "people", Types.SMALLINT, false, null, null);
            assertColumn(connection, "courses", "parent_course_id", Types.BIGINT, true, null, null);
            assertColumn(connection, "course_items", "day_no", Types.SMALLINT, false, null, null);
            assertColumn(connection, "course_items", "position", Types.SMALLINT, false, null, null);
            assertColumn(
                    connection,
                    "course_items",
                    "inbound_travel_minutes",
                    Types.SMALLINT,
                    true,
                    null,
                    null);
            assertColumn(
                    connection,
                    "course_items",
                    "recommendation_score",
                    Types.NUMERIC,
                    true,
                    8,
                    4);

            assertSelfReference(connection);
        }
    }

    private void assertColumn(
            Connection connection,
            String tableName,
            String columnName,
            int expectedJdbcType,
            boolean expectedNullable,
            Integer expectedPrecision,
            Integer expectedScale
    ) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(
                connection.getCatalog(), null, null, null)) {
            boolean found = false;
            while (columns.next()) {
                if (!tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        || !columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    continue;
                }
                found = true;
                assertThat(columns.getInt("DATA_TYPE")).isEqualTo(expectedJdbcType);
                assertThat(columns.getInt("NULLABLE"))
                        .isEqualTo(expectedNullable
                                ? DatabaseMetaData.columnNullable
                                : DatabaseMetaData.columnNoNulls);
                if (expectedPrecision != null) {
                    assertThat(columns.getInt("COLUMN_SIZE")).isEqualTo(expectedPrecision);
                }
                if (expectedScale != null) {
                    assertThat(columns.getInt("DECIMAL_DIGITS")).isEqualTo(expectedScale);
                }
                break;
            }
            assertThat(found)
                    .as("column %s.%s", tableName, columnName)
                    .isTrue();
        }
    }

    private void assertSelfReference(Connection connection) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet keys = metadata.getImportedKeys(
                connection.getCatalog(), null, "COURSES")) {
            boolean found = false;
            while (keys.next()) {
                if ("parent_course_id".equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && "courses".equalsIgnoreCase(keys.getString("PKTABLE_NAME"))) {
                    found = true;
                    break;
                }
            }
            assertThat(found).as("courses.parent_course_id self FK").isTrue();
        }
    }
}
