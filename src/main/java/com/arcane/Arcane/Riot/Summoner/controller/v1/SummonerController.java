package com.arcane.Arcane.Riot.Summoner.controller.v1;

import com.arcane.Arcane.Common.Exception.RiotAPI.CannotFoundSummoner;
import com.arcane.Arcane.Riot.Match.domain.Match;
import com.arcane.Arcane.Riot.Match.dto.MatchDto;
import com.arcane.Arcane.Riot.Match.dto.v3.MatchInfoResDto;
import com.arcane.Arcane.Riot.Match.dto.v4.MinimalMatchResDto;
import com.arcane.Arcane.Riot.Match.service.MatchService;
import com.arcane.Arcane.Riot.RiotInform.dto.MasteryDto;
import com.arcane.Arcane.Riot.RiotInform.dto.ProfileResDto;
import com.arcane.Arcane.Riot.RiotInform.service.RiotApiService;
import com.arcane.Arcane.Riot.Summoner.dto.SummonerKeywordResDto;
import com.arcane.Arcane.Riot.Summoner.dto.SummonerTierResDto;
import com.arcane.Arcane.Score.service.PythonService;
import com.arcane.Arcane.Web.Statistics.dto.ChampionSeasonStatisticsDto;
import com.arcane.Arcane.Riot.Summoner.service.SummonerService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Queue;

@RestController
@RequestMapping("/api/v1/summoner")
@Slf4j
@RequiredArgsConstructor
public class SummonerController {
    private final SummonerService summonerService;
    private final MatchService matchService;
    private final RiotApiService riotApiService;
    private final PythonService pythonService;

    /** gameName과 tagLine을 통해서 티어 정보를 검색하는 API이다.
     * - DTO 수정 완료
     * @param gameName
     * @param tagLine
     * @return Summoner
     * @throws CannotFoundSummoner
     */
    @Operation(summary = "소환사 티어 정보 조회 (전적갱신 반영 ㅇ)", description = "게임 닉네임과 태그라인을 통해 해당 소환사의 현재 티어 정보를 조회합니다.")
    @GetMapping("/tier")
    public ResponseEntity<SummonerTierResDto> summonerTierInfo(@RequestParam String gameName, @RequestParam String tagLine, @RequestParam Boolean refresh) throws CannotFoundSummoner {
        log.info("SummonerController - summonerTierInfo /tier 호출 ");
        SummonerTierResDto summonerTierInfo = summonerService.getSummonerTierInfo(gameName, tagLine, refresh);
        return ResponseEntity.ok(summonerTierInfo);
    }



    /** gameName과 tagLine을 통해서 숙련도 정보를 검색하는 API이다. -> 리신 숙련도 15만점 등
     *
     * @param gameName
     * @param tagLine
     * @return
     * @throws CannotFoundSummoner
     */
    @Operation(summary = "소환사 챔피언 숙련도 조회", description = "소환사가 보유한 챔피언들의 숙련도 점수 및 레벨 정보를 조회합니다.")
    @GetMapping("/mastery")
    public ResponseEntity<List<MasteryDto>> summonerMasteryInfo(@RequestParam String gameName, @RequestParam String tagLine) throws CannotFoundSummoner {
        log.info("SummonerController - summonerMasteryInfo /mastery 호출 ");
        List<MasteryDto> masteryInfo = summonerService.getSummonerMasteryInfo(gameName, tagLine);
        return ResponseEntity.ok(masteryInfo);
    }


    /** gameName과 tagLine을 통해서 전반적인 최근 20게임의 전적을 검색된 사람 기준으로 보내주는 API이다. (전적 갱신 반영 ㅇ)
     *
     * @param gameName
     * @param tagLine
     * @return
     * @throws CannotFoundSummoner
     */
    @Operation(summary = "최근 매치 요약 리스트 조회", description = "해당 소환사의 최근 20게임 매치 요약 정보를 조회합니다.")
    @GetMapping("/matches")
    public ResponseEntity<Queue<MatchInfoResDto>> summonerMatches(@RequestParam String gameName, @RequestParam String tagLine, @RequestParam Boolean refresh) throws CannotFoundSummoner {
        log.info("SummonerController - summonerMatches /matches 호출 ");
        Queue<MatchInfoResDto> matchDtos = summonerService.getSummonerMatches(gameName, tagLine, refresh);
        return ResponseEntity.ok(matchDtos);
    }

    /** matchId를 받아서 해당 게임의 상세한 정보를 보내주는 API이다.
     *
     * @param matchId 게임 정보의 식별 id 값
     * @return
     */
    @Operation(summary = "매치 상세 정보 조회", description = "특정 매치 ID를 통해 해당 게임의 모든 플레이어 기록 등 상세 데이터를 조회합니다.")
    @GetMapping("/matchInfo")
    public ResponseEntity<MatchDto> summonerMatchInfo(@RequestParam String matchId) {
        MatchDto matchInfo = matchService.getMatchInfoById(matchId);
        return ResponseEntity.ok(matchInfo);
    }

