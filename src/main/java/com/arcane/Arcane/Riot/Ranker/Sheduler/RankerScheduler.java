package com.arcane.Arcane.Riot.Ranker.Sheduler;


import com.arcane.Arcane.Common.Redis.RedisService;
import com.arcane.Arcane.Riot.AdditionalData.service.AdditionalDataService;
import com.arcane.Arcane.Riot.Ranker.domain.Tier;
import com.arcane.Arcane.Riot.Ranker.dto.FromRiotRankerResDto;
import com.arcane.Arcane.Riot.Ranker.dto.RedisRankerDto;
import com.arcane.Arcane.Riot.Ranker.dto.RiotRankerDto;
import com.arcane.Arcane.Riot.Ranker.service.RankerService;
import com.arcane.Arcane.Riot.RiotInform.service.RiotApiService;
import com.arcane.Arcane.Riot.Summoner.domain.Summoner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@Component
@RequiredArgsConstructor
@Slf4j
public class RankerScheduler {
    private final RedisService redisService;
    private final RankerService rankerService;
    private final RiotApiService riotApiService;
    private final AdditionalDataService additionalDataService;

//    @Scheduled(cron = "0 0/30 * * * *")
    public void scheduleRankingUpdate(){
        // 1. 레디스에 있는 데이터 복제하기
        // 2.
        // 3. 레디스에 있는 데이터 (복제본 말고) 삭제하기
        // 4. Riot API에 티어 별로 정보 받아오기
        // 5. 랭킹 정보 받아와서 Summoner에 정보 업데이트 -> 없으면 저장하고
        // 6. 레디스에 해당 정보 key : puuid / value : RedisRankerDto 로 만들어서 저장
        log.info("========= [UPDATING - RANKING] START ===========");
        try{
            log.info("----------Challneger START-----------");
            updateTier(Tier.CHALLENGER);
            log.info("----------GrandMaster START-----------");
            updateTier(Tier.GRANDMASTER);
            log.info("----------Master START-----------");
            updateTier(Tier.MASTER);

            // 전체 순위 반영
            rankerService.updateGlobalRanking();
            log.info("========= [UPDATING - RANKING] DONE ===========");

            log.info("========= [UPDATING - Rankers Icon And Level] START ===========");
            additionalDataService.downloadRankersProfile();
            log.info("========= [UPDATING - Rankers Icon And Level] DONE ===========");


        }
        catch (Exception e){
            log.warn("<<< 업데이트 중 에러 발생 >>> : " + e.getMessage());
            return;
        }
        log.info("=========Scheduled UPDATING DONE========");
    }
    private void updateTier(Tier tier) throws InterruptedException {
        FromRiotRankerResDto leagueByTier = riotApiService.getLeagueByTier(tier);
        Queue<RedisRankerDto> redisRankerDtos = getRankerObject(leagueByTier, tier);
        redisService.updateRedisRanking(redisRankerDtos, tier);
    }
    private Queue<RedisRankerDto> getRankerObject(FromRiotRankerResDto dtos, Tier tier) throws InterruptedException {
        List<RiotRankerDto> entries = dtos.getEntries();
        Queue<RedisRankerDto> redisRankerDtos = new LinkedList<>();
        for (RiotRankerDto entry : entries) {
            Summoner summoner = rankerService.updateScore(entry);
            if (summoner== null) continue;
            summoner.setSoloRankTier(tier);
            redisRankerDtos.add(RedisRankerDto.of(summoner));
        }
        return redisRankerDtos;
    }
}
