package com.arcane.Arcane.Riot.Data.Champion.dto;


import com.arcane.Arcane.Riot.Data.Champion.ChampionDto;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
public class ChampionResponseDto {
    private String type;
    private String version;
    private Map<String, ChampionDto> data; // 핵심!
}
