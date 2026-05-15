package com.sokind.chat.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class UtcDateTimes {

	private static final ZoneId API_RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

	private UtcDateTimes() {
	}

	public static LocalDateTime now() {
		return LocalDateTime.now(ZoneOffset.UTC);
	}

	public static LocalDateTime toUtcLocalDateTime(OffsetDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);
	}

	public static OffsetDateTime toOffsetDateTime(LocalDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return dateTime.atOffset(ZoneOffset.UTC)
			.atZoneSameInstant(API_RESPONSE_ZONE)
			.toOffsetDateTime();
	}
}