    /** 🚨미완성 🚨 전적을 검색한 소환사의 이번 시즌에 가장 많이 챔피언을 보내주는 API이다.
     *
     * @param gameName
     * @param tagLine
     * @return
     * @throws CannotFoundSummoner
     */
    @Operation(summary = "소환사 모스트 챔피언 통계", description = "소환사가 이번 시즌에 가장 많이 플레이한 챔피언 통계 정보를 조회합니다.")
    @GetMapping("/most")
    public ResponseEntity<List<ChampionSeasonStatisticsDto>> summonerMostInfo(@RequestParam String gameName, @RequestParam String tagLine) throws CannotFoundSummoner{
        log.info("SummonerController - summonerMostInfo /most 호출 ");
        String puuid = summonerService.findPuuid(gameName, tagLine, summonerService.findSummoner(gameName, tagLine));
        List<ChampionSeasonStatisticsDto> sortedStats = matchService.getStatisticsOfMostChampion(puuid);
        return ResponseEntity.ok(sortedStats);
    }




    @Operation(summary = "소환사 프로필 조회V2", description = "소환사의 기본 프로필(레벨, 아이콘 등) 정보를 조회합니다. (전적 갱신 ㅇ)")
    @GetMapping("/profile")
    public ResponseEntity<ProfileResDto> getProfileV2(@RequestParam String gameName, @RequestParam String tagLine, @RequestParam Boolean refresh) throws CannotFoundSummoner {
        log.info("SummonerController - getProfile  호출 ");
        ProfileResDto profileInfo = summonerService.getProfile(gameName, tagLine, refresh);
        return ResponseEntity.ok(profileInfo);
    }

    @Operation(summary = "키워드로 소환사 실시간 검색", description = "키워드가 포함된 소환사 리스트를 조회합니다.")
    @GetMapping("/contain/{keyword}")
    public ResponseEntity<List<SummonerKeywordResDto>> getSummonersByKeyWord(@PathVariable String keyword){
        List<SummonerKeywordResDto> summonerByKeyword = summonerService.findSummonerByKeyword(keyword);
        return ResponseEntity.ok(summonerByKeyword);
    }

    @Operation(summary = "키워드로 소환사 실시간 검색 V2", description = "키워드(게임네임과 태그라인이 포함될 수도 있음)가 포함된 소환사 리스트를 조회합니다.")
    @GetMapping("/contain/{keyword}/V2")
    public ResponseEntity<List<SummonerKeywordResDto>> getSummonersByKeyWordV2(@PathVariable String keyword){
        List<SummonerKeywordResDto> summonerByKeyword = summonerService.findSummonerByKeywordV2(keyword);
        return ResponseEntity.ok(summonerByKeyword);
    }


    @Operation(summary = "매치 id 얻기", description = "page에 해당하는 매치 id를 얻을 수 있습니다. ex ) 1 -> 0 ~ 19 / 2 ->  20 ~ 39")
    @GetMapping("/match-id/{page}")
    public ResponseEntity<String[]> getMatchId(@PathVariable int page, @RequestParam String gameName, @RequestParam String tagLine) throws CannotFoundSummoner {
        String[] summonerMatchesIdV2 = summonerService.getSummonerMatchesIdV2(gameName, tagLine, page);
        return ResponseEntity.ok(summonerMatchesIdV2);
    }

    @Operation(summary = "1차적인 Match 정보  (병렬 수행) - 안 쓸 것", description = "1차적으로 보이는 간단 매치 정보 ")
    @GetMapping("/match/summary/{matchId}")
    public ResponseEntity<MinimalMatchResDto> getSummaryMatchInfo(@RequestParam String puuid, @PathVariable String matchId) throws CannotFoundSummoner {
        MinimalMatchResDto minimalMatchData = summonerService.getMinimalMatchData(puuid, matchId);
        return ResponseEntity.ok(minimalMatchData);
    }

    @Operation(summary = "매치 id 및 요약 정보 얻기", description = "page에 해당하는 매치 정보 전체를 얻을 수 있습니다. ex ) 1 -> 0 ~ 19 / 2 ->  20 ~ 39")
    @GetMapping("/match-id/{page}/v3")
    public ResponseEntity<Queue<MatchInfoResDto>> getSummaryMatch(@PathVariable int page, @RequestParam String puuid) throws CannotFoundSummoner {
        String[] summonerMatchesIdV3 = summonerService.getSummonerMatchesIdV3(puuid, page);
        log.info("size of id : {}", summonerMatchesIdV3.length);
        Queue<MatchInfoResDto> summonerSummaryMatch = summonerService.getSummonerSummaryMatch(summonerMatchesIdV3, puuid);
        return ResponseEntity.ok(summonerSummaryMatch);
    }

    @Operation(summary = "timeline 얻기", description = "모르겠음")
    @GetMapping("/match/timeline")
    public ResponseEntity<List<JsonNode>> getMatchTimeline(@RequestParam String puuid, @RequestParam String matchId) throws CannotFoundSummoner {
        List<JsonNode> events = matchService.findEvents(matchId, puuid);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/getPython")
    public ResponseEntity<Integer> getsdas(){
        return ResponseEntity.ok(pythonService.getRandomNumberFromPython());
    }
}
