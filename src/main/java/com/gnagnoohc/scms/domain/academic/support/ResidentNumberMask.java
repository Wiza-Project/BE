package com.gnagnoohc.scms.domain.academic.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 주민번호는 저장하지 않는다(설계 문서 8-1) — {@code birth_date} + {@code gender}에서
 * 마스킹 표시(예: {@code 030412-1******})만 조립한다.
 *
 * <p>7번째 자리는 2000년 이전 출생이면 남자 1 / 여자 2, 2000년 이후 출생이면 남자 3 /
 * 여자 4다.
 * 우리가 들고 있는 {@code birth_date}는 4자리 연도까지 저장돼 있어 이 자리 숫자를
 * 정확히 계산할 수 있다.</p>
 */
public final class ResidentNumberMask {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int CENTURY_PIVOT_YEAR = 2000;

    private ResidentNumberMask() {
    }

    /**
     * @param gender "M" 또는 "F"(대소문자 무관). 그 외 값이거나 둘 중 하나라도 없으면
     *               마스킹 값을 조립할 수 없어 {@code null}을 반환한다("미입력"으로 표시).
     */
    public static String mask(LocalDate birthDate, String gender) {
        if (birthDate == null || gender == null) {
            return null;
        }

        char genderDigit;
        boolean bornBeforeCentury = birthDate.getYear() < CENTURY_PIVOT_YEAR;
        if ("M".equalsIgnoreCase(gender)) {
            genderDigit = bornBeforeCentury ? '1' : '3';
        } else if ("F".equalsIgnoreCase(gender)) {
            genderDigit = bornBeforeCentury ? '2' : '4';
        } else {
            return null;
        }

        return birthDate.format(YYMMDD) + "-" + genderDigit + "******";
    }
}
