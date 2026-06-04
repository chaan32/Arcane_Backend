package com.arcane.Arcane.Riot.Match.dto.v3;

import com.arcane.Arcane.Riot.Match.domain.Match;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetaDataResDto {
    private String matchId;
    private long gameCreation; // 게임 생성 시각 (타임스탬프)
    private long gameDuration; // 게임 시간 (초)
    private long gameEndTimestamp; // 게임 종료 시각 (타임스탬프)
    private String gameMode; // 게임 모드
    private Integer queueId;

    public static MetaDataResDto of(Match match) {
        return MetaDataResDto.builder()
                .matchId(match.getMatchId())
                .gameCreation(match.getGameCreation())
                .gameDuration(match.getGameDuration())
                .gameEndTimestamp(match.getGameEndTimestamp())
                .gameMode(match.getGameMode())
                .queueId(match.getQueueId())
                .build();
    }
}
