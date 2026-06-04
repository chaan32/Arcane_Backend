package com.arcane.Arcane.Riot.Match.service;

import com.arcane.Arcane.Common.Exception.RiotAPI.CannotFoundSummoner;
import com.arcane.Arcane.Riot.Data.Champion.repository.ChampionRepository;
import com.arcane.Arcane.Riot.Match.domain.Match;
import com.arcane.Arcane.Riot.Match.dto.MatchDto;
import com.arcane.Arcane.Riot.Match.dto.minimal.MinimalMatchDto;
import com.arcane.Arcane.Riot.Match.repository.MatchParticipantRepository;
import com.arcane.Arcane.Riot.Match.repository.MatchRepository;
import com.arcane.Arcane.Riot.RiotInform.service.RiotApiService;
import com.arcane.Arcane.Riot.Summoner.domain.Summoner;
import com.arcane.Arcane.Web.Statistics.dto.ChampionSeasonStatisticsDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchService {
    private final RiotApiService riotApiService;
    private final MatchTimelineService matchTimelineService;
    private final ChampionRepository championRepository;
    private final MatchRepository matchRepository;
    private final MatchWriteService matchWriteService;
    private final ConcurrentMap<String, Object> matchCreationLocks = new ConcurrentHashMap<>();
    private final MatchParticipantRepository matchParticipantRepository;


    @Value("${riot.season2025-1}")
    private long seasonStartTime;


    /** 이번 시즌에 플레이한 챔피언 정보 반환하는 메소드
     *
     * @param puuid
     * @return
     * @throws CannotFoundSummoner
     */
    public List<ChampionSeasonStatisticsDto> getStatisticsOfMostChampion(String puuid) throws CannotFoundSummoner {
        log.info("MatchService - getStatisticsOfMostChampion : {}", puuid);


        // 1) 이번 시즌의 전적 matchID를 가져옴
        List<String> matchIds = riotApiService.getMatchIdByPuuid(puuid, seasonStartTime);
        log.info("{}의 시즌 전적 {}개 분석 시작",puuid, matchIds.size());

        // 2) 챔피언 별 통계를 저장할 MAP 생성
        Map<Long, ChampionSeasonStatisticsDto> statsMap = new HashMap<>();

        // 3) 각 전적 순회하며 데이터 집계
        for (String matchId : matchIds) {

            // API 호출 제한을 피하기 위해 각 호출 사이에 약간의 딜레이
            try { Thread.sleep(50); } catch (InterruptedException e) {}

            MinimalMatchDto minimalMatchInfo = riotApiService.getMinimalMatchInfo(matchId);
            log.info("minimalMatchInfo : {}", minimalMatchInfo);
            if (minimalMatchInfo == null || minimalMatchInfo.getInfo() == null) {
                log.warn("매치 정보를 가져오지 못했습니다: {}", matchId);
                continue;
            }

            long gameDuration = minimalMatchInfo.getInfo().getGameDuration();


            // 4) 해당 게임에서 내가 플레이한 정보를 찾아서 통계에 누적 함

            minimalMatchInfo.getInfo().getParticipants().stream()
                    .filter(p -> puuid.equals(p.getPuuid()))
                    .findFirst()
                    .ifPresent(myPerformance ->{
                        // 5) Map에서 분석 중인 게임에서 플레이한 챔피언이 없는 경우 가져옴, 아니면 그걸 반환
                        long championId = myPerformance.getChampionId();

                        ChampionSeasonStatisticsDto stats = statsMap.computeIfAbsent(championId, id -> new ChampionSeasonStatisticsDto(championId));

                        // 6) 챔피언 이름 주입
                        if (stats.getChampionName() == null) {
                            championRepository.findById(championId).ifPresent(champion -> {
                                stats.setChampionName(champion.getNameKo());
                            });
                        }

                        // 7) 경기 결과를 통계 객체에 넣어서 누적 시킴
                        stats.addMatchResult(
                                myPerformance.isWin(), myPerformance.getKills(), myPerformance.getDeaths(), myPerformance.getAssists(),
                                myPerformance.getTotalDamageDealtToChampions(), gameDuration
                        );
                    });


        }
        // 8) 리스트로 변환해서 반환하기
        return statsMap.values().stream()
                .sorted(Comparator.comparingInt(ChampionSeasonStatisticsDto::getGamesPlayed).reversed())
                .collect(Collectors.toList());
    }
    public MatchDto getMatchInfoById(String matchId) {
        return riotApiService.getMatchInfo(matchId);
    }

    public Optional<Match> getMatchByMatchId(String matchId) {
        return matchRepository.findMatchByMatchId(matchId);
    }

    public Match saveIfAbsent(Match newMatch){
        String matchId = newMatch.getMatchId();
        Object lock = matchCreationLocks.computeIfAbsent(matchId, ignored -> new Object());

        synchronized (lock) {
            return matchRepository.findMatchByMatchId(matchId)
                    .orElseGet(() -> {
                        try {
                            return matchWriteService.saveNewMatch(newMatch);
                        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                            return findMatchAfterConcurrentSave(matchId, e);
                        }
                    });
        }
    }
    @Transactional(readOnly = true)
    public String[] getMatchIds(Optional<Summoner> summoner, String puuid, Boolean refresh) throws CannotFoundSummoner {
        // 여기서 RIOT Service로 호출하는게 맞아

        // 존재하거나, refresh를 하지 않았거나
        if (summoner.isPresent() && !refresh) {
            log.info("있어서 API 호출 안 함 - matchid");
            String[] latestMatchIdArrayBySummonerId =
                    matchParticipantRepository.findLatestMatchIdArrayBySummonerId(summoner.get().getId());
            if (latestMatchIdArrayBySummonerId.length >= 20){
                return Arrays.copyOfRange(latestMatchIdArrayBySummonerId,0, 20);
            }
        }

        log.info("없어서 API 호출 함 혹은 전적 갱신 함  - matchid");
        return riotApiService.getSummonerMatchesV3(puuid);
    }

    public List<JsonNode> findEvents(String matchId, String puuid){
        return matchTimelineService.getEventsTimelineByPuuid(matchId, puuid);
    }

    private Match findMatchAfterConcurrentSave(String matchId, RuntimeException cause) {
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<Match> match = matchRepository.findMatchByMatchId(matchId);
            if (match.isPresent()) {
                return match.get();
            }
            sleepAfterConcurrentSave(attempt);
        }

        return matchRepository.findMatchByMatchId(matchId)
                .orElseThrow(() -> cause);
    }

    private void sleepAfterConcurrentSave(int attempt) {
        try {
            Thread.sleep(50L * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
