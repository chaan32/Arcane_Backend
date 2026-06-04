package com.arcane.Arcane.Web.Statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterChampionResDto {
    private String championNameEn;
    private String championImgUrl;
}
