package vn.cineprime;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.cineprime.admin.ReportService;
import vn.cineprime.admin.AdminPresentation;

class ReportServiceTest {
    @Test void comparisonClampsShortMonthAndLeapDay(){
        var march=ReportService.range("month",LocalDate.of(2026,3,31),Instant.parse("2026-03-31T10:00:00Z"));
        assertEquals(LocalDate.of(2026,2,1),march.previousFrom());assertEquals(Instant.parse("2026-02-28T10:00:00Z"),march.previousEnd());
        var leap=ReportService.range("year",LocalDate.of(2024,2,29),Instant.parse("2024-02-29T08:00:00Z"));assertEquals(Instant.parse("2023-02-28T08:00:00Z"),leap.previousEnd());
        var historical=ReportService.range("month",LocalDate.of(2026,1,15),Instant.parse("2026-03-31T10:00:00Z"));assertEquals(Instant.parse("2025-12-31T17:00:00Z"),historical.previousEnd());
        var week=ReportService.range("week",LocalDate.of(2026,11,6),Instant.parse("2026-11-06T10:00:00Z"));assertEquals(LocalDate.of(2026,11,2),week.from());assertEquals(Instant.parse("2026-10-30T10:00:00Z"),week.previousEnd());
        assertTrue(ReportService.range("day",LocalDate.of(2027,1,1),Instant.parse("2026-11-06T10:00:00Z")).future());
    }
    @Test void friendlyTimeRoundTripsWholeMinutesAndRejectsInvalidTimes(){
        assertEquals("0",AdminPresentation.normalize(java.util.Map.of("fromMinute","00:00")).get("fromMinute"));
        assertEquals("1020",AdminPresentation.normalize(java.util.Map.of("fromMinute","17:00")).get("fromMinute"));
        assertEquals("1440",AdminPresentation.normalize(java.util.Map.of("toMinute","24:00")).get("toMinute"));
        assertEquals("24:00",AdminPresentation.time(1440));
        assertThrows(IllegalArgumentException.class,()->AdminPresentation.normalize(java.util.Map.of("fromMinute","24:00")));
        assertThrows(IllegalArgumentException.class,()->AdminPresentation.normalize(java.util.Map.of("toMinute","17:60")));
        assertEquals("9007199254740993",AdminPresentation.normalize(java.util.Map.of("amount","9007199254740993")).get("amount"));
    }
    @Test void csvEscapesQuotesAndSpreadsheetFormulas(){assertEquals("\"'@SUM(1)\"",ReportService.csvCell("@SUM(1)"));assertEquals("\"a\"\"b\"",ReportService.csvCell("a\"b"));assertEquals("\"85000\"",ReportService.csvCell(85000L));}
}
