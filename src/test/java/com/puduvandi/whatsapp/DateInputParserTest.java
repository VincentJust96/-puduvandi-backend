package com.puduvandi.whatsapp;

import com.puduvandi.whatsapp.service.DateInputParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DateInputParser")
class DateInputParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Test
    void parsesKeywordsAndFormats() {
        assertThat(DateInputParser.parseDate("Today", TODAY)).contains(TODAY);
        assertThat(DateInputParser.parseDate(" tomorrow ", TODAY)).contains(TODAY.plusDays(1));
        assertThat(DateInputParser.parseDate("05-10-2026", TODAY)).contains(LocalDate.of(2026, 10, 5));
        assertThat(DateInputParser.parseDate("5/10/2026", TODAY)).contains(LocalDate.of(2026, 10, 5));
    }

    @Test
    void rejectsNonsenseAndImpossibleDates() {
        assertThat(DateInputParser.parseDate("31-02-2026", TODAY)).isEmpty();
        assertThat(DateInputParser.parseDate("next friday", TODAY)).isEmpty();
        assertThat(DateInputParser.parseDate(null, TODAY)).isEmpty();
    }

    @Test
    void dateTimeUsesDefaultOrGivenTime() {
        assertThat(DateInputParser.parseDateTime("05-10-2026", TODAY, TEN))
                .contains(LocalDateTime.of(2026, 10, 5, 10, 0));
        assertThat(DateInputParser.parseDateTime("tomorrow 14:30", TODAY, TEN))
                .contains(LocalDateTime.of(2026, 10, 2, 14, 30));
        assertThat(DateInputParser.parseDateTime("05-10-2026 25:00", TODAY, TEN)).isEmpty();
        assertThat(DateInputParser.parseDateTime("05-10-2026 later", TODAY, TEN)).isEmpty();
    }

    @Test
    void quantityMustBePositiveWholeNumber() {
        assertThat(DateInputParser.parseQuantity(" 3 ")).contains(3);
        assertThat(DateInputParser.parseQuantity("0")).isEmpty();
        assertThat(DateInputParser.parseQuantity("2.5")).isEmpty();
        assertThat(DateInputParser.parseQuantity("-1")).isEmpty();
        assertThat(DateInputParser.parseQuantity("abc")).isEmpty();
    }
}
