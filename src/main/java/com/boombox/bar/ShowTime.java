package com.boombox.bar;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;

/**
 * Datas especiais pelo relógio do computador (servidor): show do DJ Leo no fim de semana e festa junina em junho.
 */
public final class ShowTime {
    /** Hora do show (sábado e domingo, das 21h às 22h). */
    public static final int SHOW_HOUR = 21;

    private ShowTime() {}

    public static boolean isShowNow() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek d = now.getDayOfWeek();
        return (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) && now.getHour() == SHOW_HOUR;
    }

    /** Faltam uns 15 minutos para o show? (aviso no chat) */
    public static boolean isShowSoon() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek d = now.getDayOfWeek();
        return (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) && now.getHour() == SHOW_HOUR - 1 && now.getMinute() >= 45;
    }

    public static boolean isJune() {
        return LocalDateTime.now().getMonth() == Month.JUNE;
    }
}
