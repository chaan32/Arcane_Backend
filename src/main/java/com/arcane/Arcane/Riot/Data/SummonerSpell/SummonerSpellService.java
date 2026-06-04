package com.arcane.Arcane.Riot.Data.SummonerSpell;

import com.arcane.Arcane.Riot.Data.SummonerSpell.dto.SmmrSpellDto;
import com.arcane.Arcane.Riot.Data.SummonerSpell.dto.SmmrSpellResponseDto;
import com.arcane.Arcane.Riot.Data.SummonerSpell.repository.SummonerSpellRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.PathMatcher;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummonerSpellService {
    private final PathMatcher pathMatcher;
    @Value("${riot.patch-version}")
    private String patchVersion;
    private final RestTemplate restTemplate;
    private final SummonerSpellRepository summonerSpellRepository;


    public void importSummonerSpells() {
        String url = "https://ddragon.leagueoflegends.com/cdn/" + patchVersion + "/data/ko_KR/summoner.json";


        SmmrSpellResponseDto response = restTemplate.getForObject(url, SmmrSpellResponseDto.class);

        if (response != null && response.getData() != null) {

            for (SmmrSpellDto dto : response.getData().values()) {


                Long spellId = Long.parseLong(dto.getKey());
                String fullUrl = "https://ddragon.leagueoflegends.com/cdn/"+patchVersion+"/img/spell/";
                SmmrSpell spell = SmmrSpell.builder()
                        .spellId(spellId)
                        .name(dto.getName())
                        .description(dto.getDescription())
                        .imageFull(fullUrl+dto.getImage().getFull())
                        .build();

                summonerSpellRepository.save(spell);
            }
        }
    }
    public SmmrSpell getSmmrSpellById(Long id){
        return summonerSpellRepository.getSmmrSpellBySpellId (id);
    }
}
