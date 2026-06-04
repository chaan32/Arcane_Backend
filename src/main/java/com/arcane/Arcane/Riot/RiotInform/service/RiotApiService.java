package com.arcane.Arcane.Riot.RiotInform.service;

import com.arcane.Arcane.Common.Exception.Fail.*;
import com.arcane.Arcane.Common.Exception.RiotAPI.CannotFoundSummoner;
import com.arcane.Arcane.Riot.Match.dto.MatchDto;
import com.arcane.Arcane.Riot.Match.dto.minimal.MinimalMatchDto;
import com.arcane.Arcane.Riot.Ranker.domain.Tier;
import com.arcane.Arcane.Riot.Ranker.dto.FromRiotRankerResDto;
import com.arcane.Arcane.Riot.RiotInform.dto.*;
import com.arcane.Arcane.Riot.RiotInform.dto.Ranker.ChallengerLeagueDto;
import com.arcane.Arcane.Riot.Summoner.dto.SummonerDto;
import com.arcane.Arcane.Score.dto.MatchModelDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.WebListenerRegistry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiotApiService {
    private final WebListenerRegistry webListenerRegistry;
    @Value("${riot.api-key}")
    private String apiKey;

    private String baseUrlAsia = "https://asia.api.riotgames.com";
    private String baseUrlKR = "https://kr.api.riotgames.com";
    private static final String KR_BASE_URL = "https://kr.api.riotgames.com";

    private final RestTemplate restTemplate = new RestTemplate();
    private static final long ACCOUNT_CACHE_TTL_MILLIS = 60_000L;
    private final ConcurrentMap<String, CachedAccount> accountCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> accountLocks = new ConcurrentHashMap<>();

    public RiotAccountDto getSummonerInfo(String gameName, String tagLine) throws CannotFoundSummoner {
        String cacheKey = accountCacheKey(gameName, tagLine);
        CachedAccount cachedAccount = accountCache.get(cacheKey);
        if (cachedAccount != null && !cachedAccount.isExpired()) {
            return cachedAccount.account();
        }

        Object lock = accountLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (lock) {
            cachedAccount = accountCache.get(cacheKey);
            if (cachedAccount != null && !cachedAccount.isExpired()) {
                return cachedAccount.account();
            }

            RiotAccountDto account = requestSummonerInfo(gameName, tagLine);
            accountCache.put(cacheKey, new CachedAccount(account));
            return account;
        }
    }

    private RiotAccountDto requestSummonerInfo(String gameName, String tagLine) throws CannotFoundSummoner {
        // uuid 정보 얻기
        String url = baseUrlAsia + "/riot/account/v1/accounts/by-riot-id/" + gameName +"/" + tagLine;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<RiotAccountDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    RiotAccountDto.class
            );
            RiotAccountDto body = response.getBody();
            if (body == null) {
                throw new CannotFoundSummoner("소환사 정보를 가져오는 중 오류가 발생했습니다.");
            }
            log.info("RiotAPI SERVICE : RiotAccountDto : {}", body);
            return body;

        } catch (HttpClientErrorException.NotFound e) {
            // 404 에러일 경우 직접 메시지 던짐
            throw new CannotFoundSummoner(gameName + "#" + tagLine + " 소환사를 찾을 수 없습니다.");
        } catch (RestClientException e) {
            log.error(" Riot API ERROR : {}", e.getMessage());
            throw new CannotFoundSummoner("소환사 정보를 가져오는 중 오류가 발생했습니다.");
        }
    }

    private String accountCacheKey(String gameName, String tagLine) {
        return (gameName + "#" + tagLine).replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private record CachedAccount(RiotAccountDto account, long cachedAt) {
        private CachedAccount(RiotAccountDto account) {
            this(account, System.currentTimeMillis());
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > ACCOUNT_CACHE_TTL_MILLIS;
        }
    }

    public String getSummonerPuuid(String gameName, String tagLine) throws CannotFoundSummoner {
        return getSummonerInfo(gameName, tagLine).getPuuid();
    }


    public ProfileResDto getProfileInfo(String puuid) throws CannotFoundSummoner {
        // uuid 정보 얻기
        String url = baseUrlKR + "/lol/summoner/v4/summoners/by-puuid/" + puuid;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<ProfileDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    ProfileDto.class
            );
            ProfileDto body = response.getBody();
            return ProfileResDto.of(body);

        } catch (HttpClientErrorException.TooManyRequests e){
            throw new TooManyRequestFail("profile request LIMIT >>>");
        } catch (HttpClientErrorException.NotFound e) {
            // 404 에러일 경우 직접 메시지 던짐
            throw new CannotFoundSummoner(puuid + " 소환사를 찾을 수 없습니다.");
        } catch (RestClientException e) {
            log.error(" Riot API ERROR : {}", e.getMessage());
            throw new CannotFoundSummoner("소환사 정보를 가져오는 중 오류가 발생했습니다.");
        }
    }


    public SummonerDto getSummonerTierInfo(SummonerDto dto){
        log.info("RiotApiService : dto : {}", dto.toString());
        String url = baseUrlKR + "/lol/league/v4/entries/by-puuid/"+dto.getPuuid() ;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<TierInfoDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    }
            );
            List<TierInfoDto> tierInfoDtos = response.getBody();
            log.info("tier INFORM : {}",tierInfoDtos.toString());
            return setSummonerDtoV2(dto, tierInfoDtos);
        }
        catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public RiotAccountDto getRiotAccountInfo(String puuid) {
        String url = baseUrlAsia + "/riot/account/v1/accounts/by-puuid/"+puuid;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<RiotAccountDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    }
            );
            RiotAccountDto body = response.getBody();
            log.info("riotAccountDto : {} {} {}", body.getGameName(), body.getTagLine(), body.getPuuid());
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("PUUID : {} 에 해당하는 소환사가 없다", puuid);
            return null;
        } catch (HttpClientErrorException.TooManyRequests e){
            log.warn("API 호출 LIMIT 초과");
            return null;
        } catch (Exception e) {
            log.info(e.getMessage());
            return null;
        }
    }




    public List<MasteryDto> getMasteryInfo(String puuid) throws CannotFoundSummoner {
        String url = baseUrlKR + "/lol/champion-mastery/v4/champion-masteries/by-puuid/" + puuid;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<List<MasteryDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    }
            );
            return response.getBody();
        }
        catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }

    }
    public String[] getSummonerMatches(SummonerDto dto) throws CannotFoundSummoner {
        String url = baseUrlAsia + "/lol/match/v5/matches/by-puuid/"+dto.getPuuid()+"/ids";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        try{
            ResponseEntity<String[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody();
        }
        catch (HttpClientErrorException.TooManyRequests e) {
            // 429 에러: 호출 제한 초과
            log.warn("매치 리스트 조회 실패: API 호출 제한 초과 (429)");
            throw new TooManyRequestFail("Riot API 호출 제한 초과");

        } catch (HttpClientErrorException.NotFound e) {
            // 404 에러: 해당 PUUID에 대한 정보가 없음
            log.warn("매치 리스트를 찾을 수 없음 (404). PUUID: {}", dto.getPuuid());
            throw new CannotFoundSummoner("해당 소환사의 매치 기록이 존재하지 않습니다.");

        } catch (HttpClientErrorException.Unauthorized e) {
            // 401/403 에러: API 키 만료 또는 권한 오류
            log.error("API 키가 유효하지 않거나 만료되었습니다.");
            return null;

        } catch (HttpServerErrorException e) {
            // 500 에러: 아까 발생했던 라이엇 서버 내부 오류
            log.error("라이엇 서버 내부 오류 발생 ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            // null 대신 빈 배열을 반환하여 이후 반복문에서 NPE가 발생하지 않도록 함
            return new String[0];

        } catch (RestClientException e) {
            // 기타 RestTemplate 관련 오류
            log.error("매치 리스트 요청 중 알 수 없는 오류 발생: {}", e.getMessage());
            return null;
        }
    }
    public String[] getSummonerMatchesV3(String puuid) throws CannotFoundSummoner {
        String url = baseUrlAsia + "/lol/match/v5/matches/by-puuid/"+puuid+"/ids";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        try{
            ResponseEntity<String[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody();
        }
        catch (HttpClientErrorException.TooManyRequests e) {
            // 429 에러: 호출 제한 초과
            log.warn("매치 리스트 조회 실패: API 호출 제한 초과 (429)");
            throw new TooManyRequestFail("Riot API 호출 제한 초과");

        } catch (HttpClientErrorException.NotFound e) {
            // 404 에러: 해당 PUUID에 대한 정보가 없음
            log.warn("매치 리스트를 찾을 수 없음 (404). PUUID: {}", puuid);
            throw new CannotFoundSummoner("해당 소환사의 매치 기록이 존재하지 않습니다.");

        } catch (HttpClientErrorException.Unauthorized e) {
            // 401/403 에러: API 키 만료 또는 권한 오류
            log.error("API 키가 유효하지 않거나 만료되었습니다.");
            return null;

        } catch (HttpServerErrorException e) {
            // 500 에러: 아까 발생했던 라이엇 서버 내부 오류
            log.error("라이엇 서버 내부 오류 발생 ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            // null 대신 빈 배열을 반환하여 이후 반복문에서 NPE가 발생하지 않도록 함
            return new String[0];

        } catch (RestClientException e) {
            // 기타 RestTemplate 관련 오류
            log.error("매치 리스트 요청 중 알 수 없는 오류 발생: {}", e.getMessage());
            return null;
        }
    }
    public String[] getSummonerMatchesV2(String puuid, int page) throws CannotFoundSummoner {
        int pageSize = 20;
        int startIdx = (page-1)*pageSize;
        int count = pageSize;
        String url = UriComponentsBuilder.fromHttpUrl(baseUrlAsia + "/lol/match/v5/matches/by-puuid/" + puuid + "/ids")
                .queryParam("start", startIdx)   // 시작 인덱스
                .queryParam("count", count)   // 가져올 개수
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        try{
            ResponseEntity<String[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody();
        }
        catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public MatchDto getMatchInfo(String matchId) {
        String url = baseUrlAsia + "/lol/match/v5/matches/"+matchId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        try{
            ResponseEntity<MatchDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody();
        } catch (HttpClientErrorException.TooManyRequests e){
            throw new TooManyRequestFail("라이엇 API 요청 한도 초과 되었어요.");
        }
        catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public List<MatchModelDto> getMatchModel(String[] matchIds){
        List<MatchModelDto> dtos = new ArrayList<>();
        for (String matchId : matchIds) {
            String url = baseUrlAsia + "/lol/match/v5/matches/"+matchId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Riot-Token", apiKey);

            HttpEntity<?> entity = new HttpEntity<>(headers);
            try{
                ResponseEntity<MatchModelDto> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<>() {}
                );
                dtos.add(response.getBody());
            } catch (Exception e) {
                log.error(e.getMessage());
                return null;
            }
        }
        return dtos;
    }

    public List<String> getMatchIdByPuuid(String puuid, long seasonStartTime){
        // 쿼리 파라미터로 검색 기간, 솔랭, 갯수 넣어서 가져옴 => 쿼리 파라미터 넣고 싶어서 UriComponentBuilder 쓰기로 함

        String url = UriComponentsBuilder.fromHttpUrl(baseUrlAsia + "/lol/match/v5/matches/by-puuid/" + puuid + "/ids")
                .queryParam("startTime", seasonStartTime) // 시즌 시작 시간 필터
                .queryParam("queue", 420) // 솔랭만 가져오기
                .queryParam("count", 100) // API 제한 고려하여 최대 100개씩 가져오기
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try{
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("전적 ID 목록 조회 실패. puuid: {}, error: {}", puuid, e.getMessage());
            return new ArrayList<>(); // 오류 발생 시 빈 리스트 반환
        }
    }

    public MinimalMatchDto getMinimalMatchInfo(String matchId) {
        String url = baseUrlAsia + "/lol/match/v5/matches/" + matchId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<MinimalMatchDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    MinimalMatchDto.class // 새로 만든 MinimalMatchDto로 파싱
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get minimal match info for {}: {}", matchId, e.getMessage());
            return null;
        }
    }

    public ChallengerLeagueDto getChallengers(){
        String url = baseUrlKR + "/lol/league/v4/challengerleagues/by-queue/RANKED_SOLO_5x5";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        try{
            ResponseEntity<ChallengerLeagueDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }


    public FromRiotRankerResDto getRankersByTierAndKey(Tier tier, String key) throws CannotFoundSummoner {
        String url = baseUrlKR;
        if (tier == Tier.CHALLENGER){
            url = url + "/lol/league/v4/challengerleagues/by-queue/RANKED_SOLO_5x5";
        }
        else if (tier == Tier.GRANDMASTER){
            url = url + "/lol/league/v4/grandmasterleagues/by-queue/RANKED_SOLO_5x5";
        }
        else if (tier == Tier.MASTER){
            url = url + "/lol/league/v4/masterleagues/by-queue/RANKED_SOLO_5x5";
        }
        else {
            log.warn("잘못된 랭크 티어 요청");
            throw new WrongRankTier("잘못된 랭크 티어 요청");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", key);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        try{
            ResponseEntity<FromRiotRankerResDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody();
        }catch (HttpClientErrorException.TooManyRequests e) {
            // 429 에러 발생 시
            log.warn("API LIMIT 걸렸어");
            return null;
        } catch (Exception e) {
            // 다른 에러는 바로 던짐
            throw e;
        }
    }


    private SummonerDto setSummonerDtoV2(SummonerDto dto, List<TierInfoDto> list) {
        if (list.size() == 2) {
            TierInfoDto solo, flex;
            if (list.get(0).getQueueType().equals("RANKED_SOLO_5x5")) {
                solo = list.get(0);
                flex = list.get(1);
            } else {
                solo = list.get(1);
                flex = list.get(0);
            }
            dto.setSoloRankDefeat(solo.getLosses());
            dto.setSoloRankWin(solo.getWins());
            dto.setSoloRankTier(solo.getTier()+" "+solo.getRank());
            dto.setSoloRankLP(solo.getLeaguePoints());

            dto.setFlexRankDefeat(flex.getLosses());
            dto.setFlexRankWin(flex.getWins());
            dto.setFlexRankTier(flex.getTier()+" "+flex.getRank());
            dto.setFlexRankLP(flex.getLeaguePoints());
            return dto;
        } else if (list.size() == 1) {
            TierInfoDto temp = list.get(0);
            if (temp.getQueueType().equals("RANKED_SOLO_5x5")) {
                dto.setSoloRankDefeat(temp.getLosses());
                dto.setSoloRankWin(temp.getWins());
                dto.setSoloRankTier(temp.getTier() + " " + temp.getRank());
                dto.setSoloRankLP(temp.getLeaguePoints());
            } else {
                dto.setFlexRankDefeat(temp.getLosses());
                dto.setFlexRankWin(temp.getWins());
                dto.setFlexRankTier(temp.getTier()+" "+temp.getRank());
                dto.setFlexRankLP(temp.getLeaguePoints());
            }
            return dto;
        }


        return dto;
    }


    /**
     * 티어별 랭킹 정보 조회 (Challenger, Grandmaster, Master)
     * 반환 타입: FromRiotRankerResDto
     */
    public FromRiotRankerResDto getLeagueByTier(Tier tier) {
        String url = KR_BASE_URL;
        if (tier == Tier.CHALLENGER) {
            url += "/lol/league/v4/challengerleagues/by-queue/RANKED_SOLO_5x5";
        } else if (tier == Tier.GRANDMASTER) {
            url += "/lol/league/v4/grandmasterleagues/by-queue/RANKED_SOLO_5x5";
        } else if (tier == Tier.MASTER) {
            url += "/lol/league/v4/masterleagues/by-queue/RANKED_SOLO_5x5";
        } else {
            throw new IllegalArgumentException("지원하지 않는 티어입니다: " + tier);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<FromRiotRankerResDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    FromRiotRankerResDto.class
            );
            return response.getBody();

        } catch (HttpClientErrorException.TooManyRequests e){
            throw new TooManyRequestFail("Too Many Request AT Find Challenger Ranking");
        } catch (Exception e){
            log.warn(e.getMessage());
            return null;
        }

    }

    // puuid를 통해서 소환사 정보 획득하기
    public RiotAccountDto getSummonerByPuuid(String puuid) throws CannotFoundSummoner, TooManyRequestFail {
        // uuid 정보 얻기
        String url = baseUrlAsia + "/riot/account/v1/accounts/by-puuid/" + puuid;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<RiotAccountDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    RiotAccountDto.class
            );
            RiotAccountDto body = response.getBody();
            return response.getBody();

        } catch (HttpClientErrorException.TooManyRequests e){
            log.info("GET Summoner Inform By Puuid");
            throw new TooManyRequestFail("Too Many Request At Find Summoner Inform By Puuid : "+ puuid);
        }
        catch (HttpClientErrorException.NotFound e) {
            // 404 에러일 경우 직접 메시지 던짐
            throw new CannotFoundSummoner(puuid + " 소환사를 찾을 수 없습니다.");
        } catch (RestClientException e) {
            log.error(" Riot API ERROR : {}", e.getMessage());
            throw new CannotFoundSummoner("소환사 정보를 가져오는 중 오류가 발생했습니다.");
        }
    }
    /**
     * 특정 매치의 타임라인 데이터 원본(JSON)을 그대로 가져옵니다.
     * 데이터 가공 및 파싱은 다른 서비스 계층에서 담당합니다.
     */
    public String getMatchTimeline(String matchId) {
        String url = baseUrlAsia + "/lol/match/v5/matches/" + matchId + "/timeline";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", apiKey);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            // 원본 데이터를 그대로 넘겨주기 위해 String.class로 받습니다.
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getBody();

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("타임라인 API 호출 LIMIT 초과 - 매치 ID: {}", matchId);
            throw new TooManyRequestFail("Too Many Request At Find Timeline By MatchId : " + matchId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("해당 매치의 타임라인을 찾을 수 없습니다. (404) - 매치 ID: {}", matchId);
            return null;
        } catch (Exception e) {
            log.error("타임라인 데이터를 가져오는 중 오류 발생 ({}): {}", matchId, e.getMessage());
            return null;
        }
    }


}
