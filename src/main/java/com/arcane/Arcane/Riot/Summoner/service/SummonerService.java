package com.arcane.Arcane.Riot.Summoner.service;

import com.arcane.Arcane.Common.Exception.Normal.CannotFoundChampion;
import com.arcane.Arcane.Common.Exception.RiotAPI.CannotFoundSummoner;
import com.arcane.Arcane.Common.Exception.RiotAPI.RiotInternalError;
import com.arcane.Arcane.Riot.Data.Champion.Champion;
import com.arcane.Arcane.Riot.Data.Champion.repository.ChampionRepository;
import com.arcane.Arcane.Riot.Data.Champion.ChampionService;
import com.arcane.Arcane.Riot.Match.domain.Match;
import com.arcane.Arcane.Riot.Match.domain.MatchParticipant;
import com.arcane.Arcane.Riot.Match.dto.InfoDto;
import com.arcane.Arcane.Riot.Match.dto.MatchDto;
import com.arcane.Arcane.Riot.Match.dto.ParticipantDto;
import com.arcane.Arcane.Riot.Match.dto.v3.MatchInfoResDto;
import com.arcane.Arcane.Riot.Match.dto.v3.MetaDataResDto;
import com.arcane.Arcane.Riot.Match.dto.v3.MyDataResDto;
import com.arcane.Arcane.Riot.Match.dto.v3.ParticipantsResDto;
import com.arcane.Arcane.Riot.Match.dto.v4.MinimalMatchResDto;
import com.arcane.Arcane.Riot.Match.service.MatchService;
import com.arcane.Arcane.Riot.RiotInform.dto.MasteryDto;
import com.arcane.Arcane.Riot.RiotInform.dto.ProfileResDto;
import com.arcane.Arcane.Riot.RiotInform.dto.RiotAccountDto;
import com.arcane.Arcane.Riot.RiotInform.service.RiotApiService;
import com.arcane.Arcane.Riot.Summoner.domain.Summoner;
import com.arcane.Arcane.Riot.Summoner.dto.SummonerDto;
import com.arcane.Arcane.Riot.Summoner.dto.SummonerKeywordResDto;
import com.arcane.Arcane.Riot.Summoner.dto.SummonerTierResDto;
import com.arcane.Arcane.Riot.Summoner.repository.SummonerRepository;
import com.arcane.Arcane.Score.service.PythonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummonerService {
    private final SummonerRepository summonerRepository;
    private final RiotApiService riotApiService;
    private final ChampionRepository championRepository;
    private final MatchService matchService;
    private final ChampionService championService;
    private final PythonService pythonService;
    private final SummonerWriteService summonerWriteService;
    private final ConcurrentMap<String, Object> summonerCreationLocks = new ConcurrentHashMap<>();

    /** gameName, tagLine으로 Optional<Summoner>를 반환하는 메소드
     *
     * @param gameName
     * @param tagLine
     * @return
     */
    @Transactional(readOnly = true)
    public Optional<Summoner> findSummoner(String gameName, String tagLine) {
        return summonerRepository.findSummonerByTrimmedGameNameAndTagLine(gameName.replace(" ", ""), tagLine);
    }
    /** 최근 전적 MatchId를 반환하는 메소드
     *
     * @param gameName, tagLine
     * @return
     * @throws CannotFoundSummoner
     */
    private String[] getSummonerMatchesId(String gameName, String tagLine) throws CannotFoundSummoner {
        Optional<Summoner> summoner = this.findSummoner(gameName, tagLine);
        return riotApiService.getSummonerMatches(SummonerDto.setInform(gameName, tagLine, findPuuid(gameName, tagLine, summoner)));
    }

    public String[] getSummonerMatchesIdV2(String gameName, String tagLine, int page) throws CannotFoundSummoner {
        String puuid = findPuuid(gameName, tagLine, findSummoner(gameName, tagLine));
        return riotApiService.getSummonerMatchesV2(puuid, page);
    }

    public String[] getSummonerMatchesIdV3(String puuid, int page) throws CannotFoundSummoner {
        return riotApiService.getSummonerMatchesV2(puuid, page);
    }

    /** gameName, tagLine, Optional<Summoner>로 puuid 반환하는 메소드
     *
     * @param gameName
     * @param tagLine
     * @param summoner
     * @return
     * @throws CannotFoundSummoner
     */
    public String findPuuid(String gameName, String tagLine, Optional<Summoner> summoner) throws CannotFoundSummoner {
        return summoner.isPresent()
                ? summoner.get().getPuuid()
                : riotApiService.getSummonerInfo(gameName, tagLine).getPuuid();
    }


    /** gameName, tagLine을 통해서 티어 정보를 반한 하는 메소드
     *
     * @param gameName
     * @param tagLine
     * @return
     * @throws CannotFoundSummoner
     */
    public SummonerTierResDto getSummonerTierInfo(String gameName, String tagLine, Boolean refresh) throws CannotFoundSummoner {
        log.info("SummonerService - getSummonerTierInfo : {}#{}", gameName, tagLine);

        // 무조건적인 API호출 ㄴㄴ
        Optional<Summoner> optionalSummoner = this.findSummoner(gameName, tagLine);

        if (optionalSummoner.isPresent() && !refresh) { // 전적 갱신을 하지도 않았고, 이미 DB에 정보가 있는 경우,
            log.info("티어 있어서 안 찾음 ");
            return SummonerTierResDto.of(optionalSummoner.get());
        }
        // 전적 갱신을 했거나, DB에 없는 사람
        log.info("티어 없어서  찾음 ");
        RiotAccountDto accountInfo = riotApiService.getSummonerInfo(gameName, tagLine);
        String puuid = accountInfo.getPuuid();

        SummonerDto tierInfo = riotApiService.getSummonerTierInfo(
                SummonerDto.setInform(accountInfo.getGameName(), accountInfo.getTagLine(), puuid)
        );

        Optional<Summoner> summoner = summonerRepository.findSummonerByPuuid(puuid);

        Summoner savedS = this.SaveOrUpateSummoner(tierInfo, summoner);
        return SummonerTierResDto.of(savedS);
    }



    /** gameName, tagLine을 통해서 숙련도 리스트를 반환하는 메소드
     *
     * @param gameName
     * @param tagLine
     * @return
     * @throws CannotFoundSummoner
     */
    public List<MasteryDto> getSummonerMasteryInfo(String gameName, String tagLine) throws CannotFoundSummoner {
        log.info("Summoner Service - getSummonerMasteryInfo : {}#{}", gameName, tagLine);

        Optional<Summoner> summoner = findSummoner(gameName, tagLine);
        String puuid = findPuuid(gameName, tagLine, summoner);

        List<MasteryDto> masteryInfo = riotApiService.getMasteryInfo(puuid);

        setChampionName(masteryInfo);

        return masteryInfo;
    }


    /** gameName, tagLine을 통해서 전적을 검색 내용을 반환하는 메소드
     *
     * @param gameName
     * @param tagLine
     * @return
     * @throws CannotFoundSummoner
     */
    public Queue<MatchInfoResDto> getSummonerMatches(String gameName, String tagLine, Boolean refresh) throws CannotFoundSummoner {
        Queue<MatchInfoResDto> matchInfoResDtos = new LinkedList<>();

        // 닉변해도 여기서는 무조건 나올 것
        Optional<Summoner> s = this.findSummoner(gameName, tagLine);
        String puuid = this.findPuuid(gameName, tagLine,s);

//        // 여기서도 Summoner 객체를 통해서 DB의 matchid를 먼저 받을 생각을 해야 함
//
//
//        // 여기서도 puuid로 요청해서 크게 의미 없음
//        String[] summonerMatchesId = riotApiService.getSummonerMatches(
//                SummonerDto.setInform(gameName, tagLine, puuid)
//        );

        String[] summonerMatchesId = matchService.getMatchIds(s, puuid, refresh);


        // step 1) : matchId를 통해서 Match_info 객체를 받아오기  => 있을 수도 있고 없을 수도 있음
        for (String matchId : summonerMatchesId) { // match id 가져와서 반복문 돌림
            Optional<Match> match = matchService.getMatchByMatchId(matchId);

            // step 2-1) : match가 있다면 바로 matchDataDto 구성하기
            if (match.isPresent()) {
                Match match1 = match.get();

                // match에서 classic이 아니면 넘어가기
                if (!this.isClassicGame(match1) ){
                    continue;
                }
                List<MatchParticipant> participants = match1.getParticipants();
                ParticipantsResDto participantsResDto = new ParticipantsResDto();
                addParticipant(participants, participantsResDto);
                MetaDataResDto metaDataResDto = MetaDataResDto.of(match1);
                MatchParticipant myDataByPuuid = match1.getMyDataByPuuid(puuid);
                Long championId = myDataByPuuid.getChampionId();
                Champion champion = championService.getChampionById(championId).orElseThrow(()->new CannotFoundChampion(championId+"에 해당하는 챔피언이 없습니다."));
                MyDataResDto myDataResDto = MyDataResDto.of(myDataByPuuid,champion);

                myDataResDto.setPerks(myDataByPuuid);

                // step 4-2) : MatchDataDto 객체를 List에 넣어준다
                MatchInfoResDto dto = MatchInfoResDto.of(metaDataResDto, myDataResDto, participantsResDto);
                matchInfoResDtos.add(dto);
            }
            // step 2-2) : match가 없다면 만들고 matchDataDto 구성하기
            else {

                // step 3-2) : riot API 요청을 통해서 해당 matchId의 값을 받기
                MatchDto matchDto = riotApiService.getMatchInfo(matchId);

                // match에서 classic이 아니면 넘어가기
                if (this.isClassicGame(matchDto) == false) {
                    continue;
                }
                // step 4-2) : matchDto 속의 infoDto를 통해서 participantDto를 통해, Summoner에 저장이 되어 있는 Summoner인지 체크하기
                InfoDto infoDto = matchDto.getInfo();
                List<ParticipantDto> participantsDtoFromApi = infoDto.getParticipants(); // 참가자 정보 찾아 왔음


                //여기서 queueID 넣어야 함
                // 기본 정보 저장
                Match newMatch = Match.builder()
                        .matchId(matchId) // Riot API에서 받은 matchId
                        .gameMode(infoDto.getGameMode())
                        .gameDuration(infoDto.getGameDuration())
                        .gameCreation(infoDto.getGameCreation())
                        .gameEndTimestamp(infoDto.getGameEndTimestamp())
                        .queueId(infoDto.getQueueId())
                        .build();

                List<MatchParticipant> matchParticipants = new ArrayList<>();
                Random rand = new Random();
                for (ParticipantDto participantDto : participantsDtoFromApi) {
                    // step 5-2) : puuid로 Summoner를 찾거나, 없으면 새로 저장
                    Summoner summoner = findOrCreateSummoner(participantDto);

                    MatchParticipant participant = MatchParticipant.builder()
                            .match(newMatch)
                            .summoner(summoner)
                            .win(participantDto.getWin())
                            .championId(participantDto.getChampionId())
                            .champLevel(participantDto.getChampLevel())
                            .teamPosition(participantDto.getTeamPosition())
                            .item0(participantDto.getItem0())
                            .item1(participantDto.getItem1())
                            .item2(participantDto.getItem2())
                            .item3(participantDto.getItem3())
                            .item4(participantDto.getItem4())
                            .item5(participantDto.getItem5())
                            .item6(participantDto.getItem6())
                            .perks(participantDto.getPerks())
                            .kda(participantDto.getKda())
                            .kills(participantDto.getKills())
                            .assists(participantDto.getAssists())
                            .deaths(participantDto.getDeaths())
                            .totalMinionKills(participantDto.getTotalMinionsKilled() + participantDto.getNeutralMinionsKilled())
                            .totalDamageTaken(participantDto.getTotalDamageTaken())
                            .totalDamageDealtToChampions(participantDto.getTotalDamageDealtToChampions())
                            .doubleKills(participantDto.getDoubleKills())
                            .tripleKills(participantDto.getTripleKills())
                            .quadraKills(participantDto.getQuadraKills())
                            .pentaKills(participantDto.getPentaKills())
                            .summoner1Casts(participantDto.getSummoner1Casts())
                            .summoner1Id(participantDto.getSummoner1Id())
                            .summoner2Id(participantDto.getSummoner2Id())
                            .summoner2Casts(participantDto.getSummoner2Casts())
                            .spell1Casts(participantDto.getSpell1Casts())
                            .spell2Casts(participantDto.getSpell2Casts())
                            .spell3Casts(participantDto.getSpell3Casts())
                            .spell4Casts(participantDto.getSpell4Casts())
                            .wardKilled(participantDto.getWardKilled())
                            .wardPlaced(participantDto.getWardPlaced())
                            .visionWardsBoughtInGame(participantDto.getVisionWardsBoughtInGame())
                            .visionScore(participantDto.getVisionScore())
                            .build();
                    participant.setTeamLuckScore(pythonService.getRandomNumberFromPython());
                    participant.setOurScore(pythonService.getRandomNumberFromPython());
                    matchParticipants.add(participant);
                }

                newMatch.setParticipants(matchParticipants);

                ParticipantsResDto participantsResDto = new ParticipantsResDto();
                addParticipant(matchParticipants, participantsResDto);
                MetaDataResDto metaDataResDto = MetaDataResDto.of(newMatch);
                MatchParticipant myDataByPuuid = newMatch.getMyDataByPuuid(puuid);
                Long championId = myDataByPuuid.getChampionId();
                Champion champion = championService.getChampionById(championId).orElseThrow(()->new CannotFoundChampion(championId+"에 해당하는 챔피언이 없습니다."));
                MyDataResDto myDataResDto = MyDataResDto.of(myDataByPuuid,champion);
                myDataResDto.setPerks(myDataByPuuid);

                matchInfoResDtos.add(MatchInfoResDto.of(metaDataResDto, myDataResDto, participantsResDto));
                matchService.saveIfAbsent(newMatch);
            }
        }

        if (Boolean.TRUE.equals(refresh)) {
            markSummonerRefreshed(s, puuid);
        }

        return matchInfoResDtos;
    }
    public Queue<MatchInfoResDto> getSummonerSummaryMatch(String[] summonerMatchesId, String puuid) throws CannotFoundSummoner {
        Queue<MatchInfoResDto> matchInfoResDtos = new LinkedList<>();
        for (String s : summonerMatchesId) {
            log.info("{}", s);
        }
        int i = 1;
        // step 1) : matchId를 통해서 Match_info 객체를 받아오기  => 있을 수도 있고 없을 수도 있음
        for (String matchId : summonerMatchesId) { // match id 가져와서 반복문 돌림
            log.info("{}) id : {}",i++,matchId);
            Optional<Match> match = matchService.getMatchByMatchId(matchId);

            // step 2-1) : match가 있다면 바로 matchDataDto 구성하기
            if (match.isPresent()) {
                Match match1 = match.get();
                List<MatchParticipant> participants = match1.getParticipants();
                ParticipantsResDto participantsResDto = new ParticipantsResDto();
                addParticipant(participants, participantsResDto);
                MetaDataResDto metaDataResDto = MetaDataResDto.of(match1);
                MatchParticipant myDataByPuuid = match1.getMyDataByPuuid(puuid);
                Long championId = myDataByPuuid.getChampionId();
                Champion champion = championService.getChampionById(championId).orElseThrow(()->new CannotFoundChampion(championId+"에 해당하는 챔피언이 없습니다."));
                MyDataResDto myDataResDto = MyDataResDto.of(myDataByPuuid,champion);

                myDataResDto.setPerks(myDataByPuuid);

                // step 4-2) : MatchDataDto 객체를 List에 넣어준다
                MatchInfoResDto dto = MatchInfoResDto.of(metaDataResDto, myDataResDto, participantsResDto);
                matchInfoResDtos.add(dto);
            }
            // step 2-2) : match가 없다면 만들고 matchDataDto 구성하기
            else {

                // step 3-2) : riot API 요청을 통해서 해당 matchId의 값을 받기
                log.info("*****1****");
                MatchDto matchDto = riotApiService.getMatchInfo(matchId);

                if (matchDto == null){
                    throw new RiotInternalError("라이엇 내부 오류 500");
                }
                // step 4-2) : matchDto 속의 infoDto를 통해서 participantDto를 통해, Summoner에 저장이 되어 있는 Summoner인지 체크하기
                InfoDto infoDto = matchDto.getInfo();
                List<ParticipantDto> participantsDtoFromApi = infoDto.getParticipants(); // 참가자 정보 찾아 왔음


                //여기서 queueID 넣어야 함
                // 기본 정보 저장
                Match newMatch = Match.builder()
                        .matchId(matchId) // Riot API에서 받은 matchId
                        .gameMode(infoDto.getGameMode())
                        .gameDuration(infoDto.getGameDuration())
                        .gameCreation(infoDto.getGameCreation())
                        .gameEndTimestamp(infoDto.getGameEndTimestamp())
                        .queueId(infoDto.getQueueId())
                        .build();

                List<MatchParticipant> matchParticipants = new ArrayList<>();
                Random rand = new Random();
                for (ParticipantDto participantDto : participantsDtoFromApi) {
                    // step 5-2) : puuid로 Summoner를 찾거나, 없으면 새로 저장
                    Summoner summoner = findOrCreateSummoner(participantDto);

                    MatchParticipant participant = MatchParticipant.builder()
                            .match(newMatch)
                            .summoner(summoner)
                            .win(participantDto.getWin())
                            .championId(participantDto.getChampionId())
                            .champLevel(participantDto.getChampLevel())
                            .teamPosition(participantDto.getTeamPosition())
                            .item0(participantDto.getItem0())
                            .item1(participantDto.getItem1())
                            .item2(participantDto.getItem2())
                            .item3(participantDto.getItem3())
                            .item4(participantDto.getItem4())
                            .item5(participantDto.getItem5())
                            .item6(participantDto.getItem6())
                            .perks(participantDto.getPerks())
                            .kda(participantDto.getKda())
                            .kills(participantDto.getKills())
                            .assists(participantDto.getAssists())
                            .deaths(participantDto.getDeaths())
                            .totalMinionKills(participantDto.getTotalMinionsKilled() + participantDto.getNeutralMinionsKilled())
                            .totalDamageTaken(participantDto.getTotalDamageTaken())
                            .totalDamageDealtToChampions(participantDto.getTotalDamageDealtToChampions())
                            .doubleKills(participantDto.getDoubleKills())
                            .tripleKills(participantDto.getTripleKills())
                            .quadraKills(participantDto.getQuadraKills())
                            .pentaKills(participantDto.getPentaKills())
                            .summoner1Casts(participantDto.getSummoner1Casts())
                            .summoner1Id(participantDto.getSummoner1Id())
                            .summoner2Id(participantDto.getSummoner2Id())
                            .summoner2Casts(participantDto.getSummoner2Casts())
                            .spell1Casts(participantDto.getSpell1Casts())
                            .spell2Casts(participantDto.getSpell2Casts())
                            .spell3Casts(participantDto.getSpell3Casts())
                            .spell4Casts(participantDto.getSpell4Casts())
                            .wardKilled(participantDto.getWardKilled())
                            .wardPlaced(participantDto.getWardPlaced())
                            .visionWardsBoughtInGame(participantDto.getVisionWardsBoughtInGame())
                            .visionScore(participantDto.getVisionScore())
                            .build();
                    participant.setTeamLuckScore(pythonService.getRandomNumberFromPython());
                    participant.setOurScore(pythonService.getRandomNumberFromPython());
                    matchParticipants.add(participant);
                }

                newMatch.setParticipants(matchParticipants);

                ParticipantsResDto participantsResDto = new ParticipantsResDto();
                addParticipant(matchParticipants, participantsResDto);
                MetaDataResDto metaDataResDto = MetaDataResDto.of(newMatch);
                MatchParticipant myDataByPuuid = newMatch.getMyDataByPuuid(puuid);
                Long championId = myDataByPuuid.getChampionId();
                Champion champion = championService.getChampionById(championId).orElseThrow(()->new CannotFoundChampion(championId+"에 해당하는 챔피언이 없습니다."));
                MyDataResDto myDataResDto = MyDataResDto.of(myDataByPuuid,champion);
                myDataResDto.setPerks(myDataByPuuid);

                matchInfoResDtos.add(MatchInfoResDto.of(metaDataResDto, myDataResDto, participantsResDto));
                matchService.saveIfAbsent(newMatch);
            }
        }

        return matchInfoResDtos;
    }
    // ## 안 쓸 것 ㅇㅇ
    public MinimalMatchResDto getMinimalMatchData(String puuid, String matchId){
        // 1) matchId를 통해서 Match정보를 받아와야 함 -> ##MatchService에서 가져왔어야 했는데
        Optional<Match> optionalMatch = matchService.getMatchByMatchId(matchId);

        if (optionalMatch.isPresent()) { // 2-1) Match 정보가 디비에 저장되어 있다면
            Match match = optionalMatch.get();
            List<MatchParticipant> participants = match.getParticipants();

            // 1. 참여자 데이터
            ParticipantsResDto participantsResDto = new ParticipantsResDto();
            addParticipant(participants, participantsResDto);


            // 2. meta 데이터 (정보)
            MetaDataResDto metaDataResDto = MetaDataResDto.of(match);


            // 3. 나의 데이터
            MatchParticipant myDataByPuuid = match.getMyDataByPuuid(puuid);
            Long championId = myDataByPuuid.getChampionId();
            Champion champion = championService.getChampionById(championId)
                    .orElseThrow(()->new CannotFoundChampion(championId+"에 해당하는 챔피언이 없습니다."));


            MyDataResDto myDataResDto = MyDataResDto.of(myDataByPuuid,champion);
            myDataResDto.setPerks(myDataByPuuid);

            return MinimalMatchResDto.of(metaDataResDto, participantsResDto, myDataResDto);
        }
        // step 2-2) : match가 없다면 만들고 matchDataDto 구성하기
        else {

            // step 3-2) : riot API 요청을 통해서 해당 matchId의 값을 받기
            MatchDto matchDto = riotApiService.getMatchInfo(matchId);

            // step 4-2) : matchDto 속의 infoDto를 통해서 participantDto를 통해, Summoner에 저장이 되어 있는 Summoner인지 체크하기
            InfoDto infoDto = matchDto.getInfo();
            List<ParticipantDto> participantsDtoFromApi = infoDto.getParticipants(); // 참가자 정보 찾아 왔음


            //여기서 queueID 넣어야 함
            // 기본 정보 저장
            Match newMatch = Match.builder()
                    .matchId(matchId) // Riot API에서 받은 matchId
                    .gameMode(infoDto.getGameMode())
                    .gameDuration(infoDto.getGameDuration())
                    .gameCreation(infoDto.getGameCreation())
                    .gameEndTimestamp(infoDto.getGameEndTimestamp())
                    .queueId(infoDto.getQueueId())
                    .build();

            List<MatchParticipant> matchParticipants = new ArrayList<>();
            Random rand = new Random();
            for (ParticipantDto participantDto : participantsDtoFromApi) {
                // step 5-2) : puuid로 Summoner를 찾거나, 없으면 새로 저장
                Summoner summoner = findOrCreateSummoner(participantDto);

                MatchParticipant participant = MatchParticipant.builder()
                        .match(newMatch)
                        .summoner(summoner)
                        .win(participantDto.getWin())
                        .championId(participantDto.getChampionId())
                        .champLevel(participantDto.getChampLevel())
                        .teamPosition(participantDto.getTeamPosition())
                        .item0(participantDto.getItem0())
                        .item1(participantDto.getItem1())
                        .item2(participantDto.getItem2())
                        .item3(participantDto.getItem3())
                        .item4(participantDto.getItem4())
                        .item5(participantDto.getItem5())
                        .item6(participantDto.getItem6())
                        .perks(participantDto.getPerks())
                        .kda(participantDto.getKda())
                        .kills(participantDto.getKills())
                        .assists(participantDto.getAssists())
                        .deaths(participantDto.getDeaths())
                        .totalMinionKills(participantDto.getTotalMinionsKilled() + participantDto.getNeutralMinionsKilled())
                        .totalDamageTaken(participantDto.getTotalDamageTaken())
                        .totalDamageDealtToChampions(participantDto.getTotalDamageDealtToChampions())
                        .doubleKills(participantDto.getDoubleKills())
                        .tripleKills(participantDto.getTripleKills())
                        .quadraKills(participantDto.getQuadraKills())
                        .pentaKills(participantDto.getPentaKills())
                        .summoner1Casts(participantDto.getSummoner1Casts())
                        .summoner1Id(participantDto.getSummoner1Id())
                        .summoner2Id(participantDto.getSummoner2Id())
                        .summoner2Casts(participantDto.getSummoner2Casts())
                        .spell1Casts(participantDto.getSpell1Casts())
                        .spell2Casts(participantDto.getSpell2Casts())
                        .spell3Casts(participantDto.getSpell3Casts())
                        .spell4Casts(participantDto.getSpell4Casts())
                        .wardKilled(participantDto.getWardKilled())
                        .wardPlaced(participantDto.getWardPlaced())
                        .visionWardsBoughtInGame(participantDto.getVisionWardsBoughtInGame())
                        .visionScore(participantDto.getVisionScore())
                        .build();
                participant.setTeamLuckScore(rand.nextInt(100 - 35 + 1) + 35);
                participant.setOurScore(rand.nextInt(100 - 35 + 1) + 35);
                matchParticipants.add(participant);
            }

            newMatch.setParticipants(matchParticipants);

            ParticipantsResDto participantsResDto = new ParticipantsResDto();
            addParticipant(matchParticipants, participantsResDto);
            MetaDataResDto metaDataResDto = MetaDataResDto.of(newMatch);
            MatchParticipant myDataByPuuid = newMatch.getMyDataByPuuid(puuid);
            Long championId = myDataByPuuid.getChampionId();
            Champion champion = championService.getChampionById(championId).orElseThrow(()->new CannotFoundChampion(championId+"에 해당하는 챔피언이 없습니다."));
            MyDataResDto myDataResDto = MyDataResDto.of(myDataByPuuid,champion);
            myDataResDto.setPerks(myDataByPuuid);

            matchService.saveIfAbsent(newMatch);

            return MinimalMatchResDto.of(metaDataResDto, participantsResDto, myDataResDto);
        }
    }







    private void addParticipant(List<MatchParticipant> participants, ParticipantsResDto participantsResDto) {
        Long championId0 = participants.get(0).getChampionId();
        Champion champion0 = championService.getChampionById(championId0).orElseThrow(()->new CannotFoundChampion(championId0+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer0(participants.get(0),champion0);

        Long championId1 = participants.get(1).getChampionId();
        Champion champion1 = championService.getChampionById(championId1).orElseThrow(()->new CannotFoundChampion(championId1+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer1(participants.get(1),champion1);

        Long championId2 = participants.get(2).getChampionId();
        Champion champion2 = championService.getChampionById(championId2).orElseThrow(()->new CannotFoundChampion(championId2+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer2(participants.get(2),champion2);

        Long championId3 = participants.get(3).getChampionId();
        Champion champion3 = championService.getChampionById(championId3).orElseThrow(()->new CannotFoundChampion(championId3+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer3(participants.get(3),champion3);

        Long championId4 = participants.get(4).getChampionId();
        Champion champion4 = championService.getChampionById(championId4).orElseThrow(()->new CannotFoundChampion(championId4+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer4(participants.get(4),champion4);



        Long championId5 = participants.get(5).getChampionId();
        Champion champion5 = championService.getChampionById(championId5).orElseThrow(()->new CannotFoundChampion(championId5+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer5(participants.get(5),champion5);

        Long championId6 = participants.get(6).getChampionId();
        Champion champion6 = championService.getChampionById(championId6).orElseThrow(()->new CannotFoundChampion(championId6+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer6(participants.get(6),champion6);

        Long championId7 = participants.get(7).getChampionId();
        Champion champion7 = championService.getChampionById(championId7).orElseThrow(()->new CannotFoundChampion(championId7+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer7(participants.get(7),champion7);

        Long championId8 = participants.get(8).getChampionId();
        Champion champion8 = championService.getChampionById(championId8).orElseThrow(()->new CannotFoundChampion(championId8+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer8(participants.get(8),champion8);

        Long championId9 = participants.get(9).getChampionId();
        Champion champion9 = championService.getChampionById(championId9).orElseThrow(()->new CannotFoundChampion(championId9+"에 해당하는 챔피언이 없습니다."));
        participantsResDto.addPlayer9(participants.get(9),champion9);
    }

    /**
     *
     * @param gameName
     * @param tagLine
     * @return
     */
    public Summoner resolveSummoner(String gameName, String tagLine) throws CannotFoundSummoner {
        // 1) DB에서 gameName, tagLIne으로 먼저 찾음
        Optional<Summoner> savedByName = findSummoner(gameName, tagLine);

        // 2) 있으면 호출 ㄴㄴ
        if (savedByName.isPresent()){
            return savedByName.get();
        }

        // 3) 없으면 호출
        RiotAccountDto account = riotApiService.getSummonerInfo(gameName, tagLine);

        summonerRepository.findSummonerByPuuid(account.getPuuid());

        // 4) 저장
        if (savedByName.isPresent()) {
            Summoner summoner = savedByName.get();
            summoner.updateIdentity(account);
            return summonerWriteService.saveAndFlush(summoner);
        }

        return saveSummonerIfAbsent(new Summoner(account));
    }
    private Summoner refreshIdentityByPuuid(Summoner summoner) throws CannotFoundSummoner {
        RiotAccountDto latestAccount = riotApiService.getSummonerByPuuid(summoner.getPuuid());

        if (!Objects.equals(summoner.getGameName(), latestAccount.getGameName())
                || !Objects.equals(summoner.getTagLine(), latestAccount.getTagLine())) {
            summoner.updateIdentity(latestAccount);
            return summonerWriteService.saveAndFlush(summoner);
        }

        return summoner;
    }


    /** Summoner 객체를 저장과 업데이트를 하는 메소드
     *
     * @param dto
     * @param summoner
     * @return
     */
    private Summoner SaveOrUpateSummoner(SummonerDto dto, Optional<Summoner> summoner){
        if (summoner.isPresent()){
            // 기존에 puuid가 같은 소환사가 있다면 정보를 업데이트
            Summoner target = summoner.get();
            // 닉네임이 바뀌었을 수 있으므로 updateTier 내부에서
            // gameName, tagLine, trimmedGameName을 모두 갱신
            target.updateTier(dto);
            target.markRefreshed();
            return summonerWriteService.saveAndFlush(target);
        }
        else {
            Summoner target = Summoner.update(dto);
            return saveSummonerIfAbsent(target);
        }
    }


    /** 숙련도 할 때 세팅하는 메소드
     *
     * @param dtos
     * @return
     */
    private List<MasteryDto> setChampionName(List<MasteryDto> dtos) {
        for (MasteryDto dto : dtos) {
            // 1. championId로 Champion 정보를 조회합니다.
            Optional<Champion> championOptional = championRepository.findById(dto.getChampionId());

            // 2. ifPresent를 사용하여 Optional 객체가 비어있지 않은 경우에만 내부 로직을 실행합니다.
            //    이렇게 하면 데이터가 없을 때 .get()으로 인한 오류를 원천적으로 방지할 수 있습니다.
            championOptional.ifPresent(champion -> {
                // 3. (핵심) 조회한 champion 객체의 이름을 MasteryDto에 설정합니다.
                dto.setChampionName(champion.getNameKo());
            });

            // 4. (선택사항) 만약 데이터가 없는 경우를 확인하고 싶다면 아래와 같이 처리할 수 있습니다.
            if (championOptional.isEmpty()) {
                log.warn("DB에서 Champion 정보를 찾을 수 없습니다. champId: {}", dto.getChampionId());
            }
        }
        return dtos;
    }

    public List<SummonerKeywordResDto> findSummonerByKeyword(String keyword){
        List<Summoner> byGameNameContainingIgnoreCase = summonerRepository.findByGameNameContainingIgnoreCase(keyword);
        List<SummonerKeywordResDto> list = new ArrayList<>();
        for (Summoner summoner : byGameNameContainingIgnoreCase) {
            list.add(SummonerKeywordResDto.of(summoner));
        }
        return list;
    }

    public List<SummonerKeywordResDto> findSummonerByKeywordV2(String keyword){
        List<Summoner> result;

        if (keyword.contains("#")){ // 태그 라인까지 같이 검색을 한 경우
            // # 기준으로 분리
            String[] parts = keyword.split("#", 2);
            String gameName = parts[0];
            String tagLine = parts[1];
            result = summonerRepository.findByGameNameContainingIgnoreCaseAndTagLineContainingIgnoreCase(gameName, tagLine);
        }
        else {
            result = summonerRepository.findByGameNameContainingIgnoreCase(keyword);
        }
        return result.stream()
                .map(SummonerKeywordResDto::of)
                .toList();
    }



    public ProfileResDto getProfile(String gameName, String tagLine, Boolean refresh) throws CannotFoundSummoner {
        gameName = gameName.replace(" ", "");

        Summoner summoner = resolveSummoner(gameName, tagLine);

        if (summoner.getIconId() != null && summoner.getLevel() != null && !refresh) {
            return ProfileResDto.of(summoner);
        }

        ProfileResDto profileInfo = riotApiService.getProfileInfo(summoner.getPuuid());
        summoner.updateProfile(profileInfo);
        if (Boolean.TRUE.equals(refresh)) {
            summoner.markRefreshed();
        }

        Summoner saved = summonerWriteService.saveAndFlush(summoner);
        return ProfileResDto.of(saved);
    }


    public Optional<Summoner> getSummonerByPuuid(String puuid){
        return summonerRepository.findSummonerByPuuid(puuid);
    }
    public Summoner saveSummoner(RiotAccountDto riotAccountDto){
        return saveSummonerIfAbsent(new Summoner(riotAccountDto));
    }
    public void updateSummoners(List<Summoner> summoners){
        summonerRepository.saveAll(summoners);
    }

    private Summoner findOrCreateSummoner(ParticipantDto participantDto) {
        return summonerRepository.findSummonerByPuuid(participantDto.getPuuid())
                .orElseGet(() -> saveSummonerIfAbsent(new Summoner(participantDto)));
    }

    private Summoner saveSummonerIfAbsent(Summoner newSummoner) {
        String puuid = newSummoner.getPuuid();
        Object lock = summonerCreationLocks.computeIfAbsent(puuid, ignored -> new Object());

        synchronized (lock) {
            return summonerRepository.findSummonerByPuuid(puuid)
                    .orElseGet(() -> summonerWriteService.insertIgnoreAndFind(newSummoner));
        }
    }

    private void markSummonerRefreshed(Optional<Summoner> summoner, String puuid) {
        Summoner target = summoner
                .or(() -> summonerRepository.findSummonerByPuuid(puuid))
                .orElse(null);

        if (target == null) {
            return;
        }

        target.markRefreshed();
        summonerWriteService.saveAndFlush(target);
    }

    private boolean isClassicGame(Match match){
        return match.getGameMode().equals("CLASSIC");
    }
    private boolean isClassicGame(MatchDto match){
        return match.getInfo().getGameMode().equals("CLASSIC");
    }

}
